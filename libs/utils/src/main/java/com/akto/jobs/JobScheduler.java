package com.akto.jobs;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.log.LoggerMaker;

public final class JobScheduler {
    private static final LoggerMaker logger = new LoggerMaker(JobScheduler.class, LoggerMaker.LogDb.DASHBOARD);

    public static void scheduleRunOnceJob(int accountId, JobParams params, JobExecutorType jobExecutorType) {
        try {
            int now = Context.now();
            JobsDao.instance.insertOne(
                new Job(accountId,
                    ScheduleType.RUN_ONCE,
                    JobStatus.SCHEDULED,
                    params,
                    jobExecutorType,
                    now,
                    0,
                    0,
                    0,
                    now
                ));
            logger.debug("Job scheduled with parameters. accountId: {}. jobParams: {}", accountId, params);
        } catch (Exception e) {
            logger.error("Error while scheduling job for accountId: {} and jobParams: {}", accountId, params, e);
        }
    }
}
