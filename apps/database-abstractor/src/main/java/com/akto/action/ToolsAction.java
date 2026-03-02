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
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
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
            ObjectId testRunId = tracking.getTestRunId();
            String testSubType = tracking.getTestSubType();

            // Find test result 
            Bson filter = Filters.and(
                TestingRunResultDao.generateFilter(testRunId, apiInfoKey),
                Filters.eq(TestingRunResult.TEST_SUB_TYPE, testSubType)
            );
            TestingRunResult testResult = TestingRunResultDao.instance.findOne(filter);

            if (testResult == null) {
                // Also check vulnerable collection
                testResult = VulnerableTestingRunResultDao.instance.findOne(filter);
            }

            if (testResult == null) {
                loggerMaker.errorAndAddToDb("Test result not found for UUID: " + uuid + ", testRunId: " + testRunId + ", apiInfoKey: " + apiInfoKey + ", testSubType: " + testSubType, LogDb.DASHBOARD);
                // Still return success since URL was hit and marked in webhook tracking
                result = "SUCCESS";
                return Action.SUCCESS.toUpperCase();
            }

            // Check if this is a NEW vulnerability (wasn't vulnerable before)
            boolean wasAlreadyVulnerable = testResult.isVulnerable();

            // Mark test result as vulnerable
            testResult.setVulnerable(true);

            // Mark all nested test results as vulnerable
            // Since we already filtered by testSubType above, all results in this TestingRunResult belong to the correct test
            if (testResult.getTestResults() != null) {
                for (GenericTestResult testResultItem : testResult.getTestResults()) {
                    if (testResultItem != null) {
                        testResultItem.setVulnerable(true);
                    }
                }
            }

            // Update in both collections (using upsert to ensure document exists in both)
            Bson updateFilter = Filters.eq("_id", testResult.getId());
            ReplaceOptions replaceOptions = new ReplaceOptions().upsert(true);

            // Update in testing_run_results collection
            TestingRunResultDao.instance.getMCollection().replaceOne(updateFilter, testResult, replaceOptions);

            // Also update in vulnerable_testing_run_results collection (upsert ensures it's created if not exists)
            VulnerableTestingRunResultDao.instance.getMCollection().replaceOne(updateFilter, testResult, replaceOptions);

            loggerMaker.infoAndAddToDb("Marked test as vulnerable for UUID: " + uuid + ", testRunId: " + testRunId + ", testSubType: " + testSubType, LogDb.DASHBOARD);

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
        
        // Check if issue already exists
        TestingRunIssues existingIssue = TestingRunIssuesDao.instance.findOne(Filters.eq("_id", issueId));
        
        if (existingIssue != null) {
            // Update existing issue - reopen if it was fixed/ignored
            TestRunIssueStatus newStatus = existingIssue.getTestRunIssueStatus() == TestRunIssueStatus.IGNORED
                ? TestRunIssueStatus.IGNORED  // Keep ignored status
                : TestRunIssueStatus.OPEN;     // Reopen if fixed
            
            TestingRunIssuesDao.instance.updateOne(
                Filters.eq("_id", issueId),
                Updates.combine(
                    Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, newStatus),
                    Updates.set(TestingRunIssues.LAST_SEEN, now),
                    Updates.set(TestingRunIssues.LAST_UPDATED, now),
                    Updates.set(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, testResult.getTestRunResultSummaryId()),
                    Updates.set(TestingRunIssues.UNREAD, false)
                )
            );
            loggerMaker.infoAndAddToDb("Updated existing issue: " + issueId.toString(), LogDb.DASHBOARD);
        } else {
            // Create new issue - get severity from first test result's confidence (same as testing module)
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
            
            TestingRunIssues newIssue = new TestingRunIssues(
                issueId,
                severity,
                TestRunIssueStatus.OPEN,
                now,  // creationTime
                now,  // lastSeen
                testResult.getTestRunResultSummaryId(),
                null, // jiraIssueUrl
                now,  // lastUpdated
                false // unread
            );
            
            TestingRunIssuesDao.instance.insertOne(newIssue);
            loggerMaker.infoAndAddToDb("Created new issue: " + issueId.toString(), LogDb.DASHBOARD);
        }
    }
}
