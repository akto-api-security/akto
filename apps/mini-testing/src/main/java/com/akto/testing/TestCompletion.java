package com.akto.testing;

import com.akto.billing.UsageMetricUtils;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.testing.TestingRunConfig;

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

        if(GetRunningTestsStatus.getRunningTests().isTestRunning(testingRun.getId())){
            dataActor.updateTestingRunAndMarkCompleted(testingRun.getId().toHexString(), scheduleTs);
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
