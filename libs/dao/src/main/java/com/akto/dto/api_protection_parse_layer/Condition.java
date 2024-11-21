package com.akto.dto.api_protection_parse_layer;

public class Condition {

    private int matchCount;
    private int windowThreshold;
    // private String operation;
    // private float value;
    
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
    // private List<Action> actions;
}
