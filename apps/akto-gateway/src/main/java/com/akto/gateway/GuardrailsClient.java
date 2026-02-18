package com.akto.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardrailsClient {

    private static final Logger logger = LogManager.getLogger(GuardrailsClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private String guardrailsServiceUrl;
    private int timeout;
    private OkHttpClient httpClient;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        this.timeout = 5000; // 5 seconds default timeout
        this.httpClient = createHttpClient(timeout);
        logger.info("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this.guardrailsServiceUrl = serviceUrl;
        this.timeout = timeout;
        this.httpClient = createHttpClient(timeout);
        logger.info("GuardrailsClient initialized with custom config - URL: {}, Timeout: {}ms",
                serviceUrl, timeout);
    }

    private OkHttpClient createHttpClient(int timeoutMs) {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
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

        try {
            // Build request according to API specification
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("payload", payload);
            if (contextSource != null && !contextSource.isEmpty()) {
                requestBody.put("contextSource", contextSource);
            }

            // Convert request to JSON
            String jsonRequest = objectMapper.writeValueAsString(requestBody);
            logger.debug("Sending request to guardrails service: {}", jsonRequest);

            // Log equivalent curl command for debugging
            String curlCommand = buildCurlCommand(guardrailsServiceUrl + "/api/validate/request", jsonRequest);
            logger.debug("Equivalent curl command:\n{}", curlCommand);

            // Create request body
            RequestBody body = RequestBody.create(jsonRequest, JSON);

            // Build HTTP request
            Request request = new Request.Builder()
                    .url(guardrailsServiceUrl + "/api/validate/request")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            // Execute request
            try (Response response = httpClient.newCall(request).execute()) {
                int statusCode = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";

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
            }

        } catch (IOException e) {
            logger.error("IO Error calling guardrails service: {}", e.getMessage(), e);
            return buildErrorResponse("Failed to call guardrails service: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error calling guardrails service: {}", e.getMessage(), e);
            return buildErrorResponse("Failed to call guardrails service: " + e.getMessage());
        }
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

    /**
     * Build a curl command equivalent for debugging
     */
    private String buildCurlCommand(String url, String jsonBody) {
        StringBuilder curl = new StringBuilder();
        curl.append("curl -X POST '").append(url).append("' \\\n");
        curl.append("  -H 'Content-Type: application/json' \\\n");
        curl.append("  -H 'Accept: application/json' \\\n");
        curl.append("  -d '").append(jsonBody.replace("'", "'\\''")).append("'");
        return curl.toString();
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
