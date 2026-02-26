package com.akto.listener;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private int port;
    private ExecutorService executorService;

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

                    // Submit packet processing to thread pool to avoid blocking the receive loop
                    executorService.submit(() -> processPacket(packet.getData(), packet.getLength()));

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

            if (batchArray == null) {
                logger.warnAndAddToDb("No batchData field found in payload. Available keys: " + outer.keySet(), LoggerMaker.LogDb.DATA_INGESTION);
                return;
            }

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

        } catch (Exception e) {
            logger.errorAndAddToDb("Error processing syslog packet: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
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

}
