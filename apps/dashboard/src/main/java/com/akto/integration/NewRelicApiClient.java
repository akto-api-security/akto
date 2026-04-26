package com.akto.integration;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NewRelic API Client
 * Handles API calls to NewRelic Metrics Ingest API
 */
public class NewRelicApiClient {

    private static final Logger logger = LoggerFactory.getLogger(NewRelicApiClient.class);
    private static final Gson gson = new Gson();

    // NewRelic API endpoints
    private static final String METRICS_API_US = "https://metric-api.newrelic.com/metric/v1/timeseries";
    private static final String METRICS_API_EU = "https://metric-api.eu.newrelic.com/metric/v1/timeseries";

    private final NewRelicConfiguration config;

    public NewRelicApiClient(NewRelicConfiguration config) {
        this.config = config;
    }

    /**
     * Send metrics to NewRelic API
     *
     * @param apiKey NewRelic API key
     * @param accountId NewRelic Account ID
     * @param region Region (US or EU)
     * @param metrics List of metrics to send
     */
    public void sendMetrics(String apiKey, String accountId, String region, List<Map<String, Object>> metrics) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }

        if (metrics == null || metrics.isEmpty()) {
            logger.debug("No metrics to send");
            return;
        }

        // Select endpoint based on region
        String endpoint = "EU".equalsIgnoreCase(region) ? METRICS_API_EU : METRICS_API_US;

        // Build request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("metrics", metrics);

        // Convert to JSON
        String jsonPayload = gson.toJson(payload);

        // Send request
        sendRequest(endpoint, apiKey, accountId, jsonPayload);
    }

    /**
     * Send HTTP request to NewRelic API
     */
    private void sendRequest(String endpoint, String apiKey, String accountId, String jsonPayload) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Set request method and headers
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Api-Key", apiKey);
            conn.setRequestProperty("User-Agent", "Akto/1.0");
            conn.setConnectTimeout(config.getApiTimeoutMs());
            conn.setReadTimeout(config.getApiTimeoutMs());

            // Send payload
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = conn.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            if (responseCode == 202) {
                // 202 Accepted - success
                logger.debug("Metrics sent successfully to NewRelic in {}ms", responseTime);
            } else if (responseCode == 400) {
                // 400 Bad Request
                throw new Exception("Invalid metrics format (400 Bad Request)");
            } else if (responseCode == 401) {
                // 401 Unauthorized
                throw new Exception("Invalid API key (401 Unauthorized)");
            } else if (responseCode == 403) {
                // 403 Forbidden
                throw new Exception("Account not authorized (403 Forbidden)");
            } else if (responseCode == 429) {
                // 429 Too Many Requests
                throw new Exception("Rate limit exceeded (429 Too Many Requests)");
            } else {
                throw new Exception("NewRelic API error: HTTP " + responseCode);
            }

            conn.disconnect();

        } catch (Exception e) {
            logger.error("Error sending metrics to NewRelic: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Test connection to NewRelic API
     */
    public boolean testConnection(String apiKey, String accountId, String region) throws Exception {
        try {
            // Send a single test metric
            Map<String, Object> testMetric = new HashMap<>();
            testMetric.put("timestamp", System.currentTimeMillis());
            testMetric.put("value", 1);
            testMetric.put("name", "akto.test.metric");
            testMetric.put("attributes", new HashMap<String, Object>() {{
                put("accountId", accountId);
                put("test", "true");
            }});

            List<Map<String, Object>> metrics = java.util.Collections.singletonList(testMetric);
            sendMetrics(apiKey, accountId, region, metrics);

            logger.info("NewRelic connection test successful");
            return true;

        } catch (Exception e) {
            logger.error("NewRelic connection test failed: {}", e.getMessage());
            throw e;
        }
    }
}
