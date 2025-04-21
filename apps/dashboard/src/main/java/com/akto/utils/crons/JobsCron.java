package com.akto.utils.crons;

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

public class JobsCron {

    public static final JobsCron instance = new JobsCron();

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private static final int MAX_HEARTBEAT_THRESHOLD_SECONDS = 300;

    private static final LoggerMaker logger = new LoggerMaker(JobsCron.class, LogDb.DASHBOARD);

    public void jobsScheduler(JobExecutorType jobExecutorType) {
        scheduler.scheduleAtFixedRate(() -> {
            logger.debug("started jobs");

            // filters -
            /* OR
             *   - STATUS = Scheduled, ScheduledEpoch < Now
             *   - STATUS = Running, CREATE_TEST_TUN_ISSUES_TICKET < Now - 300 seconds
             */

            long now = Context.now();

            Bson executorFilter = Filters.eq(Job.JOB_EXECUTOR_TYPE, jobExecutorType.name());

            Bson scheduledFilter =
                Filters.and(executorFilter,
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

                // findOneAndUpdate
                // filtersQ from above
                // updateQ - { status=Running, Heartbeat=now }

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

        }, 0, 5, TimeUnit.SECONDS);
    }
}
