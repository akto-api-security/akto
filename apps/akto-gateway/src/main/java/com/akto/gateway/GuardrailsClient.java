package com.akto.gateway;

import com.akto.log.LoggerMaker;
import com.akto.utils.OperationalAlerts;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

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
    private final OkHttpClient httpClient;
    private final BiConsumer<String, String> alerts;

    public GuardrailsClient() {
        this(loadServiceUrlFromEnv(), HTTP_CLIENT, OperationalAlerts::send);
        loggerMaker.infoAndAddToDb("GuardrailsClient initialized - URL: {}", guardrailsServiceUrl);
    }

    public GuardrailsClient(String serviceUrl, int timeout) {
        this(serviceUrl, HTTP_CLIENT, OperationalAlerts::send);
    }

    GuardrailsClient(String serviceUrl, OkHttpClient httpClient, BiConsumer<String, String> alerts) {
        this.guardrailsServiceUrl = serviceUrl;
        this.httpClient = httpClient;
        this.alerts = alerts;
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
                // Client-level metrics: Micrometer times/counts every call automatically, so no
                // instrumentation leaks into callValidate. Tags method/uri/status/outcome give
                // latency (p50/p95/p99), throughput, and transport failures (status=IO_ERROR = the
                // fail-open-on-timeout case). uri is the bounded endpoint path; recorded on the
                // global registry, which the data-ingestion-service Prometheus registry is bound to.
                // One shared metric name for every outbound HTTP client (akto.http.client.requests),
                // distinguished by the "client" tag - so all external calls share dashboards/alerts
                // instead of each client minting its own metric name.
                //   client     - names the dependency (here "guardrails"); other clients reuse the
                //                 same name with client="abstractor", client="http_ingest", ...
                //   account.id - deployment account parsed once from the JWT in
                //                DATABASE_ABSTRACTOR_SERVICE_TOKEN (OperationalAlerts.deploymentAccountId,
                //                "unknown" if absent); a per-process constant, so cardinality 1.
                .eventListener(OkHttpMetricsEventListener
                        .builder(Metrics.globalRegistry, EXTERNAL_HTTP_CLIENT_METRIC)
                        .tags(Tags.of("client", "guardrails",
                                "account.id", OperationalAlerts.deploymentAccountId()))
                        .uriMapper(req -> req.url().encodedPath())
                        .includeHostTag(false)
                        .build())
                .build();
    }

    /** Shared metric name for all outbound HTTP clients; the "client" tag names the dependency. */
    public static final String EXTERNAL_HTTP_CLIENT_METRIC = "akto.http.client.requests";

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
        // Single result reference assigned on every return path, so the decision counter is
        // recorded exactly once (in finally) regardless of which branch we exit through.
        Map<String, Object> result = null;
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

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                loggerMaker.infoAndAddToDb("Guardrails response (status {}): {}", response.code(), responseBody);

                if (response.isSuccessful()) {
                    try {
                        result = objectMapper.readValue(responseBody, Map.class);
                        return result;
                    } catch (Exception parseEx) {
                        loggerMaker.warnAndAddToDb(
                            "Guardrails response not parseable, failing open - path: {}, error: {}",
                            request.get("path"), parseEx.getMessage());
                        result = buildFailOpenResponse("invalid guardrails response: " + parseEx.getMessage());
                        return result;
                    }
                }
                loggerMaker.warnAndAddToDb(
                    "Guardrails service returned error status {}, failing open - path: {}",
                    response.code(), request.get("path"));
                result = buildFailOpenResponse("Guardrails service error: HTTP " + response.code());
                return result;
            }

        } catch (Exception e) {
            if (isFailOpenTransportError(e)) {
                // Preserve fail-open behavior; notifications are asynchronous and rate limited.
                loggerMaker.warnAndAddToDb(
                    "Guardrails unavailable ({}), failing open - path: {}, method: {}, account: {}",
                    e.getMessage(), request.get("path"), request.get("method"), request.get("akto_account_id"));
            } else {
                loggerMaker.errorAndAddToDb(e, "Unexpected error calling guardrails service: {}", e.getMessage());
            }

            alertServiceUnreachable(endpoint, e);
            result = buildFailOpenResponse(e.getMessage());
            return result;
        } finally {
            recordDecision(endpoint, result);
        }
    }

    /** Guardrails validation counter, tagged by decision (Prometheus: akto_guardrails_validations_total). */
    public static final String GUARDRAILS_VALIDATIONS_METRIC = "akto.guardrails.validations";

    private static final String DECISION_ALLOWED = "allowed";
    private static final String DECISION_BLOCKED = "blocked";
    private static final String DECISION_FAIL_OPEN = "fail_open";
    private static final String DECISION_UNKNOWN = "unknown";

    // One Counter per (endpoint, decision) pair, cached so we do not rebuild a meter on every
    // validation. Counters bind to the global registry; a registry added later (e.g. the Prometheus
    // registry in InfraMetricsListener, or a test SimpleMeterRegistry) still receives them.
    private static final ConcurrentHashMap<String, Counter> DECISION_COUNTERS = new ConcurrentHashMap<>();

    /**
     * Records exactly one guardrails decision per callValidate. Every verdict in the platform flows
     * through callValidate (http-proxy, the webhooks and Gateway), so this single point covers them
     * all. Wrapped in try/catch so a metrics failure can never break or slow the validation path.
     */
    private static void recordDecision(String endpoint, Map<String, Object> result) {
        try {
            decisionCounter(endpoint, classifyDecision(result)).increment();
        } catch (Exception ignore) {
            // metrics must never affect the guardrails call path
        }
    }

    private static Counter decisionCounter(String endpoint, String decision) {
        return DECISION_COUNTERS.computeIfAbsent(endpoint + "|" + decision, key ->
                Counter.builder(GUARDRAILS_VALIDATIONS_METRIC)
                        .tag("endpoint", endpoint)
                        .tag("decision", decision)
                        .tag("account.id", OperationalAlerts.deploymentAccountId())
                        .register(Metrics.globalRegistry));
    }

    /**
     * fail_open wins whenever no real verdict was produced (buildFailOpenResponse sets failOpen=true,
     * and also Allowed=true - so this must be checked first). Otherwise the verdict is read from both
     * key casings, mirroring Gateway.isAllowed. A real response that carries no verdict field is
     * reported as "unknown" - never assumed allowed, so an answered-but-verdictless response is not
     * miscounted as an allow.
     */
    private static String classifyDecision(Map<String, Object> result) {
        if (result == null || isFailOpen(result)) {
            return DECISION_FAIL_OPEN;
        }
        Object verdict = result.get("Allowed");
        if (verdict == null) {
            verdict = result.get("allowed");
        }
        if (verdict == null) {
            return DECISION_UNKNOWN;
        }
        boolean allowed = (verdict instanceof Boolean)
                ? (Boolean) verdict
                : Boolean.parseBoolean(verdict.toString());
        return allowed ? DECISION_ALLOWED : DECISION_BLOCKED;
    }

    private static boolean isFailOpen(Map<String, Object> result) {
        Object failOpen = result.get("failOpen");
        if (failOpen instanceof Boolean) {
            return (Boolean) failOpen;
        }
        return failOpen != null && Boolean.parseBoolean(failOpen.toString());
    }

    /** Endpoint and exception type only: never request data, which may carry customer payloads. */
    private void alertServiceUnreachable(String endpoint, Exception cause) {
        String account = OperationalAlerts.deploymentAccountId();
        try {
            alerts.accept("guardrails:" + account + ":" + endpoint,
                    "Guardrails service unreachable; traffic was allowed without a verdict"
                    + "\nAccount: " + account + "\nEndpoint: " + OperationalAlerts.label(endpoint)
                    + "\nFailure: " + cause.getClass().getSimpleName());
        } catch (Exception alertError) {
            loggerMaker.warn("Could not enqueue guardrails unreachable alert");
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
