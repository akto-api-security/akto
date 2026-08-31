package com.akto.dto.settings;

import java.util.List;
import java.util.Map;

/**
 * Configuration for the external detection corrector.
 *
 * Local detection can tell that a value looks like an email or a card; it cannot tell whose it is,
 * because that lives in a system outside Akto. When enabled, values whose locally detected type is
 * listed in triggerTypes are sent to url, which may return a more specific data type for the ones
 * it recognises.
 *
 * Grouped into its own object rather than sitting flat on AccountSettings: it is one optional
 * feature, and flattening it added a tenth of that class's fields on its own.
 *
 * The auth token is stored here, so it travels to the runtime over whatever channel serves account
 * settings and must never be echoed back to a UI.
 */
public class DetectionCorrectorSettings {

    public static final String ENABLED = "enabled";
    private boolean enabled;

    public static final String URL = "url";
    private String url;

    public static final String AUTH_TOKEN = "authToken";
    private String authToken;

    /** Locally detected data types worth sending for refinement, e.g. EMAIL, CREDIT_CARD. */
    public static final String TRIGGER_TYPES = "triggerTypes";
    private List<String> triggerTypes;

    /**
     * Akto data type name -> the name the classifier expects on the wire, e.g. CREDIT_CARD -> CARD.
     * A name the classifier does not recognise is silently ignored: it answers 200 with an empty
     * corrections list, indistinguishable from a genuine no-match. Without a mapping that failure
     * is invisible.
     */
    public static final String TYPE_ALIASES = "typeAliases";
    private Map<String, String> typeAliases;

    public static final String TIMEOUT_MS = "timeoutMs";
    private int timeoutMs;

    public static final String MAX_BATCH_SIZE = "maxBatchSize";
    private int maxBatchSize;

    /** Per-value logging, including raw values. Off unless deliberately switched on. */
    public static final String DEBUG = "debug";
    private boolean debug;

    /* --- circuit breaker: runtime processing is single threaded, so a hanging classifier would
       otherwise throttle the whole ingestion pipeline. --- */
    public static final String FAILURE_THRESHOLD = "failureThreshold";
    private int failureThreshold;

    public static final String BREAKER_COOL_OFF_SECONDS = "breakerCoolOffSeconds";
    private int breakerCoolOffSeconds;

    /* --- answer cache: lookups then scale with distinct values first seen, not traffic volume. --- */
    public static final String CACHE_SIZE = "cacheSize";
    private int cacheSize;

    public static final String CACHE_TTL_SECONDS = "cacheTtlSeconds";
    private int cacheTtlSeconds;

    public DetectionCorrectorSettings() {
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public List<String> getTriggerTypes() { return triggerTypes; }
    public void setTriggerTypes(List<String> triggerTypes) { this.triggerTypes = triggerTypes; }

    public Map<String, String> getTypeAliases() { return typeAliases; }
    public void setTypeAliases(Map<String, String> typeAliases) { this.typeAliases = typeAliases; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }

    public int getBreakerCoolOffSeconds() { return breakerCoolOffSeconds; }
    public void setBreakerCoolOffSeconds(int breakerCoolOffSeconds) { this.breakerCoolOffSeconds = breakerCoolOffSeconds; }

    public int getCacheSize() { return cacheSize; }
    public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    @Override
    public String toString() {
        return "{ enabled=" + enabled + ", url='" + url + "', triggerTypes=" + triggerTypes
                + ", typeAliases=" + typeAliases + ", timeoutMs=" + timeoutMs
                + ", maxBatchSize=" + maxBatchSize + ", debug=" + debug + " }";
    }
}
