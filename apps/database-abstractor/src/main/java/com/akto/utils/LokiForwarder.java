package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Forwards log batches from mini-runtime to Loki for centralized logging
 *
 * Loki Push API format:
 * {
 *   "streams": [
 *     {
 *       "stream": {"module_id": "...", "level": "ERROR"},
 *       "values": [["timestamp_ns", "log_line_json"], ...]
 *     }
 *   ]
 * }
 */
public class LokiForwarder {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LokiForwarder.class);

    private static final String LOKI_URL = System.getenv()
        .getOrDefault("LOKI_PUSH_URL", "http://localhost:3100/loki/api/v1/push");

    private static final int LOKI_MAX_BATCH_SIZE = Integer.parseInt(
        System.getenv().getOrDefault("LOKI_BATCH_SIZE", "1048576")); // 1MB default

    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds

    /**
     * Forward NDJSON log batch to Loki
     * @param ndjsonBatch Newline-delimited JSON logs from mini-runtime
     */
    public static void forwardBatch(String ndjsonBatch) throws Exception {
        if (ndjsonBatch == null || ndjsonBatch.isEmpty()) {
            loggerMaker.infoAndAddToDb("Empty log batch received, skipping", LogDb.DASHBOARD);
            return;
        }

        // Parse NDJSON into individual log lines
        String[] lines = ndjsonBatch.split("\n");
        loggerMaker.infoAndAddToDb("Processing " + lines.length + " log lines for Loki. Target URL: " + LOKI_URL, LogDb.DASHBOARD);

        // Group logs by their labels (module_id, level, log_db, account_id)
        Map<String, List<LogEntry>> streams = groupByLabels(lines);

        // Build Loki payload
        JSONObject lokiPayload = buildLokiPayload(streams);

        loggerMaker.infoAndAddToDb("Built Loki payload with " + streams.size() + " streams, total size: " + lokiPayload.toString().length() + " bytes", LogDb.DASHBOARD);

        // Send to Loki
        sendToLoki(lokiPayload);

        loggerMaker.infoAndAddToDb("Successfully forwarded " + lines.length + " logs to Loki", LogDb.DASHBOARD);
    }

    /**
     * Group log lines by their label combinations
     */
    private static Map<String, List<LogEntry>> groupByLabels(String[] lines) {
        Map<String, List<LogEntry>> streams = new HashMap<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            try {
                JSONObject log = new JSONObject(line);

                // Extract labels
                String moduleId = log.optString("module_id", "unknown");
                String level = log.optString("level", "INFO");
                String logDb = log.optString("log_db", "UNKNOWN");
                String accountId = log.has("account_id") ? String.valueOf(log.getInt("account_id")) : "none";

                // Create label key
                String labelKey = String.format("module_id=%s,level=%s,log_db=%s,account_id=%s",
                    moduleId, level, logDb, accountId);

                // Extract timestamp (milliseconds -> nanoseconds)
                long timestampMs = log.optLong("timestamp", System.currentTimeMillis());
                long timestampNs = timestampMs * 1_000_000; // Convert to nanoseconds

                // Store log entry
                LogEntry entry = new LogEntry(timestampNs, line);
                streams.computeIfAbsent(labelKey, k -> new ArrayList<>()).add(entry);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Failed to parse log line: " + line.substring(0, Math.min(100, line.length())), LogDb.DASHBOARD);
            }
        }

        return streams;
    }

    /**
     * Build Loki push API payload
     */
    private static JSONObject buildLokiPayload(Map<String, List<LogEntry>> streams) {
        JSONObject payload = new JSONObject();
        JSONArray streamsArray = new JSONArray();

        for (Map.Entry<String, List<LogEntry>> entry : streams.entrySet()) {
            String labelKey = entry.getKey();
            List<LogEntry> logs = entry.getValue();

            // Parse label key back into map
            JSONObject streamLabels = new JSONObject();
            for (String pair : labelKey.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    streamLabels.put(kv[0], kv[1]);
                }
            }

            // Build values array: [[timestamp_ns, log_line], ...]
            JSONArray values = new JSONArray();
            for (LogEntry log : logs) {
                JSONArray value = new JSONArray();
                value.put(String.valueOf(log.timestampNs)); // Loki expects string timestamp
                value.put(log.logLine);
                values.put(value);
            }

            // Build stream object
            JSONObject stream = new JSONObject();
            stream.put("stream", streamLabels);
            stream.put("values", values);

            streamsArray.put(stream);
        }

        payload.put("streams", streamsArray);
        return payload;
    }

    /**
     * Send payload to Loki via HTTP POST
     */
    private static void sendToLoki(JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(LOKI_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            // Write payload
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Check response
            int status = conn.getResponseCode();

            if (status == 204 || status == 200) {
                loggerMaker.infoAndAddToDb("Loki accepted batch, status: " + status, LogDb.DASHBOARD);
            } else {
                // Read error response
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                }

                String errorMsg = String.format("Loki returned status %d: %s", status, errorResponse.toString());
                loggerMaker.errorAndAddToDb(errorMsg, LogDb.DASHBOARD);
                throw new Exception(errorMsg);
            }

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Internal class to hold log entry with timestamp
     */
    private static class LogEntry {
        final long timestampNs;
        final String logLine;

        LogEntry(long timestampNs, String logLine) {
            this.timestampNs = timestampNs;
            this.logLine = logLine;
        }
    }
}
