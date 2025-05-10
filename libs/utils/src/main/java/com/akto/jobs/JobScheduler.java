package com.akto.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public final class JobScheduler {
    private static final LoggerMaker logger = new LoggerMaker(JobScheduler.class, LoggerMaker.LogDb.DASHBOARD);

    public static void scheduleRunOnceJob(int accountId, JobParams params, JobExecutorType jobExecutorType) {
        try {
            int now = Context.now();
            JobsDao.instance.insertOne(
                new Job(
                    accountId,
                    ScheduleType.RUN_ONCE,
                    JobStatus.SCHEDULED,
                    params,
                    jobExecutorType,
                    now,
                    0,
                    0,
                    0,
                    now,
                    now
                ));
            logger.debug("Job scheduled with parameters. accountId: {}. jobParams: {}", accountId, params);
        } catch (Exception e) {
            logger.error("Error while scheduling job for accountId: {} and jobParams: {}", accountId, params, e);
        }
    }

    public static Job scheduleRecurringJob(int accountId, JobParams params, JobExecutorType jobExecutorType,
        int recurringIntervalSeconds) {
        try {
            int now = Context.now();
            Job job = new Job(
                accountId,
                ScheduleType.RECURRING,
                JobStatus.SCHEDULED,
                params,
                jobExecutorType,
                now + recurringIntervalSeconds,
                0,
                0,
                0,
                now,
                now,
                recurringIntervalSeconds
            );
            JobsDao.instance.insertOne(job);
            logger.debug("Job scheduled with parameters. accountId: {}. jobParams: {}", accountId, params);
            return job;
        } catch (Exception e) {
            logger.error("Error while scheduling job for accountId: {} and jobParams: {}", accountId, params, e);
            return null;
        }
    }

    public static void deleteJob(ObjectId jobId) {
        Bson query = Filters.eq(Job.ID, jobId);
        Bson update = Updates.combine(
            Updates.set(Job.JOB_STATUS, JobStatus.STOPPED.name()),
            Updates.set(Job.LAST_UPDATED_AT, Context.now())
        );
        JobsDao.instance.getMCollection().updateOne(query, update);
    }

    public static Job restartJob(ObjectId jobId) {
        int now = Context.now();
        Bson query = Filters.eq(Job.ID, jobId);
        Bson update = Updates.combine(
            Updates.set(Job.JOB_STATUS, JobStatus.SCHEDULED.name()),
            Updates.set(Job.LAST_UPDATED_AT, now)
        );

        return JobsDao.instance.getMCollection().findOneAndUpdate(query, update,
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }
}
