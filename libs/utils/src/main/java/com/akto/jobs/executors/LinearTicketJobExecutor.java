package com.akto.jobs.executors;

import com.akto.dao.ConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Config.AktoHostUrlConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.LinearAutoTicketParams;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.jobs.JobExecutor;
import com.akto.linear.LinearIntegrationService;
import com.akto.log.LoggerMaker;
import com.akto.ticketing.ATicketIntegrationService.TicketCreationResult;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class LinearTicketJobExecutor extends JobExecutor<LinearAutoTicketParams> {

    public static final LinearTicketJobExecutor INSTANCE = new LinearTicketJobExecutor();
    private static final int BATCH_SIZE = 10;
    private static final LoggerMaker logger = new LoggerMaker(LinearTicketJobExecutor.class);

    private LinearTicketJobExecutor() {
        super(LinearAutoTicketParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        LinearAutoTicketParams params = paramClass.cast(job.getJobParams());

        List<TestingIssuesId> issueIds = fetchEligibleIssueIds(params.getSummaryId(), params.getSeverities());
        if (issueIds.isEmpty()) {
            logger.info("No issues found for severities: {}", params.getSeverities());
            return;
        }

        String dashboardUrl = resolveDashboardUrl(job);
        LinearIntegrationService service = new LinearIntegrationService();

        int created = 0;
        int failed = 0;
        for (int from = 0; from < issueIds.size(); from += BATCH_SIZE) {
            List<TestingIssuesId> batch = issueIds.subList(from, Math.min(from + BATCH_SIZE, issueIds.size()));
            TicketCreationResult result = service.createTickets(batch, params.getTeamId(), null, dashboardUrl);
            created += result.getSuccessCount();
            failed += result.getFailCount();
            updateJobHeartbeat(job);
        }

        logger.info("Linear auto-ticketing finished. Created {} and failed {} out of {} issues", created, failed,
            issueIds.size());

        if (created == 0 && failed > 0) {
            throw new Exception("Failed to create Linear tickets for " + failed + " issues");
        }
    }

    private String resolveDashboardUrl(Job job) {
        if (!DashboardMode.isOnPremDeployment()) {
            return Constants.DEFAULT_AKTO_DASHBOARD_URL;
        }

        AktoHostUrlConfig aktoUrlConfig = (AktoHostUrlConfig) ConfigsDao.instance.findOne(
            Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()));
        if (aktoUrlConfig == null) {
            logger.error("Akto Dashboard URL not found. jobId: {}", job.getId());
            return Constants.DEFAULT_AKTO_DASHBOARD_URL;
        }
        return aktoUrlConfig.getHostUrl();
    }

    private List<TestingIssuesId> fetchEligibleIssueIds(ObjectId summaryId, List<String> severities) {
        Bson filter = Filters.and(
            Filters.eq(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, summaryId),
            Filters.in(TestingRunIssues.KEY_SEVERITY, severities),
            Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN),
            Filters.exists(TestingRunIssues.LINEAR_ISSUE_URL, false)
        );

        return TestingRunIssuesDao.instance.findAll(filter, Projections.include(Constants.ID)).stream()
            .map(TestingRunIssues::getId)
            .collect(Collectors.toList());
    }
}
