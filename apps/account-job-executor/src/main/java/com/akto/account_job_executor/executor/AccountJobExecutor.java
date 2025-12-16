package com.akto.account_job_executor.executor;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for AccountJob executors.
 * Handles job lifecycle: execution, success/failure tracking, retry logic, and recurring job rescheduling.
 *
 * Subclasses must implement the runJob() method with their specific job logic.
 */
public abstract class AccountJobExecutor {

    private static final LoggerMaker logger = new LoggerMaker(AccountJobExecutor.class);

    /**
     * Execute the job with full lifecycle management.
     * This method handles:
     * - Calling the subclass's runJob() method
     * - Marking job as COMPLETED on success
     * - Marking job as FAILED on error
     * - Rescheduling job on RetryableJobException
     * - Rescheduling recurring jobs automatically
     *
     * @param job The AccountJob to execute
     */
    public final void execute(AccountJob job) {
        ObjectId jobId = job.getId();
        logger.info("Executing job: jobId={}, jobType={}, subType={}",
            jobId, job.getJobType(), job.getSubType());

        AccountJob executedJob;
        try {
            // Run the job-specific logic
            runJob(job);

            // Mark job as completed
            executedJob = logSuccess(jobId);
            logger.info("Finished executing job successfully: jobId={}", jobId);

        } catch (RetryableJobException rex) {
            // Retryable error - reschedule job for 5 minutes later
            executedJob = reScheduleJob(job);
            logger.error("Retryable error occurred. Re-scheduling job for 5 minutes later: jobId={}",
                jobId, rex);

        } catch (Exception e) {
            // Non-retryable error - mark as failed
            executedJob = logFailure(jobId, e);
            logger.error("Non-retryable error occurred. Job marked as FAILED: jobId={}", jobId, e);
        }

        // Handle recurring jobs
        handleRecurringJob(executedJob);
    }

    /**
     * Mark job as successfully completed.
     * Updates status to COMPLETED and clears any error message.
     *
     * @param id Job ID
     * @return Updated AccountJob
     */
    private AccountJob logSuccess(ObjectId id) {
        int now = Context.now();
        logger.debug("Marking job as COMPLETED: jobId={}", id);

        CyborgApiClient.updateJobStatus(id, JobStatus.COMPLETED, now, null);
        AccountJob job = CyborgApiClient.findById(id);

        return job;
    }

    /**
     * Mark job as failed with error message.
     * Updates status to FAILED and records the error.
     *
     * @param id Job ID
     * @param e Exception that caused the failure
     * @return Updated AccountJob
     */
    protected AccountJob logFailure(ObjectId id, Exception e) {
        int now = Context.now();
        String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        logger.debug("Marking job as FAILED: jobId={}, error={}", id, errorMessage);

        CyborgApiClient.updateJobStatus(id, JobStatus.FAILED, now, errorMessage);
        AccountJob job = CyborgApiClient.findById(id);

        return job;
    }

    /**
     * Update job heartbeat to indicate the job is still running.
     * Should be called periodically during long-running jobs (e.g., every 30 seconds).
     * This prevents the job from being considered stale and re-claimed by the scheduler.
     *
     * @param job The job being executed
     */
    protected void updateJobHeartbeat(AccountJob job) {
        CyborgApiClient.updateJobHeartbeat(job.getId());
        logger.info("Job is still running. Updated heartbeat for job: jobId={}", job.getId());
    }

    /**
     * Reschedule job for 5 minutes later (used for retryable errors).
     * Changes status back to SCHEDULED with scheduledAt = now + 300 seconds.
     *
     * @param job The job to reschedule
     * @return Updated AccountJob
     */
    private AccountJob reScheduleJob(AccountJob job) {
        int now = Context.now();
        int rescheduleAt = now + 300; // 5 minutes later
        logger.debug("Rescheduling job for 5 minutes later: jobId={}, rescheduleAt={}",
            job.getId(), rescheduleAt);

        Map<String, Object> updates = new HashMap<>();
        updates.put(AccountJob.SCHEDULED_AT, rescheduleAt);
        updates.put(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name());
        updates.put(AccountJob.LAST_UPDATED_AT, now);

        CyborgApiClient.updateJob(job.getId(), updates);
        AccountJob updatedJob = CyborgApiClient.findById(job.getId());

        return updatedJob;
    }

    /**
     * Handle recurring job rescheduling.
     * If the job is RECURRING, automatically reschedule it for the next interval.
     * Only reschedules if the job is COMPLETED or FAILED (not for retried jobs).
     *
     * @param job The executed job
     */
    private void handleRecurringJob(AccountJob job) {
        if (job == null || job.getScheduleType() != ScheduleType.RECURRING) {
            return;
        }

        logger.info("Re-scheduling recurring job: jobId={}, interval={}s",
            job.getId(), job.getRecurringIntervalSeconds());

        int now = Context.now();
        int nextRun = now + job.getRecurringIntervalSeconds();

        Map<String, Object> updates = new HashMap<>();
        updates.put(AccountJob.SCHEDULED_AT, nextRun);
        updates.put(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name());
        updates.put(AccountJob.LAST_UPDATED_AT, now);

        CyborgApiClient.updateJob(job.getId(), updates);

        logger.info("Recurring job rescheduled: jobId={}, nextRun={}", job.getId(), nextRun);
    }

    /**
     * Execute the job-specific logic.
     * Subclasses must implement this method with their specific job execution logic.
     *
     * If the job encounters a temporary error that should be retried, throw RetryableJobException.
     * For permanent errors, throw any other Exception.
     *
     * For long-running jobs, call updateJobHeartbeat(job) periodically (every 30-60 seconds)
     * to prevent the job from being considered stale.
     *
     * @param job The AccountJob to execute
     * @throws Exception If job execution fails
     */
    protected abstract void runJob(AccountJob job) throws Exception;
}
