package com.akto.dto.testing;


import com.akto.dto.type.SingleTypeInfo;

import java.util.ArrayList;
import java.util.List;

public class TestResult {

    private String message;
    private boolean vulnerable;
    private List<TestError> errors;

    private String originalMessage;
    private double percentageMatch;

    private List<SingleTypeInfo> privateSingleTypeInfos = new ArrayList<>();

    public enum TestError {
        NO_PATH, NO_HAPPY_PATH, NO_AUTH_MECHANISM, API_REQUEST_FAILED, SOMETHING_WENT_WRONG, MISSING_REQUEST_BODY,
        FAILED_BUILDING_REQUEST_BODY
    }

    public TestResult(String message, String originalMessage, boolean vulnerable,
                      List<TestError> errors, List<SingleTypeInfo> privateSingleTypeInfos, double percentageMatch) {
        this.message = message;
        this.vulnerable = vulnerable;
        this.errors = errors;
        this.privateSingleTypeInfos = privateSingleTypeInfos;
        this.originalMessage = originalMessage;
        this.percentageMatch = percentageMatch;
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
        this.percentageMatch = percentageMatch;
    }
}
