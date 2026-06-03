package com.akto.dto.testing;

public class AiSummaryEntry {

    public static final String PHASE = "phase";
    public static final String ATTEMPT = "attempt";
    public static final String CONTENT = "content";

    private String phase;
    private Integer attempt;
    private String content;

    public AiSummaryEntry() {
    }

    public AiSummaryEntry(String phase, Integer attempt, String content) {
        this.phase = phase;
        this.attempt = attempt;
        this.content = content;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
