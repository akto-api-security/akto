package com.akto.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobStatus;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;

public abstract class JobExecutor<T extends JobParams> {
    private static final LoggerMaker logger = new LoggerMaker(JobExecutor.class);

    protected final Class<T> paramClass;

    public JobExecutor(Class<T> paramClass) {
        this.paramClass = paramClass;
    }

    public final void execute(Job job) {
        ObjectId jobId = job.getId();
        logger.info("Executing job: {}", job);
        Job executedJob;
        try {
            runJob(job);
            executedJob = logSuccess(jobId);
            logger.info("Finished executing job: {}", executedJob);
        } catch (RetryableJobException rex) {
            executedJob = reScheduleJob(job);
            logger.error("Error occurred while executing the job. Re-scheduling the job. {}", executedJob, rex);
        } catch (Exception e) {
            executedJob = logFailure(jobId, e);
            logger.error("Error occurred while executing the job. Not re-scheduling. {}", executedJob, e);
        }
    }

    protected Job logSuccess(ObjectId id) {
        return JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, id),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.FINISHED_AT, Context.now()),
                Updates.set(Job.JOB_STATUS, JobStatus.COMPLETED.name())
            ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    protected Job logFailure(ObjectId id, Exception e) {
        return JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, id),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.FINISHED_AT, Context.now()),
                Updates.set(Job.JOB_STATUS, JobStatus.FAILED.name()),
                Updates.set("error", e.getMessage())
            ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    protected void updateJobHeartbeat(Job job) {
        JobsDao.instance.getMCollection().updateOne(
            Filters.and(
                Filters.eq(Job.ID, job.getId()),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.set(Job.HEARTBEAT_AT, Context.now())
        );
        logger.info("Job is still running. Updated heartbeat for job: {}", job);
    }

    private Job reScheduleJob(Job job) {
        return JobsDao.instance.getMCollection().findOneAndUpdate(
            Filters.and(
                Filters.eq(Job.ID, job.getId()),
                Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name())
            ),
            Updates.combine(
                Updates.set(Job.FINISHED_AT, Context.now() + 300),
                Updates.set(Job.JOB_STATUS, JobStatus.SCHEDULED.name())
            ),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    protected abstract void runJob(Job job) throws Exception;

}
