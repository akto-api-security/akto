package com.akto.listener;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.akto.dto.IngestDataBatch;
import com.akto.log.LoggerMaker;
import com.akto.utils.KafkaUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * UDP Syslog listener for Apigee MessageLogging policy
 *
 * Receives Syslog messages from Apigee's PostClientFlow MessageLogging policy.
 * Extracts the JSON payload (traffic data), parses it, and inserts into Kafka.
 *
 * Usage: Apigee MessageLogging → UDP Syslog → this listener → KafkaUtils → Kafka
 */
public class SyslogUdpListener implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(SyslogUdpListener.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final int BUFFER_SIZE = 65535;  // Max UDP payload size
    private static final int DEFAULT_PORT = 5140;
    private static final int THREAD_POOL_SIZE = 4;
    private static final long CHUNK_TTL_MILLIS = 30_000L;
    private static final int MAX_ACTIVE_CHUNK_MESSAGES = 5000;
    private static final int MAX_REASSEMBLED_BYTES = 1_000_000;
    private static final int MAX_TOTAL_CHUNKS = 500;

    private int port;
    private ExecutorService executorService;
    private final ConcurrentHashMap<String, ChunkAccumulator> chunkBuffers = new ConcurrentHashMap<>();
    private final AtomicInteger packetCounter = new AtomicInteger(0);

    public SyslogUdpListener() {
        // Read port from environment, default to 5140
        String portEnv = System.getenv("SYSLOG_UDP_PORT");
        this.port = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "syslog-processor-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            logger.infoAndAddToDb("Syslog UDP listener started on port " + port, LoggerMaker.LogDb.DATA_INGESTION);

            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, BUFFER_SIZE);
                    socket.receive(packet);

                    if (packetCounter.incrementAndGet() % 200 == 0) {
                        cleanupExpiredChunks(false);
                    }

                    // Copy packet bytes before async handoff; DatagramPacket reuses the same backing buffer.
                    int packetLength = packet.getLength();
                    byte[] packetCopy = new byte[packetLength];
                    System.arraycopy(packet.getData(), packet.getOffset(), packetCopy, 0, packetLength);

                    // Submit packet processing to thread pool to avoid blocking the receive loop.
                    executorService.submit(() -> processPacket(packetCopy, packetLength));

                } catch (Exception e) {
                    logger.errorAndAddToDb("Error receiving syslog packet: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
                    // Continue receiving despite errors
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Syslog UDP listener error: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Process a single Syslog UDP packet
     *
     * Expected format (from Apigee MessageLogging):
     *   <134>2024-01-15T10:30:00.000Z apigee akto: {"batchData":[{...}]}
     *
     * We extract the JSON payload (from first '{' to end) and parse it.
     */
    private void processPacket(byte[] data, int length) {
        try {
            // Convert bytes to string, trim whitespace
            String raw = new String(data, 0, length, StandardCharsets.UTF_8).trim();
            logger.debugAndAddToDb("Received syslog packet of length: " + length, LoggerMaker.LogDb.DATA_INGESTION);

            // Find the start of the JSON payload (first '{')
            int jsonStart = raw.indexOf('{');
            if (jsonStart == -1) {
                logger.warnAndAddToDb("No JSON found in syslog packet: " + raw.substring(0, Math.min(200, raw.length())), LoggerMaker.LogDb.DATA_INGESTION);
                return;
            }

            // Extract JSON from '{' to end of string
            String json = raw.substring(jsonStart);
            logger.debugAndAddToDb("Extracted JSON payload: " + json.substring(0, Math.min(300, json.length())), LoggerMaker.LogDb.DATA_INGESTION);

            // Parse the outer object to get batchData array
            BasicDBObject outer;
            try {
                outer = BasicDBObject.parse(json);
            } catch (Exception parseErr) {
                logger.warnAndAddToDb("Invalid JSON format in syslog packet. Error: " + parseErr.getMessage() + ". JSON snippet: " + json.substring(0, Math.min(300, json.length())), LoggerMaker.LogDb.DATA_INGESTION);
                return;
            }

            BasicDBList batchArray = (BasicDBList) outer.get("batchData");
            if (batchArray != null) {
                processBatchData(batchArray);
                return;
            }

            BasicDBObject chunkEnvelope = toBasicDBObject(outer.get("aktoChunk"));
            if (chunkEnvelope != null) {
                handleChunk(chunkEnvelope);
                return;
            }

            logger.warnAndAddToDb("No batchData or aktoChunk field found in payload. Available keys: " + outer.keySet(), LoggerMaker.LogDb.DATA_INGESTION);

        } catch (Exception e) {
            logger.errorAndAddToDb("Error processing syslog packet: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private void processBatchData(BasicDBList batchArray) {
        if (batchArray.isEmpty()) {
            logger.warnAndAddToDb("batchData array is empty", LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }

        logger.debugAndAddToDb("Processing " + batchArray.size() + " items from batchData array", LoggerMaker.LogDb.DATA_INGESTION);

        // Process each item in the batch
        int successCount = 0;
        for (Object item : batchArray) {
            try {
                if (!(item instanceof BasicDBObject)) {
                    logger.warnAndAddToDb("Skipping non-object item in batchData: " + item.getClass().getName(), LoggerMaker.LogDb.DATA_INGESTION);
                    continue;
                }

                BasicDBObject dbObject = (BasicDBObject) item;
                logger.debugAndAddToDb("Processing batch item with path: " + dbObject.get("path"), LoggerMaker.LogDb.DATA_INGESTION);

                IngestDataBatch batch = parseIngestDataBatch(dbObject);

                // Insert into Kafka using the existing KafkaUtils
                KafkaUtils.insertData(batch);
                successCount++;

            } catch (Exception e) {
                logger.errorAndAddToDb("Error processing batch item: " + e.getMessage() + ". Item: " + item, LoggerMaker.LogDb.DATA_INGESTION);
                // Continue processing other items
            }
        }

        logger.infoAndAddToDb("Successfully processed " + successCount + "/" + batchArray.size() + " items from batch", LoggerMaker.LogDb.DATA_INGESTION);
    }

    private void handleChunk(BasicDBObject chunkEnvelope) {
        String id = getString(chunkEnvelope, "id");
        int idx = getInt(chunkEnvelope, "idx", -1);
        int total = getInt(chunkEnvelope, "total", -1);
        String payloadPart = getString(chunkEnvelope, "payload");

        if (id.isEmpty()) {
            logger.warnAndAddToDb("Dropping chunk with empty id", LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }
        if (idx < 0 || total <= 0 || idx >= total || total > MAX_TOTAL_CHUNKS) {
            logger.warnAndAddToDb("Dropping invalid chunk envelope for id=" + id + " idx=" + idx + " total=" + total, LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }
        if (payloadPart.isEmpty()) {
            logger.warnAndAddToDb("Dropping empty chunk payload for id=" + id + " idx=" + idx, LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }
        if (chunkBuffers.size() > MAX_ACTIVE_CHUNK_MESSAGES && !chunkBuffers.containsKey(id)) {
            cleanupExpiredChunks(true);
            if (chunkBuffers.size() > MAX_ACTIVE_CHUNK_MESSAGES) {
                logger.warnAndAddToDb("Dropping chunk due to high in-flight chunk message count. id=" + id, LoggerMaker.LogDb.DATA_INGESTION);
                return;
            }
        }

        ChunkAccumulator acc = chunkBuffers.compute(id, (key, existing) -> {
            if (existing == null || existing.totalChunks != total || existing.isExpired()) {
                return new ChunkAccumulator(total);
            }
            return existing;
        });

        ChunkAssemblyResult result = acc.addPart(idx, payloadPart);
        if (result.status == ChunkAssemblyStatus.REJECTED_SIZE) {
            chunkBuffers.remove(id, acc);
            logger.warnAndAddToDb("Dropping oversized reassembled payload for id=" + id, LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }
        if (result.status == ChunkAssemblyStatus.INCOMPLETE) {
            return;
        }

        chunkBuffers.remove(id, acc);

        String assembledJson = result.assembledPayload;
        if (assembledJson == null || assembledJson.isEmpty()) {
            logger.warnAndAddToDb("Chunk reassembly completed with empty payload for id=" + id, LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }

        try {
            BasicDBObject outer = BasicDBObject.parse(assembledJson);
            BasicDBList batchArray = (BasicDBList) outer.get("batchData");
            if (batchArray == null) {
                logger.warnAndAddToDb("Reassembled payload missing batchData for id=" + id + ". Available keys: " + outer.keySet(), LoggerMaker.LogDb.DATA_INGESTION);
                return;
            }
            processBatchData(batchArray);
        } catch (Exception e) {
            logger.warnAndAddToDb("Failed to parse reassembled chunk payload for id=" + id + ". Error: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    /**
     * Convert BasicDBObject to IngestDataBatch
     * Maps MongoDB BSON document fields to IngestDataBatch fields
     */
    private IngestDataBatch parseIngestDataBatch(BasicDBObject dbObject) {
        IngestDataBatch batch = new IngestDataBatch();

        batch.setPath(getString(dbObject, "path"));
        batch.setRequestHeaders(getString(dbObject, "requestHeaders"));
        batch.setResponseHeaders(getString(dbObject, "responseHeaders"));
        batch.setMethod(getString(dbObject, "method"));
        batch.setRequestPayload(getString(dbObject, "requestPayload"));
        batch.setResponsePayload(getString(dbObject, "responsePayload"));
        batch.setIp(getString(dbObject, "ip"));
        batch.setDestIp(getString(dbObject, "destIp"));
        batch.setTime(getString(dbObject, "time"));
        batch.setStatusCode(getString(dbObject, "statusCode"));
        batch.setType(getString(dbObject, "type"));
        batch.setStatus(getString(dbObject, "status"));
        batch.setAkto_account_id(getString(dbObject, "akto_account_id"));
        batch.setAkto_vxlan_id(getString(dbObject, "akto_vxlan_id"));
        batch.setIs_pending(getString(dbObject, "is_pending"));
        batch.setSource(getString(dbObject, "source"));
        batch.setDirection(getString(dbObject, "direction"));
        batch.setProcess_id(getString(dbObject, "process_id"));
        batch.setSocket_id(getString(dbObject, "socket_id"));
        batch.setDaemonset_id(getString(dbObject, "daemonset_id"));
        batch.setEnabled_graph(getString(dbObject, "enabled_graph"));
        batch.setTag(getString(dbObject, "tag"));

        return batch;
    }

    /**
     * Safely get a string value from BasicDBObject, return empty string if not found
     */
    private String getString(BasicDBObject obj, String key) {
        Object value = obj.get(key);
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    private int getInt(BasicDBObject obj, String key, int defaultValue) {
        Object value = obj.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BasicDBObject toBasicDBObject(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BasicDBObject) {
            return (BasicDBObject) obj;
        }
        // Fallback for unexpected container types that still serialize as JSON.
        try {
            return BasicDBObject.parse(obj.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cleanupExpiredChunks(boolean forcePrune) {
        long now = System.currentTimeMillis();
        int removed = 0;

        List<Map.Entry<String, ChunkAccumulator>> snapshot = new ArrayList<>(chunkBuffers.entrySet());
        for (Map.Entry<String, ChunkAccumulator> entry : snapshot) {
            if (entry.getValue().isExpiredAt(now)) {
                if (chunkBuffers.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                }
            }
        }

        if (forcePrune && chunkBuffers.size() > MAX_ACTIVE_CHUNK_MESSAGES) {
            List<Map.Entry<String, ChunkAccumulator>> remaining = new ArrayList<>(chunkBuffers.entrySet());
            remaining.sort(Comparator.comparingLong(e -> e.getValue().lastTouchedAt));
            int toRemove = chunkBuffers.size() - MAX_ACTIVE_CHUNK_MESSAGES;
            for (int i = 0; i < remaining.size() && toRemove > 0; i++) {
                if (chunkBuffers.remove(remaining.get(i).getKey(), remaining.get(i).getValue())) {
                    removed++;
                    toRemove--;
                }
            }
        }

        if (removed > 0) {
            logger.debugAndAddToDb("Cleaned up " + removed + " expired/stale chunk buffers", LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private enum ChunkAssemblyStatus {
        INCOMPLETE,
        COMPLETE,
        REJECTED_SIZE
    }

    private static class ChunkAssemblyResult {
        private final ChunkAssemblyStatus status;
        private final String assembledPayload;

        private ChunkAssemblyResult(ChunkAssemblyStatus status, String assembledPayload) {
            this.status = status;
            this.assembledPayload = assembledPayload;
        }
    }

    private static class ChunkAccumulator {
        private final int totalChunks;
        private final String[] parts;
        private final boolean[] received;
        private int receivedCount = 0;
        private int assembledBytes = 0;
        private final long createdAt;
        private volatile long lastTouchedAt;

        private ChunkAccumulator(int totalChunks) {
            this.totalChunks = totalChunks;
            this.parts = new String[totalChunks];
            this.received = new boolean[totalChunks];
            long now = System.currentTimeMillis();
            this.createdAt = now;
            this.lastTouchedAt = now;
        }

        private synchronized ChunkAssemblyResult addPart(int index, String payload) {
            this.lastTouchedAt = System.currentTimeMillis();

            if (!received[index]) {
                received[index] = true;
                parts[index] = payload;
                receivedCount++;
                assembledBytes += payload.getBytes(StandardCharsets.UTF_8).length;
            }

            if (assembledBytes > MAX_REASSEMBLED_BYTES) {
                return new ChunkAssemblyResult(ChunkAssemblyStatus.REJECTED_SIZE, null);
            }
            if (receivedCount < totalChunks) {
                return new ChunkAssemblyResult(ChunkAssemblyStatus.INCOMPLETE, null);
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < totalChunks; i++) {
                String part = parts[i];
                if (part == null) {
                    return new ChunkAssemblyResult(ChunkAssemblyStatus.INCOMPLETE, null);
                }
                sb.append(part);
            }

            return new ChunkAssemblyResult(ChunkAssemblyStatus.COMPLETE, sb.toString());
        }

        private boolean isExpired() {
            return isExpiredAt(System.currentTimeMillis());
        }

        private boolean isExpiredAt(long now) {
            return now - lastTouchedAt > CHUNK_TTL_MILLIS || now - createdAt > CHUNK_TTL_MILLIS;
        }
    }

}
