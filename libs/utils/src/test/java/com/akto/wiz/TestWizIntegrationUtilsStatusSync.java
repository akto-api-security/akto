package com.akto.wiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.URLMethods;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.wiz.WizIntegrationUtils.StatusChangeResult;

/**
 * Tests the core logic of syncing issue status from Wiz: given Akto issues and the status of each Wiz finding
 * (keyed by Wiz finding id), which Akto issues should change status, and to what.
 */
public class TestWizIntegrationUtilsStatusSync {

    private static TestingIssuesId issueId(String testSubCategory) {
        ApiInfoKey apiInfoKey = new ApiInfoKey(1776945214, "https://juiceshop.akto.io/rest/" + testSubCategory, URLMethods.Method.GET);
        return new TestingIssuesId(apiInfoKey, TestErrorSource.AUTOMATED_TESTING, testSubCategory);
    }

    private static TestingRunIssues issue(String testSubCategory, TestRunIssueStatus status) {
        TestingRunIssues issue = new TestingRunIssues();
        issue.setId(issueId(testSubCategory));
        issue.setTestRunIssueStatus(status);
        return issue;
    }

    private static String wizId(String testSubCategory) throws Exception {
        return WizIntegrationUtils.fetchWizFindingId(issueId(testSubCategory));
    }

    // ---- getAktoIssueStatusFromWizStatus ----

    // Wiz OPEN/RESOLVED/REJECTED map to Akto OPEN/FIXED/IGNORED.
    @Test
    public void testWizStatusMapping() {
        assertEquals(TestRunIssueStatus.OPEN, WizIntegrationUtils.getAktoIssueStatusFromWizStatus("OPEN"));
        assertEquals(TestRunIssueStatus.FIXED, WizIntegrationUtils.getAktoIssueStatusFromWizStatus("RESOLVED"));
        assertEquals(TestRunIssueStatus.IGNORED, WizIntegrationUtils.getAktoIssueStatusFromWizStatus("REJECTED"));
    }

    // A null Wiz status maps to null; an unrecognised one currently defaults to OPEN.
    @Test
    public void testWizStatusMappingNullAndUnknown() {
        assertNull(WizIntegrationUtils.getAktoIssueStatusFromWizStatus(null));
        // unknown statuses currently default to OPEN
        assertEquals(TestRunIssueStatus.OPEN, WizIntegrationUtils.getAktoIssueStatusFromWizStatus("SOMETHING_NEW"));
    }

    // ---- computeStatusChanges ----

    // When the Wiz status maps to a different Akto status, the issue is returned with the new status.
    @Test
    public void testStatusDiffersProducesChange() throws Exception {
        List<TestingRunIssues> issues = Collections.singletonList(issue("JWT_NONE_ALGO", TestRunIssueStatus.OPEN));
        Map<String, String> wizStatuses = new HashMap<>();
        wizStatuses.put(wizId("JWT_NONE_ALGO"), "RESOLVED");

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(issues, wizStatuses);

        assertEquals(0, result.failedIssues);
        assertEquals(1, result.statusChanges.size());
        assertEquals(TestRunIssueStatus.FIXED, result.statusChanges.get(issueId("JWT_NONE_ALGO")));
    }

    // When the Wiz status already matches the Akto status, nothing changes.
    @Test
    public void testSameStatusProducesNoChange() throws Exception {
        List<TestingRunIssues> issues = Collections.singletonList(issue("NO_AUTH", TestRunIssueStatus.OPEN));
        Map<String, String> wizStatuses = new HashMap<>();
        wizStatuses.put(wizId("NO_AUTH"), "OPEN");

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(issues, wizStatuses);

        assertEquals(0, result.failedIssues);
        assertTrue(result.statusChanges.isEmpty());
    }

    // FIXED/RESOLVED and IGNORED/REJECTED are the same state, so they must not be reported as changes.
    @Test
    public void testEquivalentStatusesProduceNoChange() throws Exception {
        // Akto FIXED == Wiz RESOLVED, Akto IGNORED == Wiz REJECTED: must be compared after mapping, not as raw strings
        List<TestingRunIssues> issues = Arrays.asList(
            issue("REMOVE_TOKENS", TestRunIssueStatus.FIXED),
            issue("BFLA", TestRunIssueStatus.IGNORED));
        Map<String, String> wizStatuses = new HashMap<>();
        wizStatuses.put(wizId("REMOVE_TOKENS"), "RESOLVED");
        wizStatuses.put(wizId("BFLA"), "REJECTED");

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(issues, wizStatuses);

        assertEquals(0, result.failedIssues);
        assertTrue(result.statusChanges.isEmpty());
    }

    // An Akto issue with no matching Wiz finding (e.g. not created in Wiz yet) is skipped, not failed.
    @Test
    public void testIssueNotInWizIsSkipped() {
        List<TestingRunIssues> issues = Collections.singletonList(issue("BOLA", TestRunIssueStatus.OPEN));

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(issues, new HashMap<>());

        assertEquals(0, result.failedIssues);
        assertTrue(result.statusChanges.isEmpty());
    }

    // With a mix of changed, unchanged and missing issues, only the changed ones are returned.
    @Test
    public void testMixedIssuesOnlyChangedOnesAreReturned() throws Exception {
        List<TestingRunIssues> issues = Arrays.asList(
            issue("PARAMETER_POLLUTION", TestRunIssueStatus.OPEN),   // OPEN -> OPEN: no change
            issue("JWT_NONE_ALGO", TestRunIssueStatus.OPEN),         // OPEN -> RESOLVED: FIXED
            issue("BFLA", TestRunIssueStatus.IGNORED),               // IGNORED -> OPEN: OPEN
            issue("REMOVE_TOKENS", TestRunIssueStatus.FIXED),        // FIXED -> REJECTED: IGNORED
            issue("BOLA", TestRunIssueStatus.OPEN));                 // not in wiz: skipped
        Map<String, String> wizStatuses = new HashMap<>();
        wizStatuses.put(wizId("PARAMETER_POLLUTION"), "OPEN");
        wizStatuses.put(wizId("JWT_NONE_ALGO"), "RESOLVED");
        wizStatuses.put(wizId("BFLA"), "OPEN");
        wizStatuses.put(wizId("REMOVE_TOKENS"), "REJECTED");

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(issues, wizStatuses);

        assertEquals(0, result.failedIssues);
        assertEquals(3, result.statusChanges.size());
        assertEquals(TestRunIssueStatus.FIXED, result.statusChanges.get(issueId("JWT_NONE_ALGO")));
        assertEquals(TestRunIssueStatus.OPEN, result.statusChanges.get(issueId("BFLA")));
        assertEquals(TestRunIssueStatus.IGNORED, result.statusChanges.get(issueId("REMOVE_TOKENS")));
    }

    // One bad issue (null status) is counted as failed and does not stop the others from being processed.
    @Test
    public void testIssueWithNullStatusIsCountedAsFailedAndOthersStillProcessed() throws Exception {
        List<TestingRunIssues> issues = Arrays.asList(
            issue("NO_AUTH", null),                                  // null status: fails to process
            issue("JWT_NONE_ALGO", TestRunIssueStatus.OPEN));
        Map<String, String> wizStatuses = new HashMap<>();
        wizStatuses.put(wizId("NO_AUTH"), "RESOLVED");
        wizStatuses.put(wizId("JWT_NONE_ALGO"), "RESOLVED");

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(issues, wizStatuses);

        assertEquals(1, result.failedIssues);
        assertEquals(1, result.statusChanges.size());
        assertEquals(TestRunIssueStatus.FIXED, result.statusChanges.get(issueId("JWT_NONE_ALGO")));
    }

    // An issue with a null id has no Wiz match, so it is skipped without counting as a failure.
    @Test
    public void testIssueWithNullIdIsSkippedWithoutFailing() {
        TestingRunIssues noId = new TestingRunIssues();
        noId.setTestRunIssueStatus(TestRunIssueStatus.OPEN);

        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(Collections.singletonList(noId), new HashMap<>());

        assertEquals(0, result.failedIssues);
        assertTrue(result.statusChanges.isEmpty());
    }

    // No issues and no Wiz findings gives an empty result.
    @Test
    public void testEmptyInputs() {
        StatusChangeResult result = WizIntegrationUtils.computeStatusChanges(Collections.emptyList(), new HashMap<>());

        assertEquals(0, result.failedIssues);
        assertTrue(result.statusChanges.isEmpty());
    }
}
