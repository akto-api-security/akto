package com.akto.dto.testing;

import java.util.List;

import com.akto.dto.testing.TestResult.Confidence;

public abstract class GenericTestResult {

    private boolean vulnerable;
    public static final String _CONFIDENCE = "confidence";
    private Confidence confidence = Confidence.HIGH;

    public GenericTestResult() {
    }

    public GenericTestResult(boolean vulnerable, Confidence confidence) {
        this.vulnerable = vulnerable;
        this.confidence = confidence;
    }

    public boolean isVulnerable() {
        return this.vulnerable;
    }

    public boolean getVulnerable() {
        return this.vulnerable;
    }

    public void setVulnerable(boolean vulnerable) {
        this.vulnerable = vulnerable;
    }

    public Confidence getConfidence() {
        return this.confidence;
    }

    public void setConfidence(Confidence confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "{" +
            " vulnerable='" + isVulnerable() + "'" +
            ", confidence='" + getConfidence() + "'" +
            "}";
    }

    public abstract List<String> getResponses();    
}
