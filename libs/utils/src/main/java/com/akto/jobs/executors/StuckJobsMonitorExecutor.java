package com.akto.jobs.executors;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.StuckJobAlertsJobParams;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.notifications.slack.SlackSender;
import com.akto.notifications.slack.StuckJobAlert;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

public class StuckJobsMonitorExecutor extends JobExecutor<StuckJobAlertsJobParams> {

    public static final StuckJobsMonitorExecutor INSTANCE = new StuckJobsMonitorExecutor();
    private static final LoggerMaker logger = new LoggerMaker(StuckJobsMonitorExecutor.class, LoggerMaker.LogDb.DASHBOARD);

    private StuckJobsMonitorExecutor() {
        super(StuckJobAlertsJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        StuckJobAlertsJobParams params = paramClass.cast(job.getJobParams());
        int slackWebhookId = params.getSlackWebhookId();
        int stuckThresholdSeconds = params.getStuckThresholdSeconds();
        int accountId = Context.accountId.get();

        List<Job> stuckJobs = fetchStuckJobs(stuckThresholdSeconds, job.getId());

        if (stuckJobs.isEmpty()) {
            logger.info("No stuck jobs found for accountId: {}", accountId);
            return;
        }

        logger.info("Found {} stuck job(s) for accountId: {}", stuckJobs.size(), accountId);

        for (Job stuckJob : stuckJobs) {
            long runningForMinutes = (Context.now() - stuckJob.getStartedAt()) / 60L;
            String jobStatus = stuckJob.getJobStatus() != null ? stuckJob.getJobStatus().name() : "UNKNOWN";

            String projectId = null;
            String summaryId = null;
            String testingRunId = null;

            JobParams jobParams = stuckJob.getJobParams();
            if (jobParams instanceof AutoTicketParams) {
                AutoTicketParams autoTicketParams = (AutoTicketParams) jobParams;
                projectId = autoTicketParams.getProjectId();
                summaryId = autoTicketParams.getSummaryId() != null
                    ? autoTicketParams.getSummaryId().toHexString() : null;
                testingRunId = autoTicketParams.getTestingRunId() != null
                    ? autoTicketParams.getTestingRunId().toHexString() : null;
            }

            logger.warn("Stuck job detected. jobId: {}, jobStatus: {}, accountId: {}, runningForMinutes: {}",
                stuckJob.getId(), jobStatus, stuckJob.getAccountId(), runningForMinutes);

            StuckJobAlert alert = new StuckJobAlert(
                stuckJob.getAccountId(),
                jobStatus,
                projectId,
                summaryId,
                testingRunId,
                runningForMinutes
            );
            SlackSender.sendAlert(accountId, alert, slackWebhookId);
        }
    }

    private List<Job> fetchStuckJobs(int stuckThresholdSeconds, ObjectId monitorJobId) {
        int stuckCutoff = Context.now() - stuckThresholdSeconds;
        Bson filter = Filters.and(
            Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name()),
            Filters.lt(Job.STARTED_AT, stuckCutoff),
            Filters.ne(Job.ID, monitorJobId)
        );
        return JobsDao.instance.findAll(filter);
    }
}
