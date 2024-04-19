package com.akto.dto.testing;

import com.akto.dto.testing.info.TestInfo;

import java.util.ArrayList;
import java.util.List;

public class TestResult extends GenericTestResult {

    private String message;
    private List<String> errors;
    public static final String ERRORS = "errors";
    private String originalMessage;
    private double percentageMatch;
    private TestInfo testInfo;

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    public enum TestError {
        NO_PATH("No sample data found for the API", true),
        NO_MESSAGE_WITH_AUTH_TOKEN("No sample data found for the API which contains the auth token", true),
        NO_AUTH_MECHANISM("No authentication mechanism saved", false),
        API_REQUEST_FAILED("API request failed", false),
        SOMETHING_WENT_WRONG("OOPS! Something went wrong", false),
        FAILED_TO_CONVERT_TEST_REQUEST_TO_STRING("Failed to store test", false),
        INSUFFICIENT_MESSAGES("Insufficient messages", false),
        NO_AUTH_TOKEN_FOUND("No authentication token found", false),
        FAILED_DOWNLOADING_PAYLOAD_FILES("Failed downloading payload files", false),
        FAILED_BUILDING_URL_WITH_DOMAIN("Failed building URL with domain", false),
        EXECUTION_FAILED("Test execution failed", false),
        INVALID_EXECUTION_BLOCK("Invalid test execution block in template", true),
        NO_API_REQUEST("No test requests created", false),
        SKIPPING_EXECUTION_BECAUSE_AUTH("Request API failed authentication check, skipping execution", true),
        SKIPPING_EXECUTION_BECAUSE_FILTERS("Request API failed to satisfy api_selection_filters block, skipping execution", true);
        private final String message;
        private final boolean skipTest;

        TestError(String message, boolean skipTest) {
            this.message = message;
            this.skipTest = skipTest;
        }

        public String getMessage() {
            return message;
        }
        public boolean getSkipTest() {
            return skipTest;
        }

        public static List<String> getErrorsToSkipTests() {
            List<String> ret = new ArrayList<>();
            for(TestError te: TestError.values()) {
                if (te.getSkipTest()) {
                    ret.add(te.getMessage());
                }
            }
            return ret;
        }
    }

    public TestResult(String message, String originalMessage, List<String> errors, double percentageMatch, boolean isVulnerable,
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

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
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
