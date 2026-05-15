package com.akto.notifications.slack;

import com.akto.dto.testing.TestingRun;

/**
 * User-facing title + body for testing-related Slack alerts (database-abstractor / mini-testing paths).
 */
public final class TestingFailureSlackCopy {

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

    private static String withTestRunReferences(String detailBody, String testingRunHexId, String testRunSummaryHexId) {
        String run = isBlank(testingRunHexId) ? "Not available" : testingRunHexId.trim();
        String sum = isBlank(testRunSummaryHexId) ? "Not available" : testRunSummaryHexId.trim();
        return detailBody + "\n\nTest run ID: " + run + "\nTest run summary ID: " + sum;
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

    /**
     * {@code updateIssueCountAndStateInSummary} with zero test results: {@code FAILED} (e.g. auth prefetch)
     * vs other terminal states (e.g. {@code COMPLETED}).
     */
    public static TitleAndDetail forIssueCountAndStateNoResults(
            String stateString, String testingRunHexId, String testRunSummaryHexId) {
        if (stateString != null && TestingRun.State.FAILED.name().equalsIgnoreCase(stateString.trim())) {
            return new TitleAndDetail(
                    "API security tests could not start",
                    withTestRunReferences(
                            "Akto could not run this scan because something failed before any API tests were recorded—for example the login token for your test role could not be retrieved after several attempts. Check the test role and credentials in Akto, then run the scan again.",
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
