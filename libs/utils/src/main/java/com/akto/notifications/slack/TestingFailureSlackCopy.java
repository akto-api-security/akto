package com.akto.notifications.slack;

import com.akto.dto.testing.TestingRun;

import java.util.Map;

/**
 * User-facing title + body for testing-related Slack alerts (database-abstractor / mini-testing paths).
 */
public final class TestingFailureSlackCopy {

    /** Stored in summary {@code metadata.error} when mini-testing auth prefetch fails. */
    public static final String AUTH_PREFETCH_FAILURE_MESSAGE =
            "Failed to fetch auth token after three retries";

    private static final String DASHBOARD_BASE_URL = "https://app.akto.io";

    private TestingFailureSlackCopy() {}

    public static final class TitleAndDetail {
        private final String title;
        private final String detail;

        public TitleAndDetail(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }

        public String title() {
            return title;
        }

        public String detail() {
            return detail;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String slackLink(String path, String label) {
        return "<" + DASHBOARD_BASE_URL + path + "|" + label + ">";
    }

    private static String formatTestRunLine(String testingRunHexId) {
        if (isBlank(testingRunHexId)) {
            return "Test run: Not available";
        }
        String id = testingRunHexId.trim();
        return "Test run: " + slackLink("/dashboard/testing/" + id, id);
    }

    private static String formatTestRunSummaryLine(String testingRunHexId, String testRunSummaryHexId) {
        if (isBlank(testRunSummaryHexId)) {
            return "Test run summary: Not available";
        }
        String summaryId = testRunSummaryHexId.trim();
        if (isBlank(testingRunHexId)) {
            return "Test run summary: " + summaryId;
        }
        String runId = testingRunHexId.trim();
        return "Test run summary: " + slackLink("/dashboard/testing/" + runId + "/result/" + summaryId, summaryId);
    }

    private static String withTestRunReferences(String detailBody, String testingRunHexId, String testRunSummaryHexId) {
        return detailBody + "\n\n" + formatTestRunLine(testingRunHexId) + "\n" + formatTestRunSummaryLine(testingRunHexId, testRunSummaryHexId);
    }

    /**
     * {@code markTestRunResultSummaryFailed}: had stored results (stalled) vs none yet (no executions).
     */
    public static TitleAndDetail forMarkTestRunSummaryFailed(
            boolean hadPriorResults, String testingRunHexId, String testRunSummaryHexId) {
        if (hadPriorResults) {
            return new TitleAndDetail(
                    "This API test round stalled",
                    withTestRunReferences(
                            "The last recorded activity was long enough ago that Akto treated this round as stuck, marked it failed, and will schedule a new round. Check your runner, connectivity, and selected APIs in Akto if this keeps happening.",
                            testingRunHexId,
                            testRunSummaryHexId));
        }
        return new TitleAndDetail(
                "No API test activity recorded",
                withTestRunReferences(
                        "Akto did not record any API test executions for this attempt. Make sure APIs are selected, sample traffic exists, and your test runner service is running—then try again from Akto.",
                        testingRunHexId,
                        testRunSummaryHexId));
    }

    /** {@code updateTestRunResultSummary} after usage / quota overage. */
    public static TitleAndDetail forUsageLimitStoppedTest(String testingRunHexId, String testRunSummaryHexId) {
        return new TitleAndDetail(
                "API security testing paused — quota full",
                withTestRunReferences(
                        "Akto stopped this attempt because your organization has used all API security test runs allowed for the current billing period. Open Billing or usage settings in Akto to raise the limit or wait until your quota resets, then start tests again.",
                        testingRunHexId,
                        testRunSummaryHexId));
    }

    /** {@code updateTestRunResultSummaryNoUpsert}: summary marked STOPPED because the run was stopped. */
    public static TitleAndDetail forStoppedTestRunSummary(String testingRunHexId, String testRunSummaryHexId) {
        return new TitleAndDetail(
                "API security tests stopped",
                withTestRunReferences(
                        "Akto stopped this scan because the test was marked as stopped (for example from the dashboard). No more results will be collected for this attempt. Start a new test in Akto when you want to run again.",
                        testingRunHexId,
                        testRunSummaryHexId));
    }

    /** {@code updateIssueCountInSummary} with zero test results after recalculating issue counts. */
    public static TitleAndDetail forIssueCountInSummaryNoResults(String testingRunHexId, String testRunSummaryHexId) {
        return new TitleAndDetail(
                "No results from this API test scan",
                withTestRunReferences(
                        "Akto did not find any completed API test output for this scan. Confirm the APIs you selected, the test configuration, and that the runner could reach your services, then try again.",
                        testingRunHexId,
                        testRunSummaryHexId));
    }

    public static boolean isAuthPrefetchFailure(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        String err = metadata.get("error");
        return AUTH_PREFETCH_FAILURE_MESSAGE.equals(err);
    }

    /** Auth token prefetch failed in mini-testing before any API test ran. */
    public static TitleAndDetail forAuthPrefetchFailure(String testingRunHexId, String testRunSummaryHexId) {
        return new TitleAndDetail(
                "API security tests could not start — login failed",
                withTestRunReferences(
                        "Akto could not obtain a login token for your test role after three attempts. Check the test role, login workflow, and credentials in Akto, then run the scan again.",
                        testingRunHexId,
                        testRunSummaryHexId));
    }

    /**
     * {@code updateIssueCountAndStateInSummary} with zero test results: auth prefetch (metadata),
     * {@code FAILED} (other pre-test failures), or other terminal states (e.g. {@code COMPLETED}).
     */
    public static TitleAndDetail forIssueCountAndStateNoResults(
            String stateString, String testingRunHexId, String testRunSummaryHexId) {
        return forIssueCountAndStateNoResults(stateString, testingRunHexId, testRunSummaryHexId, null);
    }

    public static TitleAndDetail forIssueCountAndStateNoResults(
            String stateString, String testingRunHexId, String testRunSummaryHexId, Map<String, String> metadata) {
        if (isAuthPrefetchFailure(metadata)) {
            return forAuthPrefetchFailure(testingRunHexId, testRunSummaryHexId);
        }
        if (stateString != null && TestingRun.State.FAILED.name().equalsIgnoreCase(stateString.trim())) {
            return new TitleAndDetail(
                    "API security tests could not start",
                    withTestRunReferences(
                            "Akto could not run this scan because something failed before any API tests were recorded. Check the test role, runner connectivity, and selected APIs in Akto, then run the scan again.",
                            testingRunHexId,
                            testRunSummaryHexId));
        }
        return new TitleAndDetail(
                "API security tests finished with no saved results",
                withTestRunReferences(
                        "This scan finished, but Akto has no stored API test results for it. That can happen if nothing ran, APIs were out of scope, or results were still being written. Confirm your targets and runner in Akto, then review logs or run again if you expected output.",
                        testingRunHexId,
                        testRunSummaryHexId));
    }
}
