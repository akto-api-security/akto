package com.akto.dto.testing;

import com.akto.dto.testing.info.TestInfo;

import java.util.List;

public class TestResult extends GenericTestResult {

    private String message;
    private List<TestError> errors;

    private String originalMessage;
    private double percentageMatch;
    private TestInfo testInfo;

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public enum TestError {
        NO_PATH("No sample data found for the API"),
        NO_MESSAGE_WITH_AUTH_TOKEN("No sample data found for the API which contains the auth token"),
        NO_AUTH_MECHANISM("No authentication mechanism saved"),
        API_REQUEST_FAILED("API request failed"),
        SOMETHING_WENT_WRONG("OOPS! Something went wrong"),
        FAILED_TO_CONVERT_TEST_REQUEST_TO_STRING("Failed to store test"),
        INSUFFICIENT_MESSAGES("Insufficient messages"),
        NO_AUTH_TOKEN_FOUND("No authentication token found"),
        FAILED_BUILDING_NUCLEI_TEMPLATE("Failed building nuclei template");

        private final String message;

        TestError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public TestResult(String message, String originalMessage, List<TestError> errors, double percentageMatch, boolean isVulnerable,
                      Confidence confidence, TestInfo testInfo) {
        super(isVulnerable, confidence);
        this.message = message;
        this.errors = errors;
        this.originalMessage = originalMessage;
        this.percentageMatch = percentageMatch;
        this.testInfo = testInfo;
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

    public TestInfo getTestInfo() {
        return testInfo;
    }

    public void setTestInfo(TestInfo testInfo) {
        this.testInfo = testInfo;
    }
}
