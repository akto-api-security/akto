package com.akto.testing;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.util.Constants;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.WorkflowTestingEndpoints;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestingRunExecutionSummaryUtils {

    public enum QueryMode {
        VULNERABLE,
        SECURED,
        SKIPPED_EXEC_NEED_CONFIG,
        SKIPPED_EXEC,
        ALL,
        SKIPPED_EXEC_API_REQUEST_FAILED,
        IGNORED_ISSUES
    }

    public static final String HTTP_403 = "HTTP_403";
    public static final String HTTP_401 = "HTTP_401";
    public static final String HTTP_429 = "HTTP_429";
    public static final String HTTP_5XX = "HTTP_5XX";
    public static final String CLOUDFLARE = "CLOUDFLARE";

    public static final String REGEX_403 = "\"statusCode\"\\s*:\\s*403";
    public static final String REGEX_401 = "\"statusCode\"\\s*:\\s*401";
    public static final String REGEX_429 = "\"statusCode\"\\s*:\\s*429";
    public static final String REGEX_5XX = "\"statusCode\"\\s*:\\s*5[0-9][0-9]";
    public static final String REGEX_CLOUDFLARE =
            "(?is).*\"response\".*\"body\".*(" +
            "attention\\s+required.*cloudflare|" +
            "<title>[^<]*cloudflare[^<]*</title>|" +
            "cf-error-details|" +
            "access\\s+denied.*cloudflare" +
            ")";

    private TestingRunExecutionSummaryUtils() {
    }

    public static Map<String, Integer> computeExecutionResultCounts(ObjectId testingRunResultSummaryId) {
        Map<String, Integer> counts = new HashMap<>();
        for (QueryMode queryMode : QueryMode.values()) {
            if (queryMode == QueryMode.SECURED) {
                continue;
            }
            int count = countForQueryMode(testingRunResultSummaryId, queryMode);
            counts.put(queryMode.name(), count);
        }
        counts.put(QueryMode.SECURED.name(), computeSecuredCount(counts));
        return counts;
    }

    public static int computeSecuredCount(Map<String, Integer> counts) {
        int all = counts.getOrDefault(QueryMode.ALL.name(), 0);
        int others = counts.getOrDefault(QueryMode.VULNERABLE.name(), 0)
                + counts.getOrDefault(QueryMode.SKIPPED_EXEC.name(), 0)
                + counts.getOrDefault(QueryMode.SKIPPED_EXEC_NEED_CONFIG.name(), 0)
                + counts.getOrDefault(QueryMode.SKIPPED_EXEC_API_REQUEST_FAILED.name(), 0)
                + counts.getOrDefault(QueryMode.IGNORED_ISSUES.name(), 0);
        return all >= others ? all - others : 0;
    }

    public static Map<String, Integer> computeHttpErrorCounts(ObjectId testingRunResultSummaryId) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put(HTTP_403, getHttpErrorCountByPattern(testingRunResultSummaryId, REGEX_403));
        counts.put(HTTP_401, getHttpErrorCountByPattern(testingRunResultSummaryId, REGEX_401));
        counts.put(HTTP_429, getHttpErrorCountByPattern(testingRunResultSummaryId, REGEX_429));
        counts.put(HTTP_5XX, getHttpErrorCountByPattern(testingRunResultSummaryId, REGEX_5XX));
        counts.put(CLOUDFLARE, getHttpErrorCountByPattern(testingRunResultSummaryId, REGEX_CLOUDFLARE));
        return counts;
    }

    public static TestingRun.State resolveDisplayState(TestingRun run, TestingRunResultSummary summary) {
        if (run == null) {
            return summary != null ? summary.getState() : null;
        }
        TestingRun.State runState = run.getState();
        if (summary == null) {
            return runState;
        }
        TestingRun.State summaryState = summary.getState();
        if (runState == TestingRun.State.COMPLETED && summaryState != null
                && summaryState != TestingRun.State.COMPLETED
                && summaryState != TestingRun.State.STOPPED) {
            return TestingRun.State.FAILED;
        }
        if (summaryState == TestingRun.State.STOPPED) {
            return TestingRun.State.STOPPED;
        }
        if (summaryState == TestingRun.State.FAILED) {
            return TestingRun.State.FAILED;
        }
        return runState;
    }

    public static void ensureAndPersistExecutionCounts(TestingRunResultSummary summary) {
        if (summary == null || summary.getId() == null) {
            return;
        }
        if (summary.getState() != TestingRun.State.COMPLETED
                && summary.getState() != TestingRun.State.FAILED
                && summary.getState() != TestingRun.State.STOPPED) {
            return;
        }

        boolean hasExecutionCounts = summary.getExecutionResultCounts() != null
                && !summary.getExecutionResultCounts().isEmpty();
        boolean hasHttpCounts = summary.getHttpErrorCounts() != null
                && !summary.getHttpErrorCounts().isEmpty();
        if (hasExecutionCounts && hasHttpCounts) {
            return;
        }

        Map<String, Integer> executionResultCounts = hasExecutionCounts
                ? summary.getExecutionResultCounts()
                : computeExecutionResultCounts(summary.getId());
        Map<String, Integer> httpErrorCounts = hasHttpCounts
                ? summary.getHttpErrorCounts()
                : computeHttpErrorCounts(summary.getId());

        summary.setExecutionResultCounts(executionResultCounts);
        summary.setHttpErrorCounts(httpErrorCounts);

        TestingRunResultSummariesDao.instance.updateOne(
                Filters.eq(Constants.ID, summary.getId()),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.EXECUTION_RESULT_COUNTS, executionResultCounts),
                        Updates.set(TestingRunResultSummary.HTTP_ERROR_COUNTS, httpErrorCounts)));
    }

    public static boolean isIncorrectTestRun(TestingRunResultSummary summary, TestingRun.State runState) {
        if (summary == null) {
            return false;
        }

        Map<String, String> metadata = summary.getMetadata();
        if (metadata != null && metadata.get("error") != null && !metadata.get("error").isEmpty()) {
            return true;
        }

        TestingRun.State effectiveState = runState != null ? runState : summary.getState();
        if (effectiveState == TestingRun.State.FAILED || effectiveState == TestingRun.State.STOPPED) {
            return false;
        }
        if (effectiveState == TestingRun.State.RUNNING || effectiveState == TestingRun.State.SCHEDULED) {
            return false;
        }

        Map<String, Integer> executionCounts = summary.getExecutionResultCounts();
        Map<String, Integer> httpCounts = summary.getHttpErrorCounts();
        if (executionCounts == null || executionCounts.isEmpty()) {
            return false;
        }
        if (httpCounts == null) {
            httpCounts = new HashMap<>();
        }

        int all = executionCounts.getOrDefault(QueryMode.ALL.name(), 0);
        int secured = executionCounts.getOrDefault(QueryMode.SECURED.name(), 0);
        int vulnerable = executionCounts.getOrDefault(QueryMode.VULNERABLE.name(), 0);
        int http403 = httpCounts.getOrDefault(HTTP_403, 0);
        int http401 = httpCounts.getOrDefault(HTTP_401, 0);

        if (all > 0 && secured + vulnerable == 0) {
            return true;
        }
        if (secured > 0 && (http403 + http401) >= secured) {
            return true;
        }
        return false;
    }

    public static String resolveHttpRegexPattern(String patternType) {
        if (patternType == null) {
            return null;
        }
        switch (patternType.trim().toUpperCase()) {
            case "HTTP_403":
            case "403":
                return REGEX_403;
            case "HTTP_401":
            case "401":
                return REGEX_401;
            case "HTTP_429":
            case "429":
            case "RATE_LIMIT":
                return REGEX_429;
            case "HTTP_5XX":
            case "5XX":
            case "SERVER_ERROR":
                return REGEX_5XX;
            case "CLOUDFLARE":
            case "CDN":
            case "CF":
                return REGEX_CLOUDFLARE;
            default:
                return null;
        }
    }

    public static int getHttpErrorCountByPattern(ObjectId testingRunResultSummaryId, String regex) {
        return countByPattern(testingRunResultSummaryId, regex)
                + countByPatternMultiExecResults(testingRunResultSummaryId, regex);
    }

    private static int countForQueryMode(ObjectId testingRunResultSummaryId, QueryMode queryMode) {
        List<Bson> filterList = prepareTestRunResultsFilters(testingRunResultSummaryId, queryMode);
        if (queryMode == QueryMode.IGNORED_ISSUES) {
            return (int) TestingRunIssuesDao.instance.count(prepareIgnoredFilterListFromResults(filterList));
        }
        return VulnerableTestingRunResultDao.instance.countFromDb(
                Filters.and(filterList), queryMode == QueryMode.VULNERABLE);
    }

    private static List<Bson> prepareTestRunResultsFilters(ObjectId testingRunResultSummaryId, QueryMode queryMode) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId));

        switch (queryMode) {
            case VULNERABLE:
                filterList.add(Filters.eq(TestingRunResult.VULNERABLE, true));
                break;
            case SKIPPED_EXEC_API_REQUEST_FAILED:
                filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
                filterList.add(Filters.in(
                        TestingRunResultDao.ERRORS_KEY,
                        TestResult.API_CALL_FAILED_ERROR_STRING,
                        TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE));
                break;
            case SKIPPED_EXEC:
                filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
                filterList.add(Filters.in(TestingRunResultDao.ERRORS_KEY, TestError.getErrorsToSkipTests()));
                break;
            case SECURED:
                filterList.add(Filters.eq(TestingRunResult.VULNERABLE, false));
                List<String> errorsToSkipTest = new ArrayList<>(TestError.getErrorsToSkipTests());
                errorsToSkipTest.add(TestResult.API_CALL_FAILED_ERROR_STRING);
                errorsToSkipTest.add(TestResult.API_CALL_FAILED_ERROR_STRING_UNREACHABLE);
                filterList.add(
                        Filters.or(
                                Filters.exists(WorkflowTestingEndpoints._WORK_FLOW_TEST),
                                Filters.and(
                                        Filters.nin(TestingRunResultDao.ERRORS_KEY, errorsToSkipTest),
                                        Filters.ne(TestingRunResult.REQUIRES_CONFIG, true))));
                break;
            case SKIPPED_EXEC_NEED_CONFIG:
                filterList.add(Filters.eq(TestingRunResult.REQUIRES_CONFIG, true));
                break;
            default:
                break;
        }

        return filterList;
    }

    private static Bson prepareIgnoredFilterListFromResults(List<Bson> filterList) {
        List<TestingRunResult> testingRunResults = VulnerableTestingRunResultDao.instance.findAll(
                Filters.and(filterList),
                Projections.include(TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE));
        List<TestingIssuesId> issueIdsList = new ArrayList<>();
        for (TestingRunResult testingRunResult : testingRunResults) {
            TestingIssuesId issueId = new TestingIssuesId(
                    testingRunResult.getApiInfoKey(),
                    TestErrorSource.AUTOMATED_TESTING,
                    testingRunResult.getTestSubType());
            if (!issueIdsList.contains(issueId)) {
                issueIdsList.add(issueId);
            }
        }
        return Filters.and(
                Filters.in(Constants.ID, issueIdsList),
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS,
                        Arrays.asList(TestRunIssueStatus.IGNORED, TestRunIssueStatus.FIXED)));
    }

    private static int countByPattern(ObjectId testingRunResultSummaryId, String regex) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(
                Filters.and(
                        Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                        Filters.eq("vulnerable", false),
                        Filters.exists("testResults.message", true))));
        pipeline.add(Aggregates.sort(Sorts.descending("endTimestamp")));
        pipeline.add(Aggregates.limit(10000));
        pipeline.add(Aggregates.project(
                Projections.computed("lastMessage",
                        new BasicDBObject("$arrayElemAt",
                                Arrays.asList("$testResults.message", -1)))));
        pipeline.add(Aggregates.match(Filters.regex("lastMessage", regex, "i")));
        pipeline.add(Aggregates.count("count"));

        MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();
        int resultCount = 0;
        if (cursor.hasNext()) {
            BasicDBObject result = cursor.next();
            resultCount = result.getInt("count", 0);
        }
        cursor.close();
        return resultCount;
    }

    private static int countByPatternMultiExecResults(ObjectId testingRunResultSummaryId, String regex) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                Filters.eq("vulnerable", false),
                Filters.exists("testResults.nodeResultMap.x1.message", true))));
        pipeline.add(Aggregates.sort(Sorts.descending("endTimestamp")));
        pipeline.add(Aggregates.limit(10000));
        pipeline.add(Aggregates.match(Filters.regex("testResults.nodeResultMap.x1.message", regex, "i")));
        pipeline.add(Aggregates.count("count"));

        MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();
        int resultCount = 0;
        if (cursor.hasNext()) {
            BasicDBObject result = cursor.next();
            resultCount = result.getInt("count", 0);
        }
        cursor.close();
        return resultCount;
    }
}
