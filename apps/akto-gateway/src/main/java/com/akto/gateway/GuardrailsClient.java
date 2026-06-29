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

    // Inline LLM-proxy path: a slow guardrails call must not hold the request long.
    // 3s bounds tail latency and thread/connection pileup; on timeout the call
    // fails open (see buildErrorResponse) so traffic keeps flowing. Sized for
    // ~100k RPM ingress.
    private static final int TIMEOUT_MS = 3_000;

    private static final OkHttpClient HTTP_CLIENT = CoreHTTPClient.client.newBuilder()
            .dispatcher(buildDispatcher())
            .connectionPool(new ConnectionPool(512, 5L, TimeUnit.MINUTES))
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
        dispatcher.setMaxRequests(2048);
        dispatcher.setMaxRequestsPerHost(2048);
        return dispatcher;
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

    // Fail-OPEN on guardrails-service infrastructure failures (timeout, connection
    // refused, 5xx, unparseable body). This path is reached ONLY on transport/error
    // conditions — a genuine block is an HTTP 200 with allowed=false, handled at the
    // isSuccessful() branch above and never routed here. Failing open keeps LLM
    // traffic flowing when guardrails is degraded, consistent with the fail-open
    // backpressure design in agent-guard and guardrails-service: availability over
    // enforcement under degradation. The error is still surfaced in `reason`/`error`
    // for observability.
    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        // Mirror a normal guardrails-service allow verdict shape so every consumer
        // reads the fail-open decision the same way. guardrails-service emits the
        // capital-cased "Allowed"/"Modified"; the lowercase keys are kept for
        // back-compat with internal isAllowed() (which checks both).
        error.put("Allowed", true);
        error.put("allowed", true);
        error.put("Modified", false);
        error.put("modified", false);
        error.put("reason", "Guardrails unavailable, failing open: " + errorMessage);
        error.put("error", errorMessage);
        error.put("failOpen", true);
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
