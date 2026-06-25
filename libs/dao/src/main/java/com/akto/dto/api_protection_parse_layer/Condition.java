package com.akto.dto.api_protection_parse_layer;


public class Condition {

    private int matchCount;
    private int windowThreshold;
    private String incrementFilter;
    private String thresholdBreachFilter;
    private DistinctIdentifier distinctIdentifier;

    public static class DistinctIdentifier {
        private int count;
        private String source; // "request_payload", "response_payload", "request_headers"
        private String key;

        public DistinctIdentifier() {}

        public DistinctIdentifier(int count, String source, String key) {
            this.count = count;
            this.source = source;
            this.key = key;
        }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    public Condition() {
    }

    public Condition(int matchCount, int windowThreshold) {
        this.matchCount = matchCount;
        this.windowThreshold = windowThreshold;
    }

    public int getMatchCount() {
        return matchCount;
    }
    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }
    public int getWindowThreshold() {
        return windowThreshold;
    }
    public void setWindowThreshold(int windowThreshold) {
        this.windowThreshold = windowThreshold;
    }
    public DistinctIdentifier getDistinctIdentifier() {
        return distinctIdentifier;
    }
    public void setDistinctIdentifier(DistinctIdentifier distinctIdentifier) {
        this.distinctIdentifier = distinctIdentifier;
    }
    public String getIncrementFilter() {
        return incrementFilter;
    }
    public void setIncrementFilter(String incrementFilter) {
        this.incrementFilter = incrementFilter;
    }
    public String getThresholdBreachFilter() {
        return thresholdBreachFilter;
    }
    public void setThresholdBreachFilter(String thresholdBreachFilter) {
        this.thresholdBreachFilter = thresholdBreachFilter;
    }
}
