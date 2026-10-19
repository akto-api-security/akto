package com.akto.account_job_executor.ingestion;

import com.akto.account_job_executor.bigquery.HttpRetryUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sends already-formatted traffic records to the data ingestion service ({@code /api/ingestData},
 * body {@code {"batchData": [...]}}) with retries on transient failures.
 *
 * Shared by the account-job connectors: each connector builds its own records and uses this
 * client to deliver them.
 */
public class IngestionHttpClient implements AutoCloseable {

    private static final LoggerMaker logger = new LoggerMaker(IngestionHttpClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_RESPONSE_BYTES = 8192;

    private final String ingestionServiceBaseUrl;
    private final String authToken;
    private final CloseableHttpClient httpClient;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final String accountJobId;
    private final int accountId;

    private final int maxRetries;
    private final long retryBaseDelayMs;

    public IngestionHttpClient(String ingestionServiceUrl, String authToken,
            int connectTimeoutMs, int socketTimeoutMs, String jobId, int accountId) {
        this.ingestionServiceBaseUrl = normalizeBaseUrl(ingestionServiceUrl);
        this.authToken = authToken;
        this.accountJobId = jobId;
        this.accountId = accountId;
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
    }

    /**
     * Posts one batch, retrying transient failures (retryable HTTP status codes and I/O errors).
     *
     * @return the number of records sent
     */
    public int sendBatch(List<Map<String, Object>> records, int batchNumber, Runnable heartbeat) throws IOException {
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
                post(records, batchNumber);
                return records.size();
            } catch (IngestionHttpException httpException) {
                if (!HttpRetryUtils.isRetryableHttpStatusCode(httpException.getStatusCode()) || attempt > maxRetries) {
                    throw httpException;
                }
                sleepBeforeRetry(attempt, httpException.getStatusCode());
            } catch (IOException ioException) {
                if (attempt > maxRetries) {
                    throw ioException;
                }
                sleepBeforeRetry(attempt, null);
            }
        }
    }

    private void post(List<Map<String, Object>> records, int batchNumber) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", records);

        HttpPost httpPost = new HttpPost(ingestionServiceBaseUrl + "/api/ingestData");
        httpPost.setHeader("Content-Type", "application/json");
        if (authToken != null && !authToken.trim().isEmpty()) {
            httpPost.setHeader("Authorization", authToken.trim());
        }
        httpPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(payload), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                String responseBody = readResponseBodyBounded(response);
                throw new IngestionHttpException(statusCode, "Ingestion service returned " + statusCode + ": " + responseBody);
            }
            EntityUtils.consumeQuietly(response.getEntity());
            logger.debug("Batch {} sent successfully, response: {}", batchNumber, statusCode);
        }
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

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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
