package com.akto.account_job_executor.cron;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutorFactory;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.AccountJob;
import com.akto.log.LoggerMaker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cron scheduler for polling and executing AccountJobs.
 * Runs every 5 seconds to fetch jobs from Cyborg API and execute them in a thread pool.
 */
public class AccountJobsCron {

    public static final AccountJobsCron instance = new AccountJobsCron();

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private static final int MAX_HEARTBEAT_THRESHOLD_SECONDS = 300;

    private static final LoggerMaker logger = new LoggerMaker(AccountJobsCron.class);

    private AccountJobsCron() {
        // Private constructor for singleton
    }

    /**
     * Start the job scheduler that polls every 5 seconds.
     * This method should be called once during application startup.
     */
    public void startScheduler() {
        logger.info("Starting AccountJobs scheduler (polling every 5 seconds)");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = Context.now();

                // Fetch and claim one job atomically via Cyborg API
                // This returns either:
                // 1. A SCHEDULED job where scheduledAt < now
                // 2. A RUNNING job where heartbeatAt < now - 300s (stale job)
                // 3. null if no jobs available
                AccountJob job = CyborgApiClient.fetchAndClaimJob(now, MAX_HEARTBEAT_THRESHOLD_SECONDS);

                if (job == null) {
                    logger.debug("No jobs to run");
                    return;
                }

                logger.info("Claimed job for execution: jobId={}, jobType={}, subType={}",
                    job.getId(), job.getJobType(), job.getSubType());

                // Execute job in thread pool
                AccountJob finalJob = job;
                executorService.submit(() -> {
                    try {
                        // Set account context for the job execution
                        Context.accountId.set(finalJob.getAccountId());
                        logger.info("Executing job in thread pool: jobId={}", finalJob.getId());

                        // Get appropriate executor for the job type and execute
                        AccountJobExecutorFactory.getExecutor(finalJob.getJobType())
                            .execute(finalJob);

                    } catch (Exception e) {
                        logger.error("Error executing job: jobId={}", finalJob.getId(), e);
                    } finally {
                        // Clear account context
                        Context.accountId.remove();
                    }
                });

            } catch (Exception e) {
                logger.error("Error in AccountJobs scheduler", e);
            }
        }, 0, 5, TimeUnit.SECONDS);

        logger.info("AccountJobs scheduler started successfully");
    }

    /**
     * Shutdown the scheduler gracefully.
     * This should be called during application shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down AccountJobs scheduler");

        scheduler.shutdown();
        executorService.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("AccountJobs scheduler shut down complete");
    }
}
