package com.akto.dto.testing;


import com.akto.dto.type.SingleTypeInfo;

import java.util.ArrayList;
import java.util.List;

public class TestResult {

    private String message;
    private boolean vulnerable;
    private List<TestError> errors;
    private Confidence confidence = Confidence.HIGH;

    private String originalMessage;
    private double percentageMatch;

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    private List<SingleTypeInfo> privateSingleTypeInfos = new ArrayList<>();

    public enum TestError {
        NO_PATH("No sample data found for the API"),
        NO_AUTH_MECHANISM("No authentication mechanism saved"),
        API_REQUEST_FAILED("API request failed"),
        SOMETHING_WENT_WRONG("OOPS! Something went wrong"),
        NO_AUTH_TOKEN_FOUND("No authentication token found");

        private String message;

        TestError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

    }

    public TestResult(String message, String originalMessage, boolean vulnerable,
                      List<TestError> errors, List<SingleTypeInfo> privateSingleTypeInfos, double percentageMatch,
                      Confidence confidence) {
        this.message = message;
        this.vulnerable = vulnerable;
        this.errors = errors;
        this.privateSingleTypeInfos = privateSingleTypeInfos;
        this.originalMessage = originalMessage;
        this.percentageMatch = percentageMatch;
        this.confidence = confidence;
    }

    public TestResult() {
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isVulnerable() {
        return vulnerable;
    }

    public void setVulnerable(boolean vulnerable) {
        this.vulnerable = vulnerable;
    }

    public List<TestError> getErrors() {
        return errors;
    }

    public void setErrors(List<TestError> errors) {
        this.errors = errors;
    }

    public List<SingleTypeInfo> getPrivateSingleTypeInfos() {
        return privateSingleTypeInfos;
    }

    public void setPrivateSingleTypeInfos(List<SingleTypeInfo> privateSingleTypeInfos) {
        if (privateSingleTypeInfos == null) privateSingleTypeInfos = new ArrayList<>();
        this.privateSingleTypeInfos = privateSingleTypeInfos;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public double getPercentageMatch() {
        return percentageMatch;
    }

    public void setPercentageMatch(double percentageMatch) {
        this.percentageMatch = !Double.isFinite(percentageMatch) ? 0 :  percentageMatch;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public void setConfidence(Confidence confidence) {
        this.confidence = confidence;
    }
}
