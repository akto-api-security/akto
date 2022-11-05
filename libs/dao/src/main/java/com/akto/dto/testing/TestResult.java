package com.akto.dto.testing;

import java.util.List;

public class TestResult extends GenericTestResult {

    private String message;
    private List<TestError> errors;

    private String originalMessage;
    private double percentageMatch;

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public enum TestCategory {
        BOLA ("BOLA",Severity.HIGH),
        ADD_USER_ID("ADD_USER_ID",Severity.HIGH),
        PRIVILEGE_ESCALATION("PRIVILEGE_ESCALATION",Severity.HIGH),
        NO_AUTH("NO_AUTH",Severity.HIGH);
        private final String name;
        private final Severity severity;

        private static final TestCategory[] values = values();
        TestCategory(String name, Severity severity) {
            this.name = name;
            this.severity = severity;
        }

        public static TestCategory getTestCategory (String category) {
            for (TestCategory testCategory : values) {
                if (testCategory.name.equals(category)) {
                    return testCategory;
                }
            }
            throw new IllegalStateException("Unknown TestCategory passed :- " + category);
        }

        public String getName() {
            return name;
        }

        public Severity getSeverity () {
            return severity;
        }
    }

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO
    }

    public enum TestError {
        NO_PATH("No sample data found for the API"),
        NO_MESSAGE_WITH_AUTH_TOKEN("No sample data found for the API which contains the auth token"),
        NO_AUTH_MECHANISM("No authentication mechanism saved"),
        API_REQUEST_FAILED("API request failed"),
        SOMETHING_WENT_WRONG("OOPS! Something went wrong"),
        FAILED_TO_CONVERT_TEST_REQUEST_TO_STRING("Failed to store test"),
        NO_AUTH_TOKEN_FOUND("No authentication token found");

        private final String message;

        TestError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public TestResult(String message, String originalMessage, List<TestError> errors, double percentageMatch, boolean isVulnerable, Confidence confidence) {
        super(isVulnerable, confidence);
        this.message = message;
        this.errors = errors;
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

    public List<TestError> getErrors() {
        return errors;
    }

    public void setErrors(List<TestError> errors) {
        this.errors = errors;
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
}
