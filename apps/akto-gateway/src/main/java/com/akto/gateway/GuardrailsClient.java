package com.akto.gateway;

import com.akto.utils.SlackUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardrailsClient {

    private static final Logger logger = LogManager.getLogger(GuardrailsClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private String guardrailsServiceUrl;
    private OkHttpClient httpClient;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        this.httpClient = createHttpClient(10000);
        logger.info("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this.guardrailsServiceUrl = serviceUrl;
        this.httpClient = createHttpClient(timeout);
    }

    private OkHttpClient createHttpClient(int timeoutMs) {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> callValidateRequest(Map<String, Object> request) {
        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            String url = guardrailsServiceUrl + "/api/validate/request";

            logger.info("Calling guardrails service at: {}", url);

            RequestBody body = RequestBody.create(jsonRequest, JSON);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                logger.info("Guardrails response (status {}): {}", response.code(), responseBody);

                if (response.isSuccessful()) {
                    return objectMapper.readValue(responseBody, Map.class);
                } else {
                    logger.warn("Guardrails service returned error status: {}", response.code());
                    return buildErrorResponse("Guardrails service error: HTTP " + response.code());
                }
            }

        } catch (Exception e) {
            logger.error("Error calling guardrails service: {}", e.getMessage(), e);
            String alertMsg = "[guardrails] Service call failed - path: " + request.get("path")
                + ", method: " + request.get("method")
                + ", account: " + request.get("akto_account_id")
                + ", error: " + e.getMessage();
            SlackUtils.sendAlert(alertMsg);
            return buildErrorResponse(e.getMessage());
        }
    }

    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("allowed", false);
        error.put("modified", false);
        error.put("reason", "Guardrails validation failed: " + errorMessage);
        error.put("error", errorMessage);
        return error;
    }

    private static String loadServiceUrlFromEnv() {
        String url = System.getenv("GUARDRAILS_SERVICE_URL");
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8081";
        }
        return url;
    }
}
