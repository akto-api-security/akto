package com.akto.dto.api_protection_parse_layer;

public class Condition {

    private int matchCount;
    private int distinctCount;
    private int windowThreshold;
    private String incrementFilter;
    private String thresholdBreachFilter;

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
    public int getDistinctCount() {
        return distinctCount;
    }
    public void setDistinctCount(int distinctCount) {
        this.distinctCount = distinctCount;
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
