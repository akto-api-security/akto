package com.akto.clients.integration;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Client for NewRelic API calls
 * Handles validation of credentials and metrics submission
 */
public class NewRelicApiClient {

    private static final LoggerMaker logger = new LoggerMaker(NewRelicApiClient.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 10;
    private static final String NR_API_BASE_US = "https://api.newrelic.com/v1";
    private static final String NR_API_BASE_EU = "https://api.eu.newrelic.com/v1";
    private static final String NR_METRICS_ENDPOINT = "/metrics/ingest";
    private static final String NR_ACCOUNT_ENDPOINT = "/accounts/{accountId}";

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    /**
     * Validate NewRelic credentials by making a test API call
     *
     * @param apiKey NewRelic API key
     * @param accountId NewRelic account ID
     * @param region US or EU
     * @return true if valid, false otherwise
     */
    public static boolean validateCredentials(String apiKey, String accountId, String region) {
        try {
            // First validate format
            if (apiKey == null || !apiKey.matches("^(.*)$")) {
                logger.info("Invalid API key format");
                return false;
            }
            if (accountId == null || !accountId.matches("^\\d+$")) {
                logger.info("Invalid account ID format");
                return false;
            }
            if (!region.equals("US") && !region.equals("EU")) {
                logger.info("Invalid region: " + region);
                return false;
            }

            // Make test API call to verify credentials
            String baseUrl = region.equals("EU") ? NR_API_BASE_EU : NR_API_BASE_US;
            String accountUrl = baseUrl + NR_ACCOUNT_ENDPOINT.replace("{accountId}", accountId);

            Request request = new Request.Builder()
                    .url(accountUrl)
                    .addHeader("Api-Key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                logger.info("NewRelic credentials validation response: " + response.code());
                return success;
            }
        } catch (Exception e) {
            logger.error("Error validating NewRelic credentials: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get NewRelic account name by making an API call
     *
     * @param apiKey NewRelic API key
     * @param accountId NewRelic account ID
     * @param region US or EU
     * @return Account name or null
     */
    public static String getAccountName(String apiKey, String accountId, String region) {
        try {
            String baseUrl = region.equals("EU") ? NR_API_BASE_EU : NR_API_BASE_US;
            String accountUrl = baseUrl + NR_ACCOUNT_ENDPOINT.replace("{accountId}", accountId);

            Request request = new Request.Builder()
                    .url(accountUrl)
                    .addHeader("Api-Key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    logger.error("Failed to get account info: " + response.code());
                    return null;
                }

                String responseBody = response.body().string();
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                // Try to extract account name from response
                if (responseMap.containsKey("account")) {
                    Map<String, Object> account = (Map<String, Object>) responseMap.get("account");
                    if (account.containsKey("name")) {
                        return (String) account.get("name");
                    }
                }

                logger.warn("Account name not found in response");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error getting account name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Send metrics to NewRelic Metrics Ingest API
     *
     * @param apiKey NewRelic API key
     * @param accountId NewRelic account ID
     * @param metrics List of metrics to send
     * @return true if success, false otherwise
     */
    public static boolean sendMetrics(String apiKey, String accountId, List<Metric> metrics) {
        try {
            if (metrics == null || metrics.isEmpty()) {
                return true;
            }

            String baseUrl = NR_API_BASE_US; // Metrics ingest always uses US endpoint
            String metricsUrl = baseUrl + NR_METRICS_ENDPOINT;

            // Build metrics payload
            List<Map<String, Object>> metricsPayload = new ArrayList<>();
            for (Metric metric : metrics) {
                Map<String, Object> metricData = new HashMap<>();
                metricData.put("name", metric.getName());
                metricData.put("timestamp", metric.getTimestamp());
                metricData.put("value", metric.getValue());
                metricData.put("attributes", new HashMap<String, Object>() {{
                    put("endpoint", metric.getEndpoint());
                    put("orgId", metric.getOrgId());
                }});
                metricsPayload.add(metricData);
            }

            String jsonPayload = objectMapper.writeValueAsString(metricsPayload);
            RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(metricsUrl)
                    .addHeader("Api-Key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                logger.info("NewRelic metrics send response: " + response.code());
                if (!success && response.body() != null) {
                    logger.error("Metrics send error: " + response.body().string());
                }
                return success;
            }
        } catch (Exception e) {
            logger.error("Error sending metrics to NewRelic: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inner class representing a metric
     */
    public static class Metric {
        private String name;
        private Long timestamp;
        private Double value;
        private String endpoint;
        private Integer orgId;

        public Metric(String name, Long timestamp, Double value, String endpoint, Integer orgId) {
            this.name = name;
            this.timestamp = timestamp;
            this.value = value;
            this.endpoint = endpoint;
            this.orgId = orgId;
        }

        public String getName() { return name; }
        public Long getTimestamp() { return timestamp; }
        public Double getValue() { return value; }
        public String getEndpoint() { return endpoint; }
        public Integer getOrgId() { return orgId; }
    }
}
