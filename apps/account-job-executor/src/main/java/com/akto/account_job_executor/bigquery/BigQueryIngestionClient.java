package com.akto.account_job_executor.bigquery;

import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BigQueryIngestionClient implements AutoCloseable {

    private static final LoggerMaker logger = new LoggerMaker(BigQueryIngestionClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_ERROR_RESPONSE_BYTES = 8192;

    private final String ingestionServiceBaseUrl;
    private final String authToken;
    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final String accountJobId;
    private final int accountId;
    private final String jobType;
    private final String jobSubType;

    private final int maxRetries;
    private final long retryBaseDelayMs;

    public BigQueryIngestionClient(String ingestionServiceUrl, String authToken,
            int connectTimeoutMs, int socketTimeoutMs, String jobId, int accountId,
            String jobType, String jobSubType) {
        this.ingestionServiceBaseUrl = normalizeBaseUrl(ingestionServiceUrl);
        this.authToken = authToken;
        this.accountJobId = jobId;
        this.accountId = accountId;
        this.jobType = jobType;
        this.jobSubType = jobSubType;
        this.maxRetries = 3;
        this.retryBaseDelayMs = 500;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .build();

        this.connectionManager = new PoolingHttpClientConnectionManager();
        this.connectionManager.setMaxTotal(50);
        this.connectionManager.setDefaultMaxPerRoute(10);

        this.httpClient = HttpClients.custom()
                .setConnectionManager(this.connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .build();

        logger.info("Ingestion client initialized: jobId={}, accountId={}, jobType={}, jobSubType={}, url={}",
                jobId, accountId, jobType, jobSubType, ingestionServiceUrl);
    }

    public int ingestRecordsBatch(
            List<Map<String, Object>> records,
            String source,
            int batchNumber,
            Runnable heartbeat) throws IOException {
        if (records == null || records.isEmpty()) {
            return 0;
        }

        int attempt = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Ingestion interrupted");
            }

            attempt++;
            try {
                if (heartbeat != null) {
                    heartbeat.run();
                }
                postIngestionRequest(records, source, batchNumber);
                return records.size();
            } catch (IngestionHttpException httpException) {
                if (!HttpRetryUtils.isRetryableHttpStatusCode(httpException.statusCode) || attempt > maxRetries) {
                    throw httpException;
                }
                sleepBeforeRetry(attempt, httpException.statusCode);
            } catch (IOException ioException) {
                if (attempt > maxRetries) {
                    throw ioException;
                }
                sleepBeforeRetry(attempt, null);
            }
        }
    }

    private void postIngestionRequest(List<Map<String, Object>> records, String source, int batchNumber)
            throws IOException {
        List<Map<String, Object>> formattedRecords = new ArrayList<>();

        for (Map<String, Object> row : records) {
            formattedRecords.add(toIngestionRecord(row, source));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", formattedRecords);

        String jsonPayload = OBJECT_MAPPER.writeValueAsString(payload);

        HttpPost httpPost = new HttpPost(ingestionServiceBaseUrl + "/api/ingestData");
        httpPost.setHeader("Content-Type", "application/json");
        if (authToken != null && !authToken.trim().isEmpty()) {
            httpPost.setHeader("Authorization", authToken.trim());
        }
        httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                String responseBody = readResponseBodyBounded(response);
                throw new IngestionHttpException(statusCode,
                        "Ingestion service returned " + statusCode + ": " + responseBody);
            }

            EntityUtils.consumeQuietly(response.getEntity());
            logger.debug("Batch {} sent successfully, response: {}", batchNumber, statusCode);
        }
    }

    private Map<String, Object> toIngestionRecord(Map<String, Object> row, String source)
            throws IOException {
        Map<String, Object> ingestionRecord = new HashMap<>();

        String endpointValue = getStringValueOrDefault(row, "endpoint", "/");
        String path = "/";
        String host = "";

        // Vertex AI endpoint format: projects/{PROJECT}/locations/{LOCATION}/endpoints/{ENDPOINT_ID}
        // Host URL = {LOCATION}-aiplatform.googleapis.com, path = /v1/{endpoint}:predict
        String[] endpointParts = endpointValue.split("/");
        if (endpointParts.length >= 4 && "locations".equals(endpointParts[2])) {
            String location = endpointParts[3];
            host = location + "-aiplatform.googleapis.com";
            path = "/v1/" + endpointValue + ":predict";
        } else {
            path = endpointValue.startsWith("/") ? endpointValue : "/" + endpointValue;
        }
        ingestionRecord.put("path", path);

        String requestPayload = getStringValueOrDefault(row, "request_payload", "");
        ingestionRecord.put("requestPayload", requestPayload);

        String responsePayload = getStringValueOrDefault(row, "response_payload", "");
        ingestionRecord.put("responsePayload", responsePayload);

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", host);
        requestHeaders.put("content-type", "application/json");
        requestHeaders.put("x-deployed-model-id", getStringValueOrDefault(row, "deployed_model_id", ""));
        requestHeaders.put("x-request-id", getStringValueOrDefault(row, "request_id", ""));
        requestHeaders.put("x-source", source);
        ingestionRecord.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(requestHeaders));

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");
        ingestionRecord.put("responseHeaders", OBJECT_MAPPER.writeValueAsString(responseHeaders));

        ingestionRecord.put("method", "POST");
        ingestionRecord.put("statusCode", "200");
        ingestionRecord.put("type", "HTTP/1.1");
        ingestionRecord.put("status", "OK");

        Object loggingTimeValue = row.get("logging_time");
        String loggingTime;
        if (loggingTimeValue instanceof Number) {
            loggingTime = String.valueOf(((Number) loggingTimeValue).longValue());
        } else if (loggingTimeValue != null) {
            loggingTime = loggingTimeValue.toString();
        } else {
            loggingTime = String.valueOf(System.currentTimeMillis());
        }
        ingestionRecord.put("time", loggingTime);

        ingestionRecord.put("source", source);
        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("gen-ai", "Gen AI");
        ingestionRecord.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));
        ingestionRecord.put("ip", "");
        ingestionRecord.put("destIp", "");
        ingestionRecord.put("akto_account_id", String.valueOf(accountId));
        ingestionRecord.put("akto_job_type", jobType != null ? jobType : "");
        ingestionRecord.put("akto_job_sub_type", jobSubType != null ? jobSubType : "");
        ingestionRecord.put("akto_vxlan_id", "");
        ingestionRecord.put("is_pending", "false");
        ingestionRecord.put("direction", "");
        ingestionRecord.put("process_id", "");
        ingestionRecord.put("socket_id", "");
        ingestionRecord.put("daemonset_id", "");
        ingestionRecord.put("enabled_graph", "false");

        return ingestionRecord;
    }

    private String readResponseBodyBounded(CloseableHttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }
        try (InputStream inputStream = response.getEntity().getContent()) {
            byte[] buffer = new byte[MAX_ERROR_RESPONSE_BYTES];
            int totalRead = 0;
            int bytesRead;
            while (totalRead < buffer.length
                    && (bytesRead = inputStream.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
                totalRead += bytesRead;
            }
            return new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(failed to read response body: " + e.getMessage() + ")";
        }
    }

    private String getStringValueOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private void sleepBeforeRetry(int attempt, Integer statusCode) throws IOException {
        long delay = retryBaseDelayMs * (1L << Math.min(6, attempt - 1));
        long jitter = (long) (Math.random() * 200);
        long sleepMs = Math.min(10_000, delay + jitter);

        logger.info("Retrying ingestion: attempt={}, jobId={}, accountId={}, statusCode={}, delayMs={}",
                attempt, accountJobId, accountId, statusCode, sleepMs);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Ingestion interrupted", e);
        }
    }

    private String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException ignored) {
            // Ignore close errors
        }
        try {
            connectionManager.close();
        } catch (Exception ignored) {
            // Ignore close errors
        }
        logger.debug("Ingestion client closed");
    }

    public static class IngestionHttpException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;

        public IngestionHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isRetryable() {
            return HttpRetryUtils.isRetryableHttpStatusCode(statusCode);
        }
    }
}
