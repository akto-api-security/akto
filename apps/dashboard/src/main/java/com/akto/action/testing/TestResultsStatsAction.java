package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.TestingRunExecutionSummaryUtils;
import lombok.Getter;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;

public class TestResultsStatsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestResultsStatsAction.class, LogDb.DASHBOARD);

    private String testingRunResultSummaryHexId;
    private String testingRunHexId;
    private String patternType; // required: one of [HTTP_429, HTTP_5XX, CLOUDFLARE]

    @Getter
    private int count = 0;

    /**
     * HTTP 403 Forbidden Pattern
     */
    public static final String REGEX_403 = TestingRunExecutionSummaryUtils.REGEX_403;

    /**
     * HTTP 401 Unauthorized Pattern
     */
    public static final String REGEX_401 = TestingRunExecutionSummaryUtils.REGEX_401;

    /**
     * HTTP 429 Rate Limiting Pattern
     */
    public static final String REGEX_429 = TestingRunExecutionSummaryUtils.REGEX_429;

    /**
     * HTTP 5xx Server Error Pattern
     */
    public static final String REGEX_5XX = TestingRunExecutionSummaryUtils.REGEX_5XX;

    public static final String REGEX_CLOUDFLARE = TestingRunExecutionSummaryUtils.REGEX_CLOUDFLARE;

    public String fetchTestResultsStatsCount() {
        try {
            ObjectId testingRunResultSummaryId;

            // Input validation
            if (this.testingRunResultSummaryHexId == null || this.testingRunResultSummaryHexId.trim().isEmpty()) {
                addActionError("Missing required parameter: testingRunResultSummaryHexId");
                return ERROR.toUpperCase();
            }

            if (this.patternType == null || this.patternType.trim().isEmpty()) {
                addActionError("Missing required parameter: patternType");
                return ERROR.toUpperCase();
            }

            try {
                testingRunResultSummaryId = new ObjectId(this.testingRunResultSummaryHexId);
            } catch (Exception e) {
                addActionError("Invalid test summary id: " + e.getMessage());
                return ERROR.toUpperCase();
            }

            // Resolve regex pattern based on pattern type
            String resolvedRegex = resolveRegexPattern();
            if (resolvedRegex == null) {
                addActionError("Invalid pattern type. Supported types: HTTP_403, HTTP_401, HTTP_429, HTTP_5XX, CLOUDFLARE");
                return ERROR.toUpperCase();
            }

            String description = describePattern(this.patternType.trim().toUpperCase());

            this.count = TestingRunExecutionSummaryUtils.getHttpErrorCountByPattern(
                    testingRunResultSummaryId, resolvedRegex);

            loggerMaker.debugAndAddToDb(
                    "Found " + count + " requests matching " + description + " for test summary: "
                            + testingRunResultSummaryHexId,
                    LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching test results stats: " + e.getMessage());
            addActionError("Error fetching test results stats");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    private String resolveRegexPattern() {
        return TestingRunExecutionSummaryUtils.resolveHttpRegexPattern(this.patternType);
    }

    private String describePattern(String patternType) {
        switch (patternType) {
            case "HTTP_403":
            case "403":
                return "403 Forbidden";
            case "HTTP_401":
            case "401":
                return "401 Unauthorized";
            case "HTTP_429":
            case "429":
            case "RATE_LIMIT":
                return "429 Rate Limiting";
            case "HTTP_5XX":
            case "5XX":
            case "SERVER_ERROR":
                return "5xx Server Errors (includes Cloudflare 520-530)";
            case "CLOUDFLARE":
            case "CDN":
            case "CF":
                return "Cloudflare Blocking/Errors (1xxx, 10xxx, WAF, security blocks)";
            default:
                return "unknown pattern";
        }
    }

    // Standard getters and setters for Struts2 action parameter binding
    public String getTestingRunResultSummaryHexId() {
        return testingRunResultSummaryHexId;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }
}