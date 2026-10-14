package com.akto.dto;

// One appended row per account per AgenticPostureScoreCron run; the latest row is the current score.
public class AgenticPostureScoreHistory {

    public static final String VALUE = "value";
    private double value;

    public static final String AGENTS_SCORED = "agentsScored";
    private int agentsScored;

    public static final String AGENTS_WITH_NO_SIGNAL = "agentsWithNoSignal";
    private int agentsWithNoSignal;

    public static final String COMPUTED_AT = "computedAt";
    private int computedAt;

    public AgenticPostureScoreHistory() {
    }

    public AgenticPostureScoreHistory(double value, int agentsScored, int agentsWithNoSignal, int computedAt) {
        this.value = value;
        this.agentsScored = agentsScored;
        this.agentsWithNoSignal = agentsWithNoSignal;
        this.computedAt = computedAt;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public int getAgentsScored() {
        return agentsScored;
    }

    public void setAgentsScored(int agentsScored) {
        this.agentsScored = agentsScored;
    }

    public int getAgentsWithNoSignal() {
        return agentsWithNoSignal;
    }

    public void setAgentsWithNoSignal(int agentsWithNoSignal) {
        this.agentsWithNoSignal = agentsWithNoSignal;
    }

    public int getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(int computedAt) {
        this.computedAt = computedAt;
    }
}
