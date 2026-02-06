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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BigQueryIngestionClient implements AutoCloseable {

    private static final LoggerMaker logger = new LoggerMaker(BigQueryIngestionClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String ingestionServiceUrl;
    private final String authToken;
    private final int batchSize;
    private final CloseableHttpClient httpClient;

    private final int maxRetries;
    private final long baseBackoffMs;

    public BigQueryIngestionClient(String ingestionServiceUrl, String authToken, int batchSize,
            int connectTimeoutMs, int socketTimeoutMs) {
        this.ingestionServiceUrl = normalizeBaseUrl(ingestionServiceUrl);
        this.authToken = authToken;
        this.batchSize = batchSize;
        this.maxRetries = 3;
        this.baseBackoffMs = 500;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);
        cm.setDefaultMaxPerRoute(10);

        this.httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .build();

        logger.info("Ingestion client initialized: url={}, batchSize={}, connectTimeoutMs={}, socketTimeoutMs={}",
                ingestionServiceUrl, batchSize, connectTimeoutMs, socketTimeoutMs);
    }

    public int sendBatchToIngestionService(
            List<Map<String, Object>> batch,
            String source,
            Integer accountId,
            int batchNumber,
            Runnable heartbeat) throws IOException {
        if (batch == null || batch.isEmpty()) {
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
                sendBatchOnce(batch, source, accountId, batchNumber);
                return batch.size();
            } catch (IngestionHttpException ihe) {
                if (!isRetryableStatus(ihe.statusCode) || attempt > maxRetries) {
                    throw ihe;
                }
                sleepBackoff(attempt, ihe.statusCode);
            } catch (IOException ioe) {
                if (attempt > maxRetries) {
                    throw ioe;
                }
                sleepBackoff(attempt, null);
            }
        }
    }

    private void sendBatchOnce(List<Map<String, Object>> batch, String source, Integer accountId, int batchNumber)
            throws IOException {
        List<Map<String, Object>> batchData = new ArrayList<>();

        for (Map<String, Object> row : batch) {
            batchData.add(transformToIngestFormat(row, source, accountId));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", batchData);

        String jsonPayload = mapper.writeValueAsString(payload);

        HttpPost httpPost = new HttpPost(ingestionServiceUrl + "/api/ingestData");
        httpPost.setHeader("Content-Type", "application/json");
        if (authToken != null && !authToken.trim().isEmpty()) {
            httpPost.setHeader("Authorization", authToken.trim());
        }
        httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";

            if (statusCode != 200) {
                throw new IngestionHttpException(statusCode,
                        "Ingestion service returned " + statusCode + ": " + responseBody);
            }
            logger.debug("Batch {} sent successfully, response: {}", batchNumber, statusCode);
        }
    }

    private Map<String, Object> transformToIngestFormat(Map<String, Object> row, String source, Integer accountId)
            throws IOException {
        Map<String, Object> ingestData = new HashMap<>();

        ingestData.put("path", getOrDefault(row, "endpoint", "/"));

        String requestPayload = getOrDefault(row, "request_payload", "");
        ingestData.put("requestPayload", requestPayload);

        String responsePayload = getOrDefault(row, "response_payload", "");
        ingestData.put("responsePayload", responsePayload);

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("x-deployed-model-id", getOrDefault(row, "deployed_model_id", ""));
        requestHeaders.put("x-request-id", getOrDefault(row, "request_id", ""));
        requestHeaders.put("x-source", source);
        ingestData.put("requestHeaders", mapper.writeValueAsString(requestHeaders));

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");
        ingestData.put("responseHeaders", mapper.writeValueAsString(responseHeaders));

        ingestData.put("method", "POST");
        ingestData.put("statusCode", "200");
        ingestData.put("type", "HTTP/1.1");
        ingestData.put("status", "");

        Object loggingTimeObj = row.get("logging_time");
        String loggingTime;
        if (loggingTimeObj instanceof Number) {
            loggingTime = String.valueOf(((Number) loggingTimeObj).longValue());
        } else if (loggingTimeObj != null) {
            loggingTime = loggingTimeObj.toString();
        } else {
            loggingTime = String.valueOf(System.currentTimeMillis());
        }
        ingestData.put("time", loggingTime);

        ingestData.put("source", source);
        ingestData.put("tag", "Gen-AI");
        ingestData.put("ip", "");
        ingestData.put("destIp", "");
        ingestData.put("akto_account_id", accountId != null ? String.valueOf(accountId) : "");
        ingestData.put("akto_vxlan_id", "");
        ingestData.put("is_pending", "false");
        ingestData.put("direction", "");
        ingestData.put("process_id", "");
        ingestData.put("socket_id", "");
        ingestData.put("daemonset_id", "");
        ingestData.put("enabled_graph", "false");

        return ingestData;
    }

    private String getOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean isRetryableStatus(Integer statusCode) {
        if (statusCode == null)
            return true;
        return statusCode == 408 || statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503
                || statusCode == 504;
    }

    private void sleepBackoff(int attempt, Integer statusCode) throws IOException {
        long delay = baseBackoffMs * (1L << Math.min(6, attempt - 1));
        long jitter = (long) (Math.random() * 200);
        long sleepMs = Math.min(10_000, delay + jitter);

        logger.info("Retrying ingestion batch (attempt {}), statusCode={}, sleeping {}ms", attempt, statusCode,
                sleepMs);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Ingestion interrupted", ie);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        String trimmed = baseUrl.trim();
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
        logger.debug("Ingestion client closed");
    }

    public static class IngestionHttpException extends IOException {
        private final int statusCode;

        public IngestionHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
