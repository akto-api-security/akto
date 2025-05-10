package com.akto.testing;

import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.jobs.JobScheduler;
import com.akto.billing.UsageMetricUtils;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.usage.MetricTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.Constants;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class TestCompletion {

    private static final LoggerMaker logger = new LoggerMaker(TestCompletion.class, LogDb.TESTING);
    private static final ScheduledExecutorService testTelemetryScheduler = Executors.newScheduledThreadPool(2);

    public void markTestAsCompleteAndRunFunctions(TestingRun testingRun, ObjectId summaryId){
        Bson completedUpdate = Updates.combine(
                Updates.set(TestingRun.STATE, TestingRun.State.COMPLETED),
                Updates.set(TestingRun.END_TIMESTAMP, Context.now())
        );

        if (testingRun.getPeriodInSeconds() > 0 ) {
            completedUpdate = Updates.combine(
                    Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                    Updates.set(TestingRun.END_TIMESTAMP, Context.now()),
                    Updates.set(TestingRun.SCHEDULE_TIMESTAMP, testingRun.getScheduleTimestamp() + testingRun.getPeriodInSeconds())
            );
        } else if (testingRun.getPeriodInSeconds() == -1) {
            completedUpdate = Updates.combine(
                    Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                    Updates.set(TestingRun.END_TIMESTAMP, Context.now()),
                    Updates.set(TestingRun.SCHEDULE_TIMESTAMP, testingRun.getScheduleTimestamp() + 5 * 60)
            );
        }

        if(GetRunningTestsStatus.getRunningTests().isTestRunning(testingRun.getId())){
            TestingRunDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
                    Filters.eq("_id", testingRun.getId()),  completedUpdate
            );
        }

        if(summaryId != null && testingRun.getTestIdConfig() != 1){
            TestExecutor.updateTestSummary(summaryId);
        }

        int accountId = Context.accountId.get();

        Main.raiseMixpanelEvent(summaryId, testingRun, accountId);

        scheduleAutoTicketCreationJob(testingRun, accountId, summaryId);

        Organization organization = OrganizationsDao.instance.findOne(
                        Filters.in(Organization.ACCOUNTS, accountId));

        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(accountId, MetricTypes.TEST_RUNS);
        SyncLimit syncLimit = featureAccess.fetchSyncLimit();
        int usageLeft = syncLimit.getUsageLeft();

        if(organization != null && organization.getTestTelemetryEnabled()){
            logger.debug("Test telemetry enabled for account: " + accountId + ", sending results");
            testTelemetryScheduler.execute(() -> {
                Context.accountId.set(accountId);
                try {
                    com.akto.onprem.Constants.sendTestResults(summaryId, organization);
                    logger.debug("Test telemetry sent for account: " + accountId);
                } catch (Exception e) {
                    logger.error("Error in sending test telemetry for account: " + accountId + " " + e.getMessage());
                }
            });
        } else {
            logger.debug("Test telemetry disabled for account: " + accountId);
        }

        // update usage after test is completed.
        int deltaUsage = 0;
        if(syncLimit.checkLimit){
            deltaUsage = usageLeft - syncLimit.getUsageLeft();
        }

        UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.TEST_RUNS, accountId, deltaUsage);
    }

    public void scheduleAutoTicketCreationJob(TestingRun testingRun, int accountId, ObjectId summaryId) {

        try {
            TestingRunConfig testRunConfig = TestingRunConfigDao.instance.findOne(Constants.ID,
                testingRun.getTestIdConfig());

            if (testRunConfig == null || testRunConfig.getAutoTicketingDetails() == null
                || !testRunConfig.getAutoTicketingDetails().isShouldCreateTickets()) {
                return;
            }

            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "JIRA_INTEGRATION");
            if (!featureAccess.getIsGranted()) {
                logger.error("Auto Create Tickets plan is not activated for the account - {}", accountId);
                return;
            }

            AutoTicketParams params = new AutoTicketParams(testingRun.getId(), summaryId,
                testRunConfig.getAutoTicketingDetails().getProjectId(),
                testRunConfig.getAutoTicketingDetails().getIssueType(),
                testRunConfig.getAutoTicketingDetails().getSeverities(), "JIRA");
            JobScheduler.scheduleRunOnceJob(accountId, params, JobExecutorType.DASHBOARD);
        } catch (Exception e) {
            logger.error("Error scheduling auto ticket creation job: {}", e.getMessage(), e);
        }
    }
}
