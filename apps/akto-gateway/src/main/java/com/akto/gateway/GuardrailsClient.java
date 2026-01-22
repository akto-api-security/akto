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

    public boolean shouldApplyGuardrails(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return false;
        }

        Object guardrailsValue = queryParams.get("guardrails");
        if (guardrailsValue == null) {
            return false;
        }

        if (guardrailsValue instanceof Boolean) {
            return (Boolean) guardrailsValue;
        }

        if (guardrailsValue instanceof String) {
            String strValue = (String) guardrailsValue;
            return "true".equalsIgnoreCase(strValue) || "1".equals(strValue);
        }

        return false;
    }

    public Map<String, Object> validateRequest(String url, String path,
                                                Map<String, Object> request,
                                                Map<String, Object> response) {
        logger.info("Validating request through guardrails service - URL: {}", url);

        try {
            Map<String, Object> guardrailsRequest = buildGuardrailsRequest(url, path, request, response);

            // Call guardrails service (TODO: Replace with actual HTTP call)
            Map<String, Object> guardrailsResponse = callGuardrailsService(guardrailsRequest);

            logger.info("Guardrails validation completed - Status: {}", guardrailsResponse.get("status"));
            return guardrailsResponse;

        } catch (Exception e) {
            logger.error("Error calling guardrails service: {}", e.getMessage(), e);
            return buildErrorResponse(e.getMessage());
        }
    }

    private Map<String, Object> buildGuardrailsRequest(String url, String path,
                                                        Map<String, Object> request,
                                                        Map<String, Object> response) {
        Map<String, Object> payload = new HashMap<>();

        // Core fields
        payload.put("url", url);
        payload.put("path", path);
        payload.put("request", request);
        payload.put("response", response);

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("clientVersion", "1.0.0");
        metadata.put("source", "akto-gateway");
        payload.put("metadata", metadata);

        return payload;
    }

    /**
     * Calls the guardrails service API to validate the request
     */
    private Map<String, Object> callGuardrailsService(Map<String, Object> request) {
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

            // Convert request to JSON
            String jsonRequest = objectMapper.writeValueAsString(request);
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

        // Check 'passed' field
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
        error.put("passed", false);
        error.put("status", "ERROR");
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
