package com.akto.metrics;

/**
 * Single source of truth for Akto metric identifiers: metric names, tag keys, and the bounded
 * tag values (client names, guardrail decisions). Every emitter (InfraMetricsFilter,
 * InfraMetricsListener, HttpTrafficPublisher, AbstractorProxyAction, GuardrailsClient) references
 * these constants, so changing a name or label is a one-line edit here that keeps all emitters -
 * and the dashboards/alerts that query them - consistent.
 *
 * Micrometer renders dots as underscores, so e.g. "akto.http.client.requests" is scraped as
 * akto_http_client_requests_seconds and the "account.id" tag as account_id.
 */
public final class AktoMetrics {

    private AktoMetrics() {
    }

    // ---- Metric names ----
    /** Inbound HTTP server request timer (Prometheus: akto_http_server_requests_seconds). */
    public static final String HTTP_SERVER_REQUESTS = "akto.http.server.requests";
    /** Shared outbound HTTP client timer for every dependency, split by the "client" tag. */
    public static final String HTTP_CLIENT_REQUESTS = "akto.http.client.requests";
    /** Guardrails validation counter, split by the "decision" tag. */
    public static final String GUARDRAILS_VALIDATIONS = "akto.guardrails.validations";

    // ---- Tag keys (shared across metrics so queries read the same everywhere) ----
    public static final String TAG_CLIENT = "client";
    public static final String TAG_ACCOUNT_ID = "account.id";
    public static final String TAG_METHOD = "method";
    public static final String TAG_URI = "uri";
    public static final String TAG_STATUS = "status";
    public static final String TAG_ENDPOINT = "endpoint";
    public static final String TAG_DECISION = "decision";
    /** Auto-added by the OkHttp binder; normalized away in InfraMetricsListener. */
    public static final String TAG_OUTCOME = "outcome";

    // ---- "client" tag values: one per outbound dependency ----
    public static final String CLIENT_GUARDRAILS = "guardrails";
    public static final String CLIENT_HTTP_INGEST = "http_ingest";
    public static final String CLIENT_ULTRON = "ultron";

    // ---- "decision" tag values: guardrails verdict outcome ----
    public static final String DECISION_ALLOWED = "allowed";
    public static final String DECISION_BLOCKED = "blocked";
    public static final String DECISION_FAIL_OPEN = "fail_open";
    public static final String DECISION_UNKNOWN = "unknown";
}
