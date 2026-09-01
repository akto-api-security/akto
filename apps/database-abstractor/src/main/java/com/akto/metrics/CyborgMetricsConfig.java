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
 *   APP_NAME                     this instance's role -> "app" common tag (default "unknown")
 *                                e.g. api-service / consumer / traffic / consumer-acct.
 *                                (The fleet-level "service" label is appended by the Prometheus
 *                                 scrape job, NOT emitted by the app.)
 *   METRICS_AUTH_ENABLED         require a token on /metrics (default true; "false" to disable)
 *   METRICS_AUTH_TOKEN           bearer token for /metrics
 */
public final class CyborgMetricsConfig {

    private static final String DEFAULT_APP_NAME = "unknown";

    // volatile: read on every request; the setters exist only for tests, never for production use.
    private static volatile boolean enabled = "true".equalsIgnoreCase(env("PROMETHEUS_METRICS_ENABLED"));
    private static volatile boolean authEnabled = !"false".equalsIgnoreCase(env("METRICS_AUTH_ENABLED"));
    private static volatile String authToken = env("METRICS_AUTH_TOKEN");

    // This instance's role/component -> the "app" common tag. The fleet ("service") is set by
    // the Prometheus job, so the app only owns "app".
    private static final String appName = firstNonBlank(env("APP_NAME"), DEFAULT_APP_NAME);

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

    /** Value of the "app" common tag applied to every metric (this instance's role). */
    public static String getAppName() {
        return appName;
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
