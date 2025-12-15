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
    private static final int MAX_HEARTBEAT_THRESHOLD_SECONDS = 5;

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
                logger.info("Polling for jobs: now={}, threshold={}", now, MAX_HEARTBEAT_THRESHOLD_SECONDS);

                // Fetch and claim one job atomically via Cyborg API
                // This returns either:
                // 1. A SCHEDULED job where scheduledAt < now
                // 2. A RUNNING job where heartbeatAt < now - 300s (stale job)
                // 3. null if no jobs available
                logger.info("About to call CyborgApiClient.fetchAndClaimJob()...");
                AccountJob job = CyborgApiClient.fetchAndClaimJob(now, MAX_HEARTBEAT_THRESHOLD_SECONDS);
                logger.info("CyborgApiClient.fetchAndClaimJob() returned: {}", job != null ? "job found" : "null");

                if (job == null) {
                    logger.info("No jobs to run at this time");
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
                logger.error("!!! EXCEPTION in AccountJobs scheduler !!!", e);
                e.printStackTrace();
                System.err.println("EXCEPTION: " + e.getMessage());
            } catch (Throwable t) {
                logger.error("!!! THROWABLE in AccountJobs scheduler !!!", t);
                t.printStackTrace();
                System.err.println("THROWABLE: " + t.getMessage());
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
