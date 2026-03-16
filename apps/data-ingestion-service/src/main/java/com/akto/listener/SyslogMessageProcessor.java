package com.akto.listener;

import com.akto.dto.IngestDataBatch;
import com.akto.log.LoggerMaker;
import com.akto.utils.KafkaUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared syslog message processor used by both UDP and TCP listeners.
 * It parses JSON payloads, reassembles chunked envelopes, and publishes to Kafka.
 */
public class SyslogMessageProcessor {

    private static final LoggerMaker logger = new LoggerMaker(SyslogMessageProcessor.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final long CHUNK_TTL_MILLIS = 30_000L;
    private static final int MAX_ACTIVE_CHUNK_MESSAGES = 5000;
    private static final int MAX_REASSEMBLED_BYTES = 1_000_000;
    private static final int MAX_TOTAL_CHUNKS = 500;
    private static final int CLEANUP_INTERVAL_MESSAGES = 200;

    private final ConcurrentHashMap<String, ChunkAccumulator> chunkBuffers = new ConcurrentHashMap<>();
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    public void processPacket(byte[] data, int length) {
        String raw = new String(data, 0, length, StandardCharsets.UTF_8);
        processRaw(raw);
    }

    public void processRaw(String rawMessage) {
        try {
            if (messageCounter.incrementAndGet() % CLEANUP_INTERVAL_MESSAGES == 0) {
                cleanupExpiredChunks(false);
            }

            String raw = rawMessage == null ? "" : rawMessage.trim();
            if (raw.isEmpty()) {
                return;
            }

            logger.debugAndAddToDb("Received syslog message of length: " + raw.length(), LoggerMaker.LogDb.DATA_INGESTION);

            int jsonStart = raw.indexOf('{');
            if (jsonStart == -1) {
                logger.warnAndAddToDb("No JSON found in syslog message: " + raw.substring(0, Math.min(200, raw.length())), LoggerMaker.LogDb.DATA_INGESTION);
                return;
            }

            String json = raw.substring(jsonStart);
            logger.debugAndAddToDb("Extracted JSON payload: " + json.substring(0, Math.min(300, json.length())), LoggerMaker.LogDb.DATA_INGESTION);

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
                KafkaUtils.insertData(batch, false);
                successCount++;

            } catch (Exception e) {
                logger.errorAndAddToDb("Error processing batch item: " + e.getMessage() + ". Item: " + item, LoggerMaker.LogDb.DATA_INGESTION);
            }
        }

        logger.infoAndAddToDb("Successfully processed " + successCount + "/" + batchArray.size() + " items from batch", LoggerMaker.LogDb.DATA_INGESTION);
    }

    private void handleChunk(BasicDBObject chunkEnvelope) {
        String id = getString(chunkEnvelope, "id");
        int idx = getInt(chunkEnvelope, "idx", -1);
        int total = getInt(chunkEnvelope, "total", -1);
        String payloadPart = getString(chunkEnvelope, "payload");
        String reqId = getString(chunkEnvelope, "reqId");
        int payloadBytes = getInt(chunkEnvelope, "bytes", -1);
        String payloadSig = getString(chunkEnvelope, "sig");

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
            if (existing == null || existing.isExpired()) {
                return new ChunkAccumulator(total, reqId, payloadBytes, payloadSig);
            }
            return existing;
        });

        if (acc.totalChunks != total) {
            logger.warnAndAddToDb("Dropping chunk due to total mismatch for id=" + id + " expectedTotal=" + acc.totalChunks + " receivedTotal=" + total, LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }

        ChunkAssemblyResult result = acc.addPart(idx, payloadPart, reqId, payloadBytes, payloadSig);
        if (result.status == ChunkAssemblyStatus.REJECTED_SIZE) {
            chunkBuffers.remove(id, acc);
            logger.warnAndAddToDb("Dropping oversized reassembled payload for id=" + id, LoggerMaker.LogDb.DATA_INGESTION);
            return;
        }
        if (result.status == ChunkAssemblyStatus.REJECTED_MISMATCH) {
            logger.warnAndAddToDb(
                    "Dropping chunk due to envelope mismatch for id=" + id + " idx=" + idx +
                            " (prevents cross-request chunk mixing)",
                    LoggerMaker.LogDb.DATA_INGESTION
            );
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
        REJECTED_SIZE,
        REJECTED_MISMATCH
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
        private final String expectedReqId;
        private final int expectedPayloadBytes;
        private final String expectedPayloadSig;
        private final boolean[] received;
        private int receivedCount = 0;
        private int assembledBytes = 0;
        private final long createdAt;
        private volatile long lastTouchedAt;

        private ChunkAccumulator(int totalChunks, String expectedReqId, int expectedPayloadBytes, String expectedPayloadSig) {
            this.totalChunks = totalChunks;
            this.parts = new String[totalChunks];
            this.expectedReqId = expectedReqId == null ? "" : expectedReqId;
            this.expectedPayloadBytes = expectedPayloadBytes;
            this.expectedPayloadSig = expectedPayloadSig == null ? "" : expectedPayloadSig;
            this.received = new boolean[totalChunks];
            long now = System.currentTimeMillis();
            this.createdAt = now;
            this.lastTouchedAt = now;
        }

        private synchronized ChunkAssemblyResult addPart(int index, String payload, String reqId, int payloadBytes, String payloadSig) {
            this.lastTouchedAt = System.currentTimeMillis();

            if (!isCompatible(reqId, payloadBytes, payloadSig)) {
                return new ChunkAssemblyResult(ChunkAssemblyStatus.REJECTED_MISMATCH, null);
            }

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

        private boolean isCompatible(String reqId, int payloadBytes, String payloadSig) {
            String normalizedReqId = reqId == null ? "" : reqId;
            String normalizedPayloadSig = payloadSig == null ? "" : payloadSig;
            return expectedReqId.equals(normalizedReqId)
                    && expectedPayloadBytes == payloadBytes
                    && expectedPayloadSig.equals(normalizedPayloadSig);
        }

        private boolean isExpired() {
            return isExpiredAt(System.currentTimeMillis());
        }

        private boolean isExpiredAt(long now) {
            return now - lastTouchedAt > CHUNK_TTL_MILLIS || now - createdAt > CHUNK_TTL_MILLIS;
        }
    }
}
