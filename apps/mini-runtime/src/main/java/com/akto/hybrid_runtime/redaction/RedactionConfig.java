package com.akto.hybrid_runtime.redaction;

/**
 * Settings for redacting stored payloads through guardrails-service. Disabled unless
 * AKTO_GUARDRAIL_REDACTION_ENABLED is true and AKTO_GUARDRAIL_REDACTION_URL is set; there is
 * deliberately no default URL, so an install never sends payloads to a host it did not choose.
 */
public final class RedactionConfig {

    static final String ENABLED_ENV = "AKTO_GUARDRAIL_REDACTION_ENABLED";
    static final String URL_ENV = "AKTO_GUARDRAIL_REDACTION_URL";
    static final String SKIP_ON_UNAVAILABLE_ENV = "AKTO_GUARDRAIL_REDACTION_SKIP_ON_UNAVAILABLE";

    public final boolean enabled;
    /** Base URL of guardrails-service, without the /api path. */
    public final String baseUrl;
    public final int connectTimeoutMs;
    /** One HTTP call. Kept below GS's own deadline would be wrong: GS answers within it or reports UNAVAILABLE. */
    public final int callTimeoutMs;
    /** Total time one prepare/queue batch may spend across all its calls; later items become UNAVAILABLE. */
    public final int budgetMs;
    /** Items per call. Must not exceed GUARDRAILS_REDACT_MAX_ITEMS on the service (default 100). */
    public final int chunkSize;
    /** Payloads over this are not sent; the service answers UNAVAILABLE for them anyway. */
    public final int maxPayloadBytes;
    public final int breakerThreshold;
    public final int breakerOpenMs;
    public final int queueCapacity;
    public final int lingerMs;
    /**
     * What to do with a payload that guardrails-service did not redact (UNAVAILABLE: service down,
     * deadline, breaker open, oversize). False (default) stores it unredacted and marked; true does
     * not store it at all. A BASIC result (scanner down, regex scrub applied) is always stored.
     */
    public final boolean skipWhenUnavailable;

    RedactionConfig(boolean enabled, String baseUrl, int connectTimeoutMs, int callTimeoutMs, int budgetMs,
                    int chunkSize, int maxPayloadBytes, int breakerThreshold, int breakerOpenMs,
                    int queueCapacity, int lingerMs) {
        this(enabled, baseUrl, connectTimeoutMs, callTimeoutMs, budgetMs, chunkSize, maxPayloadBytes,
                breakerThreshold, breakerOpenMs, queueCapacity, lingerMs, false);
    }

    RedactionConfig(boolean enabled, String baseUrl, int connectTimeoutMs, int callTimeoutMs, int budgetMs,
                    int chunkSize, int maxPayloadBytes, int breakerThreshold, int breakerOpenMs,
                    int queueCapacity, int lingerMs, boolean skipWhenUnavailable) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.callTimeoutMs = callTimeoutMs;
        this.budgetMs = budgetMs;
        this.chunkSize = chunkSize;
        this.maxPayloadBytes = maxPayloadBytes;
        this.breakerThreshold = breakerThreshold;
        this.breakerOpenMs = breakerOpenMs;
        this.queueCapacity = queueCapacity;
        this.lingerMs = lingerMs;
        this.skipWhenUnavailable = skipWhenUnavailable;
    }

    public static RedactionConfig fromEnv() {
        String url = trimTrailingSlash(System.getenv(URL_ENV));
        boolean enabled = "true".equalsIgnoreCase(System.getenv(ENABLED_ENV)) && url != null && !url.isEmpty();
        return new RedactionConfig(
                enabled,
                url,
                intEnv("AKTO_GUARDRAIL_REDACTION_CONNECT_TIMEOUT_MS", 1000),
                intEnv("AKTO_GUARDRAIL_REDACTION_CALL_TIMEOUT_MS", 8000),
                intEnv("AKTO_GUARDRAIL_REDACTION_BUDGET_MS", 20000),
                intEnv("AKTO_GUARDRAIL_REDACTION_CHUNK_SIZE", 100),
                intEnv("AKTO_GUARDRAIL_REDACTION_MAX_PAYLOAD_BYTES", 256 * 1024),
                intEnv("AKTO_GUARDRAIL_REDACTION_BREAKER_THRESHOLD", 5),
                intEnv("AKTO_GUARDRAIL_REDACTION_BREAKER_OPEN_MS", 30000),
                intEnv("AKTO_GUARDRAIL_REDACTION_QUEUE_CAPACITY", 5000),
                intEnv("AKTO_GUARDRAIL_REDACTION_LINGER_MS", 200),
                "true".equalsIgnoreCase(System.getenv(SKIP_ON_UNAVAILABLE_ENV)));
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static int intEnv(String name, int fallback) {
        String v = System.getenv(name);
        if (v == null || v.trim().isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(v.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
