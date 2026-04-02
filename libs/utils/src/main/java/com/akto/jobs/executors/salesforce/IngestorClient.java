package com.akto.jobs.executors.salesforce;

import com.akto.jobs.exception.RetryableJobException;
import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.*;
import java.util.UUID;

/**
 * Client for pushing transformed data to Akto's ingestion API.
 * Handles batching, retries, and error handling.
 */
public class IngestorClient {

    private static final LoggerMaker logger = new LoggerMaker(IngestorClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    private final String baseUrl;
    private final String apiKey;
    private final int batchSize;

    public IngestorClient(String baseUrl, String apiKey, int batchSize) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.batchSize = batchSize != 0 ? batchSize : AIAgentConnectorConstants.INGESTION_DEFAULT_BATCH_SIZE;
        logger.info("IngestorClient initialized: baseUrl={}, batchSize={}", baseUrl, this.batchSize);
    }

    /**
     * Push data to Akto ingestion API in batches.
     * Automatically handles batching and retries.
     *
     * @param data List of Akto HTTP entries to push
     * @return Result containing success status and statistics
     * @throws Exception if push fails after retries
     */
    public PushResult pushData(List<Map<String, Object>> data) throws Exception {
        if (data == null || data.isEmpty()) {
            logger.info("No data to push to Akto");
            return new PushResult(true, 0, 0, 0);
        }

        List<List<Map<String, Object>>> batches = createBatches(data);
        int successCount = 0;
        int failureCount = 0;

        logger.infoAndAddToDb("Pushing {} entries to Akto in {} batches", LoggerMaker.LogDb.AGENTIC_TESTING);

        for (int i = 0; i < batches.size(); i++) {
            List<Map<String, Object>> batch = batches.get(i);
            try {
                sendBatch(batch, i + 1, batches.size());
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to send batch {} of {}: {}", i + 1, batches.size(), e.getMessage());
                failureCount++;
            }
        }

        boolean success = failureCount == 0;
        logger.info("Push completed: {} successful, {} failed batches", successCount, failureCount);

        return new PushResult(success, batches.size(), successCount, failureCount);
    }

    /**
     * Create batches from data list.
     */
    private List<List<Map<String, Object>>> createBatches(List<Map<String, Object>> data) {
        List<List<Map<String, Object>>> batches = new ArrayList<>();
        for (int i = 0; i < data.size(); i += batchSize) {
            int end = Math.min(i + batchSize, data.size());
            batches.add(new ArrayList<>(data.subList(i, end)));
        }
        return batches;
    }

    /**
     * Send a single batch to Akto API.
     * Logs detailed information about the request and response for debugging.
     */
    private void sendBatch(List<Map<String, Object>> batch, int batchNumber, int totalBatches) throws Exception {
        String batchId = UUID.randomUUID().toString();

        try {
            String url = baseUrl + AIAgentConnectorConstants.INGESTION_API_ENDPOINT;

            // Log sample entry for debugging
            if (!batch.isEmpty()) {
                Map<String, Object> sample = batch.get(0);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put(AIAgentConnectorConstants.INGESTION_BATCH_DATA_KEY, batch);

            String requestBodyJson = objectMapper.writeValueAsString(payload);
            long requestStart = System.currentTimeMillis();

            Request request = new Request.Builder()
                .url(url)
                .header(AIAgentConnectorConstants.HEADER_X_API_KEY, apiKey)
                .header(AIAgentConnectorConstants.HEADER_CONTENT_TYPE, AIAgentConnectorConstants.CONTENT_TYPE_JSON)
                .header(AIAgentConnectorConstants.HEADER_X_BATCH_ID, batchId)
                .post(RequestBody.create(requestBodyJson, MediaType.parse(AIAgentConnectorConstants.CONTENT_TYPE_JSON)))
                .build();

            Response response = client.newCall(request).execute();
            long requestDuration = System.currentTimeMillis() - requestStart;

            String responseBody = response.body() != null ? response.body().string() : "";
            response.headers().toMultimap().forEach((name, values) ->
                logger.info("  {}: {}", name, String.join(", ", values))
            );

            // Log response body for debugging
            if (!responseBody.isEmpty()) {
                logger.info(responseBody);
            } else {
                logger.info("Response body is empty");
            }

            // Handle 401 as non-retryable (authentication issue)
            if (response.code() == 401) {
                logger.error("✗ AUTHENTICATION FAILED: batchId={}, invalid API key or credentials", batchId);
                throw new Exception("Authentication failed: Invalid API key or expired credentials");
            }

            // Handle 5xx and network errors as retryable
            if (response.code() >= 500 || response.code() == 503) {
                logger.warn("✗ SERVER ERROR: batchId={}, status={}", batchId, response.code());
                throw new RetryableJobException("Akto API server error: " + response.code());
            }

            // Handle other errors
            if (!response.isSuccessful()) {
                throw new Exception("Akto API returned status " + response.code() + ": " + responseBody);
            }

            logger.info("✓ BATCH SUCCESSFUL: batchId={}, batch={}/{}, {} entries ingested in {}ms",
                batchId, batchNumber, totalBatches, batch.size(), requestDuration);

        } catch (RetryableJobException e) {
            throw e;
        } catch (Exception e) {
            logger.error("✗ ERROR sending batch {}/{}: batchId={}, error={}",
                batchNumber, totalBatches, batchId, e.getMessage(), e);
            // Check if this is a network error (retryable)
            if (e.getCause() instanceof java.net.ConnectException ||
                e.getCause() instanceof java.net.SocketTimeoutException ||
                e instanceof java.net.SocketException) {
                throw new RetryableJobException("Network error: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * Result class for push operation statistics.
     */
    public static class PushResult {
        public final boolean success;
        public final int totalBatches;
        public final int successfulBatches;
        public final int failedBatches;

        public PushResult(boolean success, int totalBatches, int successfulBatches, int failedBatches) {
            this.success = success;
            this.totalBatches = totalBatches;
            this.successfulBatches = successfulBatches;
            this.failedBatches = failedBatches;
        }
    }
}
