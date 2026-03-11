package com.akto.action;

import com.akto.dao.TestingRunWebhookDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.ApiInfo;
import com.akto.dto.TestingRunWebhook;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.slack.SlackSender;
import com.akto.notifications.slack.TestingRunWebhookAlert;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;


@Getter
@Setter
public class ToolsAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ToolsAction.class, LogDb.DASHBOARD);

    private String uuid;
    private String result;

    public String ssrfHit() {
        try {
            // Validate input
            if (uuid == null || uuid.isEmpty()) {
                addActionError("UUID is required");
                return Action.ERROR.toUpperCase();
            }

            // Find UUID mapping in common database
            TestingRunWebhook tracking = TestingRunWebhookDao.instance.findByUuid(uuid);
            if (tracking == null) {
                loggerMaker.errorAndAddToDb("SSRF UUID mapping not found: " + uuid, LogDb.DASHBOARD);
                addActionError("UUID mapping not found: " + uuid);
                return Action.ERROR.toUpperCase();
            }

            // Mark URL as hit
            TestingRunWebhookDao.instance.markUrlHit(uuid);
            loggerMaker.infoAndAddToDb("Marked SSRF URL as hit for UUID: " + uuid, LogDb.DASHBOARD);

            // Set account context for database operations
            Context.accountId.set(tracking.getAccountId());

            // Parse API info from string
            ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.ApiInfoKey.fromString(tracking.getApiInfoKey());
            ObjectId testRunResultSummaryId = tracking.getTestRunResultSummaryId();
            String testSubType = tracking.getTestSubType();

            // Find test result using summaryId (indexes are on summaryId, not testRunId)
            Bson filter = Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testRunResultSummaryId),
                Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, apiInfoKey.getApiCollectionId()),
                Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.URL, apiInfoKey.getUrl()),
                Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.METHOD, apiInfoKey.getMethod().name()),
                Filters.eq(TestingRunResult.TEST_SUB_TYPE, testSubType)
            );
            TestingRunResult testResult = TestingRunResultDao.instance.findOne(filter);

            if (testResult == null) {
                // Also check vulnerable collection
                testResult = VulnerableTestingRunResultDao.instance.findOne(filter);
            }

            if (testResult == null) {
                loggerMaker.errorAndAddToDb("Test result not found for UUID: " + uuid + ", summaryId: " + testRunResultSummaryId + ", apiInfoKey: " + apiInfoKey + ", testSubType: " + testSubType, LogDb.DASHBOARD);
                // Still return success since URL was hit and marked in webhook tracking
                result = "SUCCESS";
                return Action.SUCCESS.toUpperCase();
            }

            // Check if this is a NEW vulnerability (wasn't vulnerable before)
            boolean wasAlreadyVulnerable = testResult.isVulnerable();

            // Update in both collections
            Bson updateFilter = Filters.eq("_id", testResult.getId());
            Bson updates = Updates.set(TestingRunResult.VULNERABLE, true);

            // Update in testing_run_results collection (no upsert needed - document already exists)
            TestingRunResultDao.instance.getMCollection().updateOne(updateFilter, updates);

            // Insert into vulnerable_testing_run_results collection if it's a new vulnerability
            if (!wasAlreadyVulnerable) {
                testResult.setVulnerable(true); // Set the field in the object before inserting
                VulnerableTestingRunResultDao.instance.insertOne(testResult);
            }

            loggerMaker.infoAndAddToDb("Marked test as vulnerable for UUID: " + uuid + ", summaryId: " + testRunResultSummaryId + ", testSubType: " + testSubType, LogDb.DASHBOARD);

            // Create or update issue ONLY if this is a NEW vulnerability
            if (!wasAlreadyVulnerable) {
                try {
                    createOrUpdateIssue(testResult, tracking);
                    loggerMaker.infoAndAddToDb("Created/updated issue for new vulnerability: UUID " + uuid, LogDb.DASHBOARD);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error creating/updating issue: " + e.getMessage(), LogDb.DASHBOARD);
                    // Don't fail the request if issue creation fails
                }
            }

            // Send Slack alert for SSRF
            try {
                ObjectId testRunId = tracking.getTestRunId();
                TestingRun testingRun = DbLayer.findTestingRun(testRunId.toHexString());
                if (testingRun != null && testingRun.getSendSlackAlert()) {
                    String summaryId = testResult.getTestRunResultSummaryId() != null 
                        ? testResult.getTestRunResultSummaryId().toHexString() 
                        : null;
                    TestingRunWebhookAlert alert = new TestingRunWebhookAlert(
                        testSubType,
                        apiInfoKey,
                        testRunId.toHexString(),
                        summaryId,
                        tracking.getAccountId()
                    );
                    SlackSender.sendAlert(tracking.getAccountId(), alert, testingRun.getSelectedSlackChannelId());
                    loggerMaker.infoAndAddToDb("Sent Slack alert for SSRF vulnerability: UUID " + uuid, LogDb.DASHBOARD);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error sending Slack alert for SSRF vulnerability: " + e.getMessage(), LogDb.DASHBOARD);
                // Don't fail the request if Slack alert fails
            }

            result = "SUCCESS";
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error processing SSRF hit for UUID: " + uuid + ", error: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Internal server error: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Creates or updates a testing issue when a vulnerability is detected via webhook.
     * Issues are tracked by (apiInfoKey, testSubType, testErrorSource) combination.
     */
    private void createOrUpdateIssue(TestingRunResult testResult, TestingRunWebhook tracking) {
        int now = Context.now();
        
        // Create issue ID from test result
        TestingIssuesId issueId = new TestingIssuesId(
            testResult.getApiInfoKey(),
            TestErrorSource.AUTOMATED_TESTING,
            testResult.getTestSubType()
        );
        
        // Build filter for composite ID
        Bson issueFilter = Filters.and(
            Filters.eq("_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, testResult.getApiInfoKey().getApiCollectionId()),
            Filters.eq("_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.URL, testResult.getApiInfoKey().getUrl()),
            Filters.eq("_id." + TestingIssuesId.API_KEY_INFO + "." + ApiInfo.ApiInfoKey.METHOD, testResult.getApiInfoKey().getMethod().name()),
            Filters.eq("_id." + TestingIssuesId.TEST_ERROR_SOURCE, TestErrorSource.AUTOMATED_TESTING.name()),
            Filters.eq("_id." + TestingIssuesId.TEST_SUB_CATEGORY, testResult.getTestSubType())
        );
        
        // Get severity from first test result's confidence (same as testing module)
        Severity severity = Severity.HIGH; // default
        try {
            if (testResult.getTestResults() != null && !testResult.getTestResults().isEmpty()) {
                GenericTestResult firstResult = testResult.getTestResults().get(0);
                if (firstResult != null && firstResult.getConfidence() != null) {
                    severity = Severity.valueOf(firstResult.getConfidence().toString());
                }
            }
        } catch (Exception e) {
            // Keep default HIGH severity if conversion fails
        }
        
        // Check if issue exists to determine status (keep IGNORED if it was ignored, otherwise OPEN)
        TestingRunIssues existingIssue = TestingRunIssuesDao.instance.findOne(issueFilter);
        TestRunIssueStatus status = (existingIssue != null && existingIssue.getTestRunIssueStatus() == TestRunIssueStatus.IGNORED)
            ? TestRunIssueStatus.IGNORED
            : TestRunIssueStatus.OPEN;

        if (existingIssue != null) {
            // Update existing document: only set mutable fields (never _id)
            Bson updateFilter = Filters.eq("_id", existingIssue.getId());
            TestingRunIssuesDao.instance.updateOneNoUpsert(
                updateFilter,
                Updates.combine(
                    Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, status),
                    Updates.set(TestingRunIssues.LAST_SEEN, now),
                    Updates.set(TestingRunIssues.LAST_UPDATED, now),
                    Updates.set(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, testResult.getTestRunResultSummaryId()),
                    Updates.set(TestingRunIssues.UNREAD, false)
                )
            );
        } else {
            // Insert new document with _id set at insert time
            TestingRunIssues newIssue = new TestingRunIssues(
                issueId,
                severity,
                status,
                now,
                now,
                testResult.getTestRunResultSummaryId(),
                null,
                now,
                false
            );
            TestingRunIssuesDao.instance.insertOne(newIssue);
        }

        loggerMaker.infoAndAddToDb("Created/updated issue for vulnerability: " + issueId.toString(), LogDb.DASHBOARD);
    }
}
