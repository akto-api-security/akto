package com.akto.account_job_executor.bigquery;

import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BigQueryIngestionClient {

    private static final LoggerMaker logger = new LoggerMaker(BigQueryIngestionClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String ingestionServiceUrl;
    private final String authToken;
    private final int batchSize;

    public BigQueryIngestionClient(String ingestionServiceUrl, String authToken) {
        this(ingestionServiceUrl, authToken, 100);
    }

    public BigQueryIngestionClient(String ingestionServiceUrl, String authToken, int batchSize) {
        this.ingestionServiceUrl = ingestionServiceUrl;
        this.authToken = authToken;
        this.batchSize = batchSize;
        logger.info("Ingestion client initialized: url={}, batchSize={}", ingestionServiceUrl, batchSize);
    }

    public int sendToIngestionService(List<Map<String, Object>> rows, String source) throws IOException {
        if (rows == null || rows.isEmpty()) {
            logger.info("No data to send to ingestion service");
            return 0;
        }

        logger.info("Sending {} records to ingestion service in batches of {}", rows.size(), batchSize);

        int totalSent = 0;
        int batchNumber = 0;

        for (int i = 0; i < rows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, rows.size());
            List<Map<String, Object>> batch = rows.subList(i, end);
            batchNumber++;

            try {
                sendBatch(batch, source, batchNumber);
                totalSent += batch.size();
                logger.debug("Batch {}: sent {} records", batchNumber, batch.size());
            } catch (IOException e) {
                logger.error("Failed to send batch {}: {}", batchNumber, e.getMessage());
                throw e;
            }
        }

        logger.info("Successfully sent {} records in {} batches", totalSent, batchNumber);
        return totalSent;
    }

    private void sendBatch(List<Map<String, Object>> batch, String source, int batchNumber) throws IOException {
        List<Map<String, Object>> batchData = new ArrayList<>();

        for (Map<String, Object> row : batch) {
            batchData.add(transformToIngestFormat(row, source));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", batchData);

        String jsonPayload = mapper.writeValueAsString(payload);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(ingestionServiceUrl + "/api/ingestData");
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("authorization", authToken);
            httpPost.setEntity(new StringEntity(jsonPayload));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    throw new IOException("Ingestion service returned " + statusCode + ": " + responseBody);
                }
                logger.debug("Batch {} sent successfully, response: {}", batchNumber, statusCode);
            }
        }
    }

    private Map<String, Object> transformToIngestFormat(Map<String, Object> row, String source) throws IOException {
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

        String loggingTime = getOrDefault(row, "logging_time", String.valueOf(System.currentTimeMillis()));
        ingestData.put("time", loggingTime);

        ingestData.put("source", source);
        ingestData.put("tag", "Gen-AI");
        ingestData.put("ip", "");
        ingestData.put("destIp", "");
        ingestData.put("akto_account_id", "");
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

    public void close() {
        logger.debug("Ingestion client closed");
    }
}
