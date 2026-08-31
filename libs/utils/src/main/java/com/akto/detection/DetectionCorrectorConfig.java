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
    /** Consecutive failures before the breaker opens. */
    private int failureThreshold = 20;
    /** How long the breaker stays open before a probe is allowed through. */
    private int breakerCoolOffSeconds = 30;

    /* --- the queue parameters wait on, and how long an answer stands. --- */
    private String candidateTopic = "akto.detection.candidates";
    /**
     * Partitions to ask for when creating the topic. This is what lets several runtime instances
     * share classification work, and it cannot be raised later without a topic operation, so it is
     * worth getting right at creation. Ignored if the topic already exists.
     */
    private int candidateTopicPartitions = 3;
    private int paramCacheSize = 100_000;
    private int paramCacheTtlSeconds = 86_400;

    public DetectionCorrectorConfig() {
    }

    public boolean isUsable() {
        return enabled
                && url != null && !url.trim().isEmpty()
                && !triggerSubTypes.isEmpty()
                && candidateTopic != null && !candidateTopic.trim().isEmpty();
    }

    public String getCandidateTopic() {
        return candidateTopic;
    }

    public void setCandidateTopic(String candidateTopic) {
        if (candidateTopic != null && !candidateTopic.trim().isEmpty()) this.candidateTopic = candidateTopic.trim();
    }

    public int getCandidateTopicPartitions() {
        return candidateTopicPartitions;
    }

    public void setCandidateTopicPartitions(int candidateTopicPartitions) {
        if (candidateTopicPartitions > 0) this.candidateTopicPartitions = candidateTopicPartitions;
    }

    public int getParamCacheSize() {
        return paramCacheSize;
    }

    public void setParamCacheSize(int paramCacheSize) {
        if (paramCacheSize > 0) this.paramCacheSize = paramCacheSize;
    }

    public int getParamCacheTtlSeconds() {
        return paramCacheTtlSeconds;
    }

    public void setParamCacheTtlSeconds(int paramCacheTtlSeconds) {
        if (paramCacheTtlSeconds > 0) this.paramCacheTtlSeconds = paramCacheTtlSeconds;
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
                + ", candidateTopic='" + candidateTopic + "'"
                + ", token=" + tokenMarker + " }";
    }
}
