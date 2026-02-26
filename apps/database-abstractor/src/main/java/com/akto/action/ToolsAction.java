package com.akto.action;

import com.akto.dao.TestingRunWebhookDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.ApiInfo;
import com.akto.dto.TestingRunWebhook;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.slack.SlackSender;
import com.akto.notifications.slack.TestingRunWebhookAlert;
import com.mongodb.client.model.Filters;
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
            Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey);
            TestingRunResult testResult = TestingRunResultDao.instance.findOne(filter);

            if (testResult == null) {
                // Also check vulnerable collection
                testResult = VulnerableTestingRunResultDao.instance.findOne(filter);
            }

            if (testResult == null) {
                loggerMaker.infoAndAddToDb("Test result not found for UUID: " + uuid + ", testRunId: " + testRunId + ", testSubType: " + testSubType, LogDb.DASHBOARD);
                // Still return success since URL was hit
                result = "SUCCESS";
                return Action.SUCCESS.toUpperCase();
            }

            // Mark test result as vulnerable
            testResult.setVulnerable(true);

            // Mark all nested test results as vulnerable
            if (testResult.getTestResults() != null) {
                for (GenericTestResult testResultItem : testResult.getTestResults()) {
                    if (testResultItem != null) {
                        testResultItem.setVulnerable(true);
                    }
                }
            }

            // Update in appropriate collection
            Bson updateFilter = Filters.eq("_id", testResult.getId());

            // Update in testing_run_results collection
            TestingRunResultDao.instance.getMCollection().replaceOne(updateFilter, testResult);

            // Also update in vulnerable_testing_run_results collection
            VulnerableTestingRunResultDao.instance.getMCollection().replaceOne(updateFilter, testResult);

            loggerMaker.infoAndAddToDb("Marked test as vulnerable for UUID: " + uuid + ", testRunId: " + testRunId + ", testSubType: " + testSubType, LogDb.DASHBOARD);

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
}
