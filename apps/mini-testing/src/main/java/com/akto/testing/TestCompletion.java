package com.akto.testing;

import com.akto.billing.UsageMetricUtils;
import com.akto.dto.ApiCollection;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.notifications.data.TestingAlertData;
import com.akto.notifications.teams.TeamsAlert;
import com.akto.util.enums.GlobalEnums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.bson.types.ObjectId;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.Organization;
import com.akto.dto.testing.TestingRun;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.usage.OrgUtils;

public class TestCompletion {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestCompletion.class, LogDb.TESTING);
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

        if (testingRun != null && testingRun.getId() != null) {
            dataActor.updateTestingRunAndMarkCompleted(testingRun.getId().toHexString(), scheduleTs);
        }

        if (testingRun != null && testingRun.getId() != null && summaryId != null) {
            try {
                sendTeamsAlertIfNeeded(testingRun, summaryId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error sending test completion alerts for testingRunId: "
                        + testingRun.getId().toHexString() + " summaryId: " + summaryId);
            }
        }

        if(summaryId != null && testingRun.getTestIdConfig() != 1){
            TestExecutor.updateTestSummary(summaryId);
        }

        AllMetrics.instance.setTestingRunLatency(System.currentTimeMillis() - startDetailed);

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

    // The mini-testing worker doesn't have direct DB access, so unlike apps/testing's
    // TeamsSender/WebhookSender (which write straight to Mongo), this composes the alert
    // locally from data fetched via DataActor and sends the webhook HTTP call itself,
    // then reports the result back through DataActor for bookkeeping.
    private void sendTeamsAlertIfNeeded(TestingRun testingRun, ObjectId summaryId) {
        if (!testingRun.isSendMsTeamsAlert()) {
            return;
        }

        List<CustomWebhook> teamsWebhooks = dataActor.fetchTeamsWebhooksForTestResults();
        if (teamsWebhooks == null || teamsWebhooks.isEmpty()) {
            return;
        }

        Map<ObjectId, TestingRunResultSummary> summaryMap = dataActor.fetchTestingRunResultSummaryMap(testingRun.getId().toHexString());

        TestingRunResultSummary testingRunResultSummary = summaryMap != null ? summaryMap.get(testingRun.getId()) : null;
        if (testingRunResultSummary == null) {
            return;
        }

        int totalApis = testingRunResultSummary.getTotalApis();
        String testType = "ONE_TIME";
        if (testingRun.getPeriodInSeconds() > 0) {
            testType = "SCHEDULED";
        }
        if (testingRunResultSummary.getMetadata() != null) {
            testType = "CI_CD";
        }

        List<TestingRunIssues> testingRunIssuesList = dataActor.fetchOpenIssues(summaryId.toHexString());
        if (testingRunIssuesList == null) {
            testingRunIssuesList = new ArrayList<>();
        }

        Map<String, Integer> severityCount = new HashMap<>();
        int newIssues = 0;
        for (TestingRunIssues issue : testingRunIssuesList) {
            String key = issue.getSeverity().toString();
            severityCount.put(key, severityCount.getOrDefault(key, 0) + 1);
            if (issue.getCreationTime() > testingRunResultSummary.getStartTimestamp()) {
                newIssues++;
            }
        }

        String collection = null;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if (testingEndpoints != null && testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE)) {
            CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
            ApiCollection apiCollection = dataActor.fetchApiCollectionMeta(collectionWiseTestingEndpoints.getApiCollectionId());
            collection = apiCollection != null ? apiCollection.getName() : null;
        }

        long currentTime = Context.now();
        long scanTimeInSeconds = Math.abs(currentTime - testingRunResultSummary.getStartTimestamp());
        long nextTestRun = testingRun.getPeriodInSeconds() == 0 ? 0
                : ((long) testingRun.getScheduleTimestamp() + (long) testingRun.getPeriodInSeconds());

        TestingAlertData alertData = new TestingAlertData(
                collection != null ? collection : testingRun.getName(),
                severityCount.getOrDefault(GlobalEnums.Severity.CRITICAL.name(), 0),
                severityCount.getOrDefault(GlobalEnums.Severity.HIGH.name(), 0),
                severityCount.getOrDefault(GlobalEnums.Severity.MEDIUM.name(), 0),
                severityCount.getOrDefault(GlobalEnums.Severity.LOW.name(), 0),
                testingRunIssuesList.size(),
                newIssues,
                totalApis,
                collection,
                scanTimeInSeconds,
                testType,
                nextTestRun,
                new ArrayList<>(),
                testingRun.getHexId(),
                summaryId.toHexString()
        );

        for (CustomWebhook webhook : teamsWebhooks) {
            int now = Context.now();
            List<String> errors = new ArrayList<>();
            String message = null;
            OriginalHttpResponse response = null;
            try {
                String payload = TeamsAlert.createAndGetBody(alertData, webhook);
                Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
                OriginalHttpRequest request = new OriginalHttpRequest(webhook.getUrl(), webhook.getQueryParams(),
                        webhook.getMethod().toString(), payload, headers, "");
                try {
                    response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
                    loggerMaker.infoAndAddToDb("sendTeamsAlertIfNeeded: webhook callback for webhookId=" + webhook.getId()
                            + " url=" + webhook.getUrl()
                            + " statusCode=" + (response != null ? response.getStatusCode() : "null")
                            + " responseBody=" + (response != null ? response.getBody() : "null"), LogDb.TESTING);
                    if (response == null || response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                        String statusCode = response == null ? "null" : String.valueOf(response.getStatusCode());
                        errors.add("Webhook endpoint returned non-2xx status: " + statusCode);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "sendTeamsAlertIfNeeded: ApiExecutor.sendRequest threw for url=" + webhook.getUrl());
                    errors.add("API execution failed: " + e.getMessage());
                }
                message = "url=" + webhook.getUrl() + ", statusCode=" + (response != null ? response.getStatusCode() : "null");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "sendTeamsAlertIfNeeded: error building/sending Teams alert");
                errors.add("Error building/sending Teams alert: " + e.getMessage());
            }

            if (!errors.isEmpty()) {
                loggerMaker.errorAndAddToDb("sendTeamsAlertIfNeeded: errors=" + errors, LogDb.TESTING);
            }

            try {
                dataActor.recordWebhookSendResult(webhook.getId(), webhook.getUserEmail(), now, message, errors);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error recording webhook send result for webhookId: " + webhook.getId());
            }
        }
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
