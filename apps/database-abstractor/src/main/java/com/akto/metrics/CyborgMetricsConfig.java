package com.akto.metrics;

/**
 * Single source of truth for cyborg's Prometheus-metrics configuration (all env-driven). Kept
 * separate from the push-based AllMetrics/OpenTelemetry config — this only governs the Micrometer/
 * Prometheus feature in database-abstractor.
 *
 * Everything is OFF by default (opt-in). {@link #isEnabled()} is the master switch: when false, no
 * collection runs and the /metrics endpoint 404s.
 *
 * Env vars:
 *   PROMETHEUS_METRICS_ENABLED   master switch ("true" to enable; default off)
 *   SERVICE_NAME                 overall service name -> "service" common tag (default "cyborg")
 *   METRICS_AUTH_ENABLED         require a token on /metrics (default true; "false" to disable)
 *   METRICS_AUTH_TOKEN           bearer token for /metrics
 */
public final class CyborgMetricsConfig {

    private static final String DEFAULT_SERVICE_NAME = "cyborg";

    // volatile: read on every request; the setters exist only for tests, never for production use.
    private static volatile boolean enabled = "true".equalsIgnoreCase(env("PROMETHEUS_METRICS_ENABLED"));
    private static volatile boolean authEnabled = !"false".equalsIgnoreCase(env("METRICS_AUTH_ENABLED"));
    private static volatile String authToken = env("METRICS_AUTH_TOKEN");

    // Overall service name (general SERVICE_NAME env, not a metrics-specific var).
    private static final String serviceName = firstNonBlank(env("SERVICE_NAME"), DEFAULT_SERVICE_NAME);

    private CyborgMetricsConfig() {
    }

    /** Master switch: when false the entire metrics feature (collection + endpoint) is inert. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Whether /metrics requires a bearer token. */
    public static boolean isAuthEnabled() {
        return authEnabled;
    }

    /** Configured bearer token for /metrics (null/empty when unset). */
    public static String getAuthToken() {
        return authToken;
    }

    /** Value of the "service" common tag applied to every metric. */
    public static String getServiceName() {
        return serviceName;
    }

    private static String env(String key) {
        return System.getenv(key);
    }

    private static String firstNonBlank(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    // ---- test seams (env-derived statics can't be driven from a unit test otherwise) ----
    public static void setEnabledForTest(boolean value) {
        enabled = value;
    }

    public static void setAuthEnabledForTest(boolean value) {
        authEnabled = value;
    }

    public static void setAuthTokenForTest(String value) {
        authToken = value;
    }
}
