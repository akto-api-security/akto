package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Struts Action for AccountJob-related operations via Cyborg API.
 * Provides APIs for job polling, status updates, and heartbeat tracking.
 */
@Getter
@Setter
public class AccountJobAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AccountJobAction.class, LogDb.DB_ABS);

    private static final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Input/Output fields
    private Map<String, Object> accountJob;
    private String jobId;
    private String status;
    private Integer finishedAt;
    private String error;
    private long now;
    private int heartbeatThreshold;
    private Map<String, Object> updates;

    /**
     * Fetch and claim a single AccountJob atomically.
     * This is used by the job executor to poll for scheduled jobs.
     *
     * Query logic:
     * - Find jobs where (status=SCHEDULED AND scheduledAt < now) OR (status=RUNNING AND heartbeatAt < now-threshold)
     * - Update status to RUNNING, set startedAt and heartbeatAt
     * - Return the claimed job
     */
    public String fetchAndClaimAccountJob() {
        try {
            // Convert long to int for proper type handling
            int nowInt = (int) now;
            int heartbeatThresholdInt = (int) (now - heartbeatThreshold);

            // Build filter: scheduled jobs or stale running jobs
            Bson scheduledFilter = Filters.and(
                Filters.eq(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name()),
                Filters.lt(AccountJob.SCHEDULED_AT, nowInt)
            );

            Bson staleRunningFilter = Filters.and(
                Filters.eq(AccountJob.JOB_STATUS, JobStatus.RUNNING.name()),
                Filters.lt(AccountJob.HEARTBEAT_AT, heartbeatThresholdInt)
            );

            Bson filter = Filters.or(scheduledFilter, staleRunningFilter);

            // Update to RUNNING with heartbeat (use int values for consistency)
            Bson update = Updates.combine(
                Updates.set(AccountJob.JOB_STATUS, JobStatus.RUNNING.name()),
                Updates.set(AccountJob.STARTED_AT, nowInt),
                Updates.set(AccountJob.HEARTBEAT_AT, nowInt),
                Updates.set(AccountJob.LAST_UPDATED_AT, nowInt)
            );

            // Atomic findOneAndUpdate
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .sort(Sorts.ascending(AccountJob.SCHEDULED_AT))
                .returnDocument(ReturnDocument.AFTER);

            AccountJob claimedJob = AccountJobDao.instance.getMCollection().findOneAndUpdate(
                filter, update, options
            );

            if (claimedJob != null) {
                this.accountJob = mapper.convertValue(claimedJob, Map.class);
                // Convert ObjectId to string for consistent serialization
                if (claimedJob.getId() != null) {
                    this.accountJob.put("_id", claimedJob.getId().toHexString());
                }
                loggerMaker.debug("Claimed job: {}", claimedJob.getId());
            } else {
                this.accountJob = null;
                loggerMaker.debug("No jobs to claim");
            }

        } catch (Exception e) {
            loggerMaker.error("Error in fetchAndClaimAccountJob", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Update AccountJob status (used after job completion or failure).
     */
    public String updateAccountJobStatus() {
        try {
            ObjectId id = new ObjectId(jobId);
            JobStatus jobStatus = JobStatus.valueOf(status);
            int now = Context.now();

            Bson filter = Filters.eq(AccountJob.ID, id);
            Bson update;

            if (error != null && !error.isEmpty()) {
                // Job failed with error
                update = Updates.combine(
                    Updates.set(AccountJob.JOB_STATUS, jobStatus.name()),
                    Updates.set(AccountJob.FINISHED_AT, finishedAt != null ? finishedAt : now),
                    Updates.set(AccountJob.ERROR, error),
                    Updates.set(AccountJob.LAST_UPDATED_AT, now)
                );
            } else {
                // Job completed successfully
                update = Updates.combine(
                    Updates.set(AccountJob.JOB_STATUS, jobStatus.name()),
                    Updates.set(AccountJob.FINISHED_AT, finishedAt != null ? finishedAt : now),
                    Updates.unset(AccountJob.ERROR),
                    Updates.set(AccountJob.LAST_UPDATED_AT, now)
                );
            }

            AccountJobDao.instance.getMCollection().updateOne(filter, update);
            loggerMaker.debug("Updated job status: jobId={}, status={}", jobId, status);

        } catch (Exception e) {
            loggerMaker.error("Error in updateAccountJobStatus", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Update AccountJob heartbeat (used during job execution to indicate progress).
     */
    public String updateAccountJobHeartbeat() {
        try {
            ObjectId id = new ObjectId(jobId);
            int now = Context.now();

            Bson filter = Filters.and(
                Filters.eq(AccountJob.ID, id),
                Filters.eq(AccountJob.JOB_STATUS, JobStatus.RUNNING.name())
            );

            Bson update = Updates.combine(
                Updates.set(AccountJob.HEARTBEAT_AT, now),
                Updates.set(AccountJob.LAST_UPDATED_AT, now)
            );

            AccountJobDao.instance.getMCollection().updateOne(filter, update);
            loggerMaker.debug("Updated job heartbeat: jobId={}", jobId);

        } catch (Exception e) {
            loggerMaker.error("Error in updateAccountJobHeartbeat", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * General update method for AccountJob (used for rescheduling, etc.).
     */
    public String updateAccountJob() {
        try {
            ObjectId id = new ObjectId(jobId);

            Bson filter = Filters.eq(AccountJob.ID, id);

            // Build update from map with proper type conversion
            java.util.Set<String> integerFields = new java.util.HashSet<>(java.util.Arrays.asList(
                AccountJob.SCHEDULED_AT,
                AccountJob.STARTED_AT,
                AccountJob.FINISHED_AT,
                AccountJob.HEARTBEAT_AT,
                AccountJob.LAST_UPDATED_AT,
                AccountJob.CREATED_AT,
                AccountJob.RECURRING_INTERVAL_SECONDS
            ));

            Bson[] updateOperations = updates.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    if (integerFields.contains(key) && value != null) {
                        if (value instanceof Long) {
                            value = ((Long) value).intValue();
                        } else if (value instanceof Double) {
                            value = ((Double) value).intValue();
                        }
                    }

                    return Updates.set(key, value);
                })
                .toArray(Bson[]::new);

            Bson update = Updates.combine(updateOperations);

            AccountJobDao.instance.getMCollection().updateOne(filter, update);
            loggerMaker.debug("Updated job: jobId={}, updates={}", jobId, updates);

        } catch (Exception e) {
            loggerMaker.error("Error in updateAccountJob", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Fetch a specific AccountJob by ID.
     */
    public String fetchAccountJob() {
        try {
            ObjectId id = new ObjectId(jobId);

            AccountJob job = AccountJobDao.instance.findOne(AccountJob.ID, id);

            if (job != null) {
                this.accountJob = mapper.convertValue(job, Map.class);
                // Convert ObjectId to string for consistent serialization
                if (job.getId() != null) {
                    this.accountJob.put("_id", job.getId().toHexString());
                }
                loggerMaker.debug("Fetched job: jobId={}", jobId);
            } else {
                this.accountJob = null;
                loggerMaker.debug("Job not found: jobId={}", jobId);
            }

        } catch (Exception e) {
            loggerMaker.error("Error in fetchAccountJob", e);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }
}
