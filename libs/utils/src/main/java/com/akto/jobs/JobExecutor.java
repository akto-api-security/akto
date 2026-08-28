package com.akto.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.akto.notifications.slack.CustomTextAlert;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.slack.api.Slack;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.event.Level;

public abstract class JobExecutor<T extends JobParams> {
    private static final LoggerMaker logger = new LoggerMaker(JobExecutor.class);

    protected final Class<T> paramClass;
    private static final Slack SLACK_INSTANCE = Slack.getInstance();
    private static final ExecutorService SLACK_EXECUTOR = Executors.newSingleThreadExecutor();

    private final List<String> jobLogs = new ArrayList<>();

    public JobExecutor(Class<T> paramClass) {
        this.paramClass = paramClass;
    }

    public final void execute(Job job) {
        ObjectId jobId = job.getId();
        logger.info("Executing job: {}", job);
        Job executedJob;
        String errorMessage = null;
        try {
            runJob(job);
            executedJob = logSuccess(jobId);
            logger.info("Finished executing job: {}", executedJob);
        } catch (RetryableJobException rex) {
            executedJob = reScheduleJob(job);
            logger.error("Error occurred while executing the job. Re-scheduling the job. {}", executedJob, rex);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            executedJob = logFailure(jobId, e);
            logger.error("Error occurred while executing the job. Not re-scheduling. {}", executedJob, e);
        }
        handleRecurringJob(executedJob);
        String capturedLogs = "";
        if (!jobLogs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String line : jobLogs) {
                sb.append(line).append('\n');
            }
            capturedLogs = sb.toString();
            jobLogs.clear();
        }
        sendSlackAlert(job, errorMessage, capturedLogs);
    }

    private Job logSuccess(ObjectId id) {
        int now = Context.now();
        return JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, id),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.FINISHED_AT, now),
                Updates.set(Job.LAST_UPDATED_AT, now),
                Updates.set(Job.JOB_STATUS, JobStatus.COMPLETED.name()),
                Updates.unset("error")
            ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    protected Job logFailure(ObjectId id, Exception e) {
        int now = Context.now();
        return JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, id),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.FINISHED_AT, now),
                Updates.set(Job.LAST_UPDATED_AT, now),
                Updates.set(Job.JOB_STATUS, JobStatus.FAILED.name()),
                Updates.set("error", e.getMessage())
            ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    protected void updateJobHeartbeat(Job job) {
        int now = Context.now();
        JobsDao.instance.getMCollection().updateOne(
            Filters.and(
                Filters.eq(Job.ID, job.getId()),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.HEARTBEAT_AT, now),
                Updates.set(Job.LAST_UPDATED_AT, now)
            )
        );
        logger.info("Job is still running. Updated heartbeat for job: {}", job);
    }

    protected void updateJobParams(Job job, T params) {
        JobsDao.instance.getMCollection().updateOne(
            Filters.and(
                Filters.eq(Job.ID, job.getId())
            ),
            Updates.combine(
                Updates.set(Job.JOB_PARAMS, params),
                Updates.set(Job.LAST_UPDATED_AT, Context.now())
            )
        );
    }

    protected void logAndCollect(Level level, String message, Object... vars) {
        try {
            switch (level) {
                case ERROR:
                    logger.error(message, vars);
                    break;
                case WARN:
                    logger.warn(message, vars);
                    break;
                case DEBUG:
                    logger.debug(message, vars);
                    break;
                default:
                    logger.info(message, vars);
                    break;
            }
            if (level == Level.ERROR) {
                vars = MessageFormatter.trimmedCopy(vars);
            }
            String formatted = MessageFormatter.arrayFormat(message, vars).getMessage();
            jobLogs.add("[" + level + "]: " + formatted);
        } catch (Exception e) {
            logger.error("Error logging message: " + message, e);
        }
    }

    private Job reScheduleJob(Job job) {
        int now = Context.now();
        return JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, job.getId()),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.FINISHED_AT, now + 300),
                Updates.set(Job.LAST_UPDATED_AT, now),
                Updates.set(Job.JOB_STATUS, JobStatus.SCHEDULED.name())
            ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    private void handleRecurringJob(Job job) {
        if (job.getScheduleType() != ScheduleType.RECURRING) {
            return;
        }
        logger.info("Re-scheduling recurring job: {}", job);
        int now = Context.now();
        JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, job.getId())
            ),
            Updates.combine(
                Updates.set(Job.SCHEDULED_AT, now + job.getRecurringIntervalSeconds()),
                Updates.set(Job.JOB_STATUS, JobStatus.SCHEDULED.name()),
                Updates.set(Job.LAST_UPDATED_AT, now)
            )
        );
    }

    private void sendSlackAlert(Job job, String errorMessage, String capturedLogs) {
        int targetAccountId = job.getAccountId();
        SLACK_EXECUTOR.submit(() -> {
            Context.accountId.set(1000000);
            SlackWebhook slackWebhook = SlackWebhooksDao.instance.findOne(Filters.empty());
            if (targetAccountId == 1723492815 && slackWebhook != null) { // send slack alerts only for account
                try {
                    StringBuilder message = new StringBuilder();

                    message.append("Job ")
                        .append(errorMessage == null ? "completed successfully. " : "failed. ")
                        .append("Name: ").append(job.getJobParams().getJobType())
                        .append(" | Account: ").append(targetAccountId)
                        .append(" | JobId: ").append(job.getId().toHexString());

                    if (errorMessage != null) {
                        message.append(" | Error: ").append(errorMessage);
                    }

                    if (capturedLogs != null && !capturedLogs.isEmpty()) {
                        message.append("\n\nLogs:\n").append(capturedLogs);
                    }

                    CustomTextAlert customTextAlert = new CustomTextAlert(message.toString());
                    SLACK_INSTANCE.send(slackWebhook.getWebhook(), customTextAlert.toJson());
                } catch (Exception e) {
                    logger.error("Error sending slack alert", e);
                }
            }
        });
    }

    protected abstract void runJob(Job job) throws Exception;

}
