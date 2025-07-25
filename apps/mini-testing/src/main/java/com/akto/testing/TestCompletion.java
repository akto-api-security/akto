package com.akto.testing;

import com.akto.billing.UsageMetricUtils;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.Organization;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.notifications.slack.APITestStatusAlert;
import com.akto.notifications.slack.NewIssuesModel;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.SlackSender;
import com.akto.usage.OrgUtils;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.client.model.Filters;

public class TestCompletion {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestCompletion.class);
    public static final ScheduledExecutorService testTelemetryScheduler = Executors.newScheduledThreadPool(2);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public void markTestAsCompleteAndRunFunctions(TestingRun testingRun, ObjectId summaryId, long startDetailed){
        int scheduleTs = 0;
        int accountId = Context.accountId.get();
        if (testingRun.getPeriodInSeconds() > 0 ) {
            scheduleTs = testingRun.getScheduleTimestamp() + testingRun.getPeriodInSeconds();
        } else if (testingRun.getPeriodInSeconds() == -1) {
            scheduleTs = testingRun.getScheduleTimestamp() + 5 * 60;
        }

        if(GetRunningTestsStatus.getRunningTests().isTestRunning(testingRun.getId())){
            dataActor.updateTestingRunAndMarkCompleted(testingRun.getId().toHexString(), scheduleTs);
        }

        if(summaryId != null && testingRun.getTestIdConfig() != 1){
            TestExecutor.updateTestSummary(summaryId);
        }

        AllMetrics.instance.setTestingRunLatency(System.currentTimeMillis() - startDetailed);
        TestingRunResultSummary trrs = dataActor.fetchTestingRunResultSummary(summaryId.toHexString());
        List<TestingRunIssues> testingRunIssuesList = dataActor.fetchOpenIssues(summaryId.toHexString());

        String testType = "ONE_TIME";
        if(testingRun.getPeriodInSeconds()>0)
        {
            testType = "SCHEDULED";
        }
        if (trrs.getMetadata() != null) {
            testType = "CI_CD";
        }

        Map<String, Integer> apisAffectedCount = new HashMap<>();
        int newIssues = 0;
        List<NewIssuesModel> newIssuesModelList = new ArrayList<>();
        Map<String, Integer> severityCount = new HashMap<>();

        for (TestingRunIssues testingRunIssues: testingRunIssuesList) {
            String key = testingRunIssues.getSeverity().toString();
            if (!severityCount.containsKey(key)) {
                severityCount.put(key, 0);
            }

            int issuesSeverityCount = severityCount.get(key);
            severityCount.put(key, issuesSeverityCount+1);

            String testSubCategory = testingRunIssues.getId().getTestSubCategory();
            int totalApisAffected = apisAffectedCount.getOrDefault(testSubCategory, 0)+1;

            apisAffectedCount.put(
                    testSubCategory,
                    totalApisAffected
            );

            if(testingRunIssues.getCreationTime() > trrs.getStartTimestamp()) {
                newIssues++;
                if(newIssues < 6){
                    String testRunResultId = "";
                    Bson filterForRunResult = Filters.and(
                        Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunIssues.getLatestTestingRunSummaryId()),
                        Filters.eq(TestingRunResult.TEST_SUB_TYPE, testingRunIssues.getId().getTestSubCategory()),
                        Filters.eq(TestingRunResult.API_INFO_KEY, testingRunIssues.getId().getApiInfoKey())
                    );
                    TestingRunResult testingRunResult = dataActor.fetchTestingRunResults(filterForRunResult);
                    testRunResultId = testingRunResult.getHexId();

                    String issueCategory = testingRunIssues.getId().getTestSubCategory();
                    newIssuesModelList.add(new NewIssuesModel(
                            issueCategory,
                            testRunResultId,
                            apisAffectedCount.get(issueCategory),
                            testingRunIssues.getCreationTime()
                    ));
                }
            }

        }

        String collection = null;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if(testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE)) {
            CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
            int apiCollectionId = collectionWiseTestingEndpoints.getApiCollectionId();
            ApiCollection apiCollection = dataActor.fetchApiCollectionMeta(apiCollectionId);
            collection = apiCollection.getName();
        }
        long currentTime = Context.now();
        long startTimestamp = trrs.getStartTimestamp();
        long scanTimeInSeconds = Math.abs(currentTime - startTimestamp);
        long nextTestRun = testingRun.getPeriodInSeconds() == 0 ? 0 : ((long) testingRun.getScheduleTimestamp() + (long) testingRun.getPeriodInSeconds());

        SlackAlerts apiTestStatusAlert = new APITestStatusAlert(
            testingRun.getName(),
            (int) severityCount.getOrDefault(GlobalEnums.Severity.CRITICAL.name(), 0),
            (int) severityCount.getOrDefault(GlobalEnums.Severity.HIGH.name(), 0),
            (int) severityCount.getOrDefault(GlobalEnums.Severity.MEDIUM.name(), 0),
            (int) severityCount.getOrDefault(GlobalEnums.Severity.LOW.name(), 0),
            apisAffectedCount.size(),
            newIssues,
            trrs.getTotalApis(),
            collection,
            scanTimeInSeconds,
            testType,
            nextTestRun,
            newIssuesModelList,
            testingRun.getHexId(),
            trrs.getHexId(),
            "app.akto.io"
        );

        if (testingRun.isSendSlackAlert()) {
            SlackSender.sendAlert(accountId, apiTestStatusAlert, testingRun.getSelectedSlackChannelId(), dataActor.fetchSlackWebhooks());
        }

        Organization organization = OrgUtils.getOrganizationCached(accountId);


        if(organization != null && organization.getTestTelemetryEnabled()){
            loggerMaker.infoAndAddToDb("Test telemetry enabled for account: " + accountId + ", sending results", LogDb.TESTING);
            ObjectId finalSummaryId = summaryId;
            testTelemetryScheduler.execute(() -> {
                Context.accountId.set(accountId);
                try {
                    com.akto.onprem.Constants.sendTestResults(finalSummaryId, organization);
                    loggerMaker.infoAndAddToDb("Test telemetry sent for account: " + accountId, LogDb.TESTING);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in sending test telemetry for account: " + accountId);
                }
            });

        } else {
            loggerMaker.infoAndAddToDb("Test telemetry disabled for account: " + accountId, LogDb.TESTING);
        }

        scheduleAutoTicketCreationJob(testingRun, accountId, summaryId);
    }

    private void scheduleAutoTicketCreationJob(TestingRun testingRun, int accountId, ObjectId summaryId) {

        try {

            TestingRunConfig testRunConfig = dataActor.findTestingRunConfig(testingRun.getTestIdConfig());

            if (testRunConfig == null || testRunConfig.getAutoTicketingDetails() == null
                || !testRunConfig.getAutoTicketingDetails().isShouldCreateTickets()) {
                return;
            }

            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "JIRA_INTEGRATION");
            if (!featureAccess.getIsGranted()) {
                loggerMaker.error("Auto Create Tickets plan is not activated for the account - {}", accountId);
                return;
            }

            AutoTicketParams params = new AutoTicketParams(testingRun.getId(), summaryId,
                testRunConfig.getAutoTicketingDetails().getProjectId(),
                testRunConfig.getAutoTicketingDetails().getIssueType(),
                testRunConfig.getAutoTicketingDetails().getSeverities(), "JIRA");
            dataActor.scheduleAutoCreateTicketsJob(accountId, params, JobExecutorType.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.error("Error scheduling auto ticket creation job: {}", e.getMessage(), e);
        }
    }
}
