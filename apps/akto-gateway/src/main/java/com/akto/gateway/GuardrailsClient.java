package com.akto.gateway;

import com.akto.log.LoggerMaker;
import com.akto.utils.SlackUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardrailsClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailsClient.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private String guardrailsServiceUrl;
    private OkHttpClient httpClient;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        this.httpClient = createHttpClient(10000);
        loggerMaker.infoAndAddToDb("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
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
    public Map<String, Object> callValidate(Map<String, Object> request, String endpoint) {
        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            String url = guardrailsServiceUrl + endpoint;

            loggerMaker.infoAndAddToDb("Calling guardrails service at: {}", url);

            RequestBody body = RequestBody.create(jsonRequest, JSON);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                loggerMaker.infoAndAddToDb("Guardrails response (status {}): {}", response.code(), responseBody);

                if (response.isSuccessful()) {
                    return objectMapper.readValue(responseBody, Map.class);
                } else {
                    loggerMaker.warnAndAddToDb("Guardrails service returned error status: {}", response.code());
                    return buildErrorResponse("Guardrails service error: HTTP " + response.code());
                }
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error calling guardrails service: {}", e.getMessage());
            String alertMsg = "[guardrails] Service call failed - path: " + request.get("path")
                + ", method: " + request.get("method")
                + ", account: " + request.get("akto_account_id")
                + ", error: " + e.getMessage();
            SlackUtils.sendAlert(alertMsg);
            return buildErrorResponse(e.getMessage());
        }
    }

    public Map<String, Object> callValidateRequest(Map<String, Object> request) {
        return callValidate(request, "/api/validate/request");
    }

    public Map<String, Object> callValidateResponse(Map<String, Object> request) {
        return callValidate(request, "/api/validate/response");
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
