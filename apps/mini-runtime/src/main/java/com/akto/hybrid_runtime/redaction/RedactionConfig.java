package com.akto.hybrid_runtime.redaction;

public final class RedactionConfig {

    static final String ENABLED_ENV = "AKTO_GUARDRAIL_REDACTION_ENABLED";
    static final String URL_ENV = "AKTO_GUARDRAIL_REDACTION_URL";
    static final String SKIP_ON_UNAVAILABLE_ENV = "AKTO_GUARDRAIL_REDACTION_SKIP_ON_UNAVAILABLE";

    public final boolean enabled;
    public final String baseUrl;
    public final int connectTimeoutMs;
    public final int callTimeoutMs;
    public final int budgetMs;
    public final int chunkSize;
    public final int maxPayloadBytes;
    public final int breakerThreshold;
    public final int breakerOpenMs;
    public final int queueCapacity;
    public final int lingerMs;
    /** true: do not store payloads that could not be redacted (BASIC is still stored); false: store them marked UNAVAILABLE. */
    public final boolean skipWhenUnavailable;

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
