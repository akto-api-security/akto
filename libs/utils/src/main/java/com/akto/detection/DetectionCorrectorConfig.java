package com.akto.detection;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings for the external detection corrector.
 *
 * Everything comes from account settings; the runtime reads no environment variables for this.
 */
public class DetectionCorrectorConfig {

    private boolean enabled;
    private String url;
    private String authToken;
    /** Akto data type name -> the name the classifier expects on the wire, e.g. CREDIT_CARD -> CARD. */
    private Map<String, String> typeAliases = new HashMap<>();
    private Set<String> triggerSubTypes = new HashSet<>();
    private int timeoutMs = 200;
    private int maxBatchSize = 100;
    private int cacheSize = 50_000;
    private int cacheTtlSeconds = 3600;
    /** Consecutive failures before the breaker opens. */
    private int failureThreshold = 5;
    /** How long the breaker stays open before a probe is allowed through. */
    private int breakerCoolOffSeconds = 30;

    public DetectionCorrectorConfig() {
    }

    public boolean isUsable() {
        return enabled && url != null && !url.trim().isEmpty() && !triggerSubTypes.isEmpty();
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public Map<String, String> getTypeAliases() {
        return typeAliases == null ? Collections.<String, String>emptyMap() : typeAliases;
    }

    public void setTypeAliases(Map<String, String> typeAliases) {
        this.typeAliases = typeAliases == null ? new HashMap<String, String>() : typeAliases;
    }

    /**
     * Translates Akto's data type name into whatever the classifier calls it. Falls back to the
     * Akto name when no alias is configured.
     */
    public String wireTypeFor(String aktoTypeName) {
        if (aktoTypeName == null) return null;
        String alias = getTypeAliases().get(aktoTypeName);
        return alias == null ? aktoTypeName : alias;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Set<String> getTriggerSubTypes() {
        return triggerSubTypes == null ? Collections.<String>emptySet() : triggerSubTypes;
    }

    public void setTriggerSubTypes(Set<String> triggerSubTypes) {
        this.triggerSubTypes = triggerSubTypes == null ? new HashSet<String>() : triggerSubTypes;
    }

    public void setTriggerSubTypesFromList(List<String> names) {
        Set<String> set = new LinkedHashSet<>();
        if (names != null) {
            for (String n : names) {
                if (n != null && !n.trim().isEmpty()) set.add(n.trim().toUpperCase());
            }
        }
        this.triggerSubTypes = set;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        if (timeoutMs > 0) this.timeoutMs = timeoutMs;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        if (maxBatchSize > 0) this.maxBatchSize = maxBatchSize;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        if (cacheSize > 0) this.cacheSize = cacheSize;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        if (cacheTtlSeconds > 0) this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        if (failureThreshold > 0) this.failureThreshold = failureThreshold;
    }

    public int getBreakerCoolOffSeconds() {
        return breakerCoolOffSeconds;
    }

    public void setBreakerCoolOffSeconds(int breakerCoolOffSeconds) {
        if (breakerCoolOffSeconds > 0) this.breakerCoolOffSeconds = breakerCoolOffSeconds;
    }

    @Override
    public String toString() {
        // Used as a change-detection signature by the installer, and logged. The token is reduced
        // to a hash so a rotation still triggers a rebuild without ever printing the secret.
        String tokenMarker = (authToken == null || authToken.trim().isEmpty())
                ? "none" : "set#" + authToken.hashCode();
        return "{ enabled=" + enabled + ", url='" + url + "', triggers=" + getTriggerSubTypes()
                + ", typeAliases=" + getTypeAliases()
                + ", timeoutMs=" + timeoutMs + ", maxBatchSize=" + maxBatchSize
                + ", token=" + tokenMarker + " }";
    }
}
