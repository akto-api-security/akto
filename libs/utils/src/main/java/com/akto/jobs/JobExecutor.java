package com.akto.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobStatus;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public abstract class JobExecutor<T extends JobParams> {

    protected final Class<T> paramClass;
    private static final LoggerMaker logger = new LoggerMaker(JobExecutor.class, LoggerMaker.LogDb.DASHBOARD);

    protected JobExecutor(Class<T> paramClass) {
        this.paramClass = paramClass;
    }

    public final void execute(Job job) {
        markRunning(job);
        try {
            runJob(job);
            markCompleted(job);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Job failed. jobId: " + job.getId(), LoggerMaker.LogDb.DASHBOARD);
            markFailed(job);
        }
    }

    protected abstract void runJob(Job job) throws Exception;

    private void markRunning(Job job) {
        int now = Context.now();
        job.setJobStatus(JobStatus.RUNNING);
        job.setStartedAt(now);
        JobsDao.instance.updateOneNoUpsert(
            Filters.eq(Job.ID, job.getId()),
            Updates.combine(
                Updates.set(Job.JOB_STATUS, JobStatus.RUNNING.name()),
                Updates.set(Job.STARTED_AT, now)
            )
        );
    }

    private void markCompleted(Job job) {
        int now = Context.now();
        job.setJobStatus(JobStatus.COMPLETED);
        job.setFinishedAt(now);
        JobsDao.instance.updateOneNoUpsert(
            Filters.eq(Job.ID, job.getId()),
            Updates.combine(
                Updates.set(Job.JOB_STATUS, JobStatus.COMPLETED.name()),
                Updates.set(Job.FINISHED_AT, now)
            )
        );
    }

    private void markFailed(Job job) {
        int now = Context.now();
        job.setJobStatus(JobStatus.FAILED);
        job.setFinishedAt(now);
        JobsDao.instance.updateOneNoUpsert(
            Filters.eq(Job.ID, job.getId()),
            Updates.combine(
                Updates.set(Job.JOB_STATUS, JobStatus.FAILED.name()),
                Updates.set(Job.FINISHED_AT, now)
            )
        );
    }
}
