package com.akto.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class GuardrailsClient {

    private static final Logger logger = LogManager.getLogger(GuardrailsClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String guardrailsServiceUrl;
    private int timeout;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        this.timeout = 5000; // 5 seconds default timeout
        logger.info("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this.guardrailsServiceUrl = serviceUrl;
        this.timeout = timeout;
        logger.info("GuardrailsClient initialized with custom config - URL: {}, Timeout: {}ms",
                serviceUrl, timeout);
    }

    /**
     * Call guardrails service with formatted API request
     * Takes the formatted request from adapter and makes the actual API call
     *
     * @param apiRequest Formatted request with "payload" and "contextSource" fields
     * @return Guardrails validation response
     */
    public Map<String, Object> callValidateRequest(Map<String, Object> apiRequest) {
        logger.info("Calling guardrails /validate/request endpoint");

        try {
            String payload = (String) apiRequest.get("payload");
            String contextSource = (String) apiRequest.get("contextSource");

            if (payload == null || payload.isEmpty()) {
                logger.debug("No payload to validate, allowing request");
                return buildPassedResponse("No payload to validate");
            }

            // Call guardrails service
            Map<String, Object> guardrailsResponse = callGuardrailsService(payload, contextSource);

            logger.info("Guardrails validation completed - Allowed: {}", guardrailsResponse.get("allowed"));
            return guardrailsResponse;

        } catch (Exception e) {
            logger.error("Error calling guardrails service: {}", e.getMessage(), e);
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * Build response for passed validation
     */
    private Map<String, Object> buildPassedResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("allowed", true);
        result.put("passed", true);
        result.put("modified", false);
        result.put("modifiedPayload", "");
        result.put("reason", message);
        result.put("status", "ALLOWED");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * Calls the guardrails service API to validate the request
     * API expects: {"payload": "string", "contextSource": "string"}
     * API returns: {"allowed": boolean, "modified": boolean, "modifiedPayload": "string", "reason": "string", "metadata": {}}
     */
    private Map<String, Object> callGuardrailsService(String payload, String contextSource) {
        logger.info("Calling guardrails service at: {}/api/validate/request", guardrailsServiceUrl);

        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;

        try {
            // Configure request timeout
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(timeout)
                    .setConnectionRequestTimeout(timeout)
                    .setSocketTimeout(timeout)
                    .build();

            // Create HTTP client
            httpClient = HttpClients.createDefault();

            // Create POST request
            HttpPost httpPost = new HttpPost(guardrailsServiceUrl + "/api/validate/request");
            httpPost.setConfig(requestConfig);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");

            // Build request according to API specification
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("payload", payload);
            if (contextSource != null && !contextSource.isEmpty()) {
                requestBody.put("contextSource", contextSource);
            }

            // Convert request to JSON
            String jsonRequest = objectMapper.writeValueAsString(requestBody);
            httpPost.setEntity(new StringEntity(jsonRequest, "UTF-8"));

            logger.debug("Sending request to guardrails service: {}", jsonRequest);

            // Execute request
            response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();

            // Parse response
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            logger.debug("Received response from guardrails service (status {}): {}", statusCode, responseBody);

            // Convert response to Map
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Guardrails service call successful");
                // Add 'passed' field for backward compatibility
                Object allowed = result.get("allowed");
                if (allowed instanceof Boolean) {
                    result.put("passed", (Boolean) allowed);
                    result.put("status", ((Boolean) allowed) ? "ALLOWED" : "BLOCKED");
                }
                result.put("timestamp", System.currentTimeMillis());
                return result;
            } else {
                logger.warn("Guardrails service returned error status: {}", statusCode);
                return buildErrorResponse("Guardrails service error: HTTP " + statusCode);
            }

        } catch (Exception e) {
            logger.error("Error calling guardrails service: {}", e.getMessage(), e);
            return buildErrorResponse("Failed to call guardrails service: " + e.getMessage());
        } finally {
            // Close resources
            try {
                if (response != null) {
                    response.close();
                }
                if (httpClient != null) {
                    httpClient.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing HTTP resources: {}", e.getMessage());
            }
        }
    }

    public boolean isValidationPassed(Map<String, Object> guardrailsResponse) {
        if (guardrailsResponse == null) {
            return false;
        }

        // Check 'allowed' field (from actual API)
        Object allowed = guardrailsResponse.get("allowed");
        if (allowed instanceof Boolean) {
            return (Boolean) allowed;
        }

        // Check 'passed' field (backward compatibility)
        Object passed = guardrailsResponse.get("passed");
        if (passed instanceof Boolean) {
            return (Boolean) passed;
        }

        // Check 'status' field
        Object status = guardrailsResponse.get("status");
        if (status instanceof String) {
            String statusStr = (String) status;
            return "ALLOWED".equalsIgnoreCase(statusStr) || "PASS".equalsIgnoreCase(statusStr);
        }

        return false;
    }

    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("allowed", false);
        error.put("passed", false);
        error.put("modified", false);
        error.put("modifiedPayload", "");
        error.put("status", "ERROR");
        error.put("reason", "Guardrails validation failed: " + errorMessage);
        error.put("message", "Guardrails validation failed: " + errorMessage);
        error.put("error", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    private static String loadServiceUrlFromEnv() {
        String url = System.getenv("GUARDRAILS_SERVICE_URL");
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8081"; // Default URL
        }
        return url;
    }

    public String getGuardrailsServiceUrl() {
        return guardrailsServiceUrl;
    }

    public void setGuardrailsServiceUrl(String guardrailsServiceUrl) {
        this.guardrailsServiceUrl = guardrailsServiceUrl;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
