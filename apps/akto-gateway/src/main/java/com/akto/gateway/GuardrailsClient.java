package com.akto.gateway;

import com.akto.log.LoggerMaker;
import com.akto.utils.SlackUtils;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardrailsClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailsClient.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private static final OkHttpClient HTTP_CLIENT = CoreHTTPClient.client.newBuilder()
            .dispatcher(buildDispatcher())
            .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
            .connectTimeout(resolveTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(resolveTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(resolveTimeoutMs(), TimeUnit.MILLISECONDS)
            .callTimeout(resolveTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();

    private final String guardrailsServiceUrl;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        loggerMaker.infoAndAddToDb("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this.guardrailsServiceUrl = serviceUrl;
    }

    private static Dispatcher buildDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(256);
        dispatcher.setMaxRequestsPerHost(128);
        return dispatcher;
    }

    private static int resolveTimeoutMs() {
        String raw = System.getenv("GUARDRAILS_HTTP_TIMEOUT_MS");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            int timeoutMs = Integer.parseInt(raw.trim());
            return timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> callValidate(Map<String, Object> request, String endpoint) {
        try {
            String jsonRequest = objectMapper.writeValueAsString(request);
            String url = guardrailsServiceUrl + endpoint;

            loggerMaker.infoAndAddToDb("Calling guardrails service at: {}", url);

            RequestBody body = RequestBody.create(jsonRequest, JSON);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json");

            if (isGuardrailsAuthEnabled()) {
                String authToken = loadGuardrailsAuthToken();
                if (authToken == null || authToken.trim().isEmpty()) {
                    loggerMaker.warnAndAddToDb("AKTO_GR_AUTHENTICATE is enabled but DATABASE_ABSTRACTOR_SERVICE_TOKEN is not set");
                } else {
                    requestBuilder.addHeader("Authorization", authToken.trim());
                }
            }

            Request httpRequest = requestBuilder.build();

            try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
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

    private static boolean isGuardrailsAuthEnabled() {
        String envValue = System.getenv("AKTO_GR_AUTHENTICATE");
        return "true".equalsIgnoreCase(envValue);
    }

    private static String loadGuardrailsAuthToken() {
        String token = System.getProperty("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        }
        return token;
    }

    private static String loadServiceUrlFromEnv() {
        String url = System.getenv("GUARDRAILS_SERVICE_URL");
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8081";
        }
        return url;
    }
}
