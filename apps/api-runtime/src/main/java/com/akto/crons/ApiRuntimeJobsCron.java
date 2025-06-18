package com.akto.crons;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.JobsDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobStatus;
import com.akto.jobs.JobExecutorFactory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

public class ApiRuntimeJobsCron {

    private static final LoggerMaker logger = new LoggerMaker(ApiRuntimeJobsCron.class, LogDb.RUNTIME);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);

    public static final ApiRuntimeJobsCron INSTANCE = new ApiRuntimeJobsCron();

    private static final int MAX_HEARTBEAT_THRESHOLD_SECONDS = 300;

    public void jobsScheduler(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = Context.now();

                Bson scheduledFilter = getScheduleJobsFilter();

                Bson updateQ = Updates.combine(
                    Updates.set(Job.JOB_STATUS, JobStatus.RUNNING.name()),
                    Updates.set(Job.STARTED_AT, now),
                    Updates.set(Job.HEARTBEAT_AT, now)
                );

                FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                    .sort(Sorts.ascending(Job.SCHEDULED_AT))
                    .returnDocument(ReturnDocument.AFTER);

                Job job = null;
                try {
                    job = JobsDao.instance.getMCollection().findOneAndUpdate(
                        scheduledFilter,
                        updateQ,
                        options
                    );
                } catch (Exception e) {
                    logger.error("error while fetching scheduled jobs ", e);
                }

                if (job == null) {
                    logger.debug("No jobs to run");
                    return;
                }

                Job finalJob = job;
                executorService.submit(
                    () -> {
                        Context.accountId.set(finalJob.getAccountId());
                        JobExecutorFactory.getExecutor(finalJob.getJobParams().getJobType()).execute(finalJob);
                    }
                );
            } catch (Exception e) {
                logger.error("Error while scheduling jobs", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static @NotNull Bson getScheduleJobsFilter() {
        int now = Context.now();
        Bson executorFilter = Filters.eq(Job.JOB_EXECUTOR_TYPE, JobExecutorType.RUNTIME.name());

        return Filters.and(executorFilter,
            Filters.or(
                Filters.and(
                    Filters.eq(Job.JOB_STATUS, JobStatus.SCHEDULED.name()),
                    Filters.lt(Job.SCHEDULED_AT, now)
                ),
                Filters.and(
                    Filters.eq(Job.JOB_STATUS, JobStatus.RUNNING.name()),
                    Filters.lt(Job.HEARTBEAT_AT, now - MAX_HEARTBEAT_THRESHOLD_SECONDS)
                )
            )
        );
    }
}
