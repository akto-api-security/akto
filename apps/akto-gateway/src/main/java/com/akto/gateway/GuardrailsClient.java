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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardrailsClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailsClient.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Inline LLM-proxy path: bound guardrails wait so servlet threads release quickly.
    // On timeout/transport failure we fail-open (see buildFailOpenResponse) — same HTTP
    // 200 + Allowed=true shape as a healthy validation, so LiteLLM/k6 keep flowing.
    private static final int TIMEOUT_MS = resolveTimeoutMs();

    private static final OkHttpClient HTTP_CLIENT = buildHttpClient(TIMEOUT_MS);

    private final String guardrailsServiceUrl;

    public GuardrailsClient() {
        this.guardrailsServiceUrl = loadServiceUrlFromEnv();
        loggerMaker.infoAndAddToDb("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this.guardrailsServiceUrl = serviceUrl;
    }

    private static int resolveTimeoutMs() {
        String raw = System.getenv("GUARDRAILS_CLIENT_TIMEOUT_MS");
        if (raw == null || raw.trim().isEmpty()) {
            return 3_000;
        }
        try {
            return Math.max(500, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 3_000;
        }
    }

    private static OkHttpClient buildHttpClient(int timeoutMs) {
        return CoreHTTPClient.client.newBuilder()
                .dispatcher(buildDispatcher())
                .connectionPool(new ConnectionPool(1024, 5L, TimeUnit.MINUTES))
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    private static Dispatcher buildDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(2048);
        dispatcher.setMaxRequestsPerHost(2048);
        return dispatcher;
    }

    /** Transport/degradation failures where we intentionally fail-open like a normal allow. */
    private static boolean isFailOpenTransportError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof InterruptedIOException) {
                return true;
            }
            if (t instanceof IOException) {
                String msg = t.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("timeout") || lower.contains("canceled") || lower.contains("cancelled")) {
                        return true;
                    }
                }
            }
        }
        return false;
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

            String authToken = loadGuardrailsAuthToken();
            if (authToken == null || authToken.trim().isEmpty()) {
                loggerMaker.warnAndAddToDb("DATABASE_ABSTRACTOR_SERVICE_TOKEN is not set");
            } else {
                requestBuilder.addHeader("Authorization", authToken.trim());
            }

            Request httpRequest = requestBuilder.build();

            try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                loggerMaker.infoAndAddToDb("Guardrails response (status {}): {}", response.code(), responseBody);

                if (response.isSuccessful()) {
                    try {
                        return objectMapper.readValue(responseBody, Map.class);
                    } catch (Exception parseEx) {
                        loggerMaker.warnAndAddToDb(
                            "Guardrails response not parseable, failing open - path: {}, error: {}",
                            request.get("path"), parseEx.getMessage());
                        return buildFailOpenResponse("invalid guardrails response: " + parseEx.getMessage());
                    }
                }
                loggerMaker.warnAndAddToDb(
                    "Guardrails service returned error status {}, failing open - path: {}",
                    response.code(), request.get("path"));
                return buildFailOpenResponse("Guardrails service error: HTTP " + response.code());
            }

        } catch (Exception e) {
            if (isFailOpenTransportError(e)) {
                // Expected under load: return the same allow verdict shape as success; do not
                // treat as a client-facing failure or spam Slack on every timeout.
                loggerMaker.warnAndAddToDb(
                    "Guardrails unavailable ({}), failing open - path: {}, method: {}, account: {}",
                    e.getMessage(), request.get("path"), request.get("method"), request.get("akto_account_id"));
                return buildFailOpenResponse(e.getMessage());
            }
            loggerMaker.errorAndAddToDb(e, "Unexpected error calling guardrails service: {}", e.getMessage());
            String alertMsg = "[guardrails] Service call failed - path: " + request.get("path")
                + ", method: " + request.get("method")
                + ", account: " + request.get("akto_account_id")
                + ", error: " + e.getMessage();
            SlackUtils.sendAlert(alertMsg);
            return buildFailOpenResponse(e.getMessage());
        }
    }

    public Map<String, Object> callValidateRequest(Map<String, Object> request) {
        return callValidate(request, "/api/validate/request");
    }

    public Map<String, Object> callValidateResponse(Map<String, Object> request) {
        return callValidate(request, "/api/validate/response");
    }

    /**
     * Same JSON shape as guardrails-service {@code ValidationResult} on allow, plus
     * {@code failOpen} for observability. LiteLLM hook and k6 only require Allowed.
     */
    private Map<String, Object> buildFailOpenResponse(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("Allowed", true);
        result.put("allowed", true);
        result.put("Modified", false);
        result.put("modified", false);
        result.put("ModifiedPayload", "");
        result.put("Reason", "");
        result.put("reason", "");
        result.put("Behaviour", "");
        result.put("behaviour", "");
        result.put("failOpen", true);
        result.put("error", errorMessage);
        return result;
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
