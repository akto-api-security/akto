package com.akto.account_job_executor.client;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API client for making requests to Cyborg service for AccountJob operations.
 * Handles job polling, status updates, and heartbeat tracking via REST APIs.
 */
public class CyborgApiClient {

    private static final String url = buildCyborgUrl();
    private static final LoggerMaker logger = new LoggerMaker(CyborgApiClient.class);
    private static final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Build Cyborg service URL from environment variable.
     */
    private static String buildCyborgUrl() {
        String dbAbsHost = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (dbAbsHost == null || dbAbsHost.isEmpty()) {
            throw new IllegalStateException("DATABASE_ABSTRACTOR_SERVICE_URL environment variable not set");
        }
        if (dbAbsHost.endsWith("/")) {
            dbAbsHost = dbAbsHost.substring(0, dbAbsHost.length() - 1);
        }
        logger.info("Cyborg URL configured: {}", dbAbsHost + "/api");
        return dbAbsHost + "/api";
    }

    /**
     * Build HTTP headers with JWT authentication token.
     */
    private static Map<String, List<String>> buildHeaders() {
        String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable not set");
        }
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList(token));
        return headers;
    }

    /**
     * Fetch and claim one AccountJob atomically from Cyborg service.
     * This is called by the job executor to poll for scheduled jobs.
     *
     * @param now Current timestamp (epoch seconds)
     * @param heartbeatThreshold Threshold for stale running jobs (seconds)
     * @return Claimed AccountJob or null if no jobs available
     */
    public static AccountJob fetchAndClaimJob(long now, int heartbeatThreshold) {
        try {
            Map<String, List<String>> headers = buildHeaders();
            BasicDBObject requestBody = new BasicDBObject();
            requestBody.put("now", now);
            requestBody.put("heartbeatThreshold", heartbeatThreshold);

            OriginalHttpRequest request = new OriginalHttpRequest(
                url + "/fetchAndClaimAccountJob",
                "", "POST",
                requestBody.toString(),
                headers, ""
            );

            logger.debug("Fetching and claiming job from Cyborg API");
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() != 200) {
                logger.error("Failed to fetch job. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
                return null;
            }

            String body = response.getBody();
            if (body == null || body.isEmpty() || body.equals("null") || body.equals("{}")) {
                logger.debug("No jobs available to claim");
                return null;
            }

            // Parse JSON response to AccountJob
            Map<String, Object> responseMap = mapper.readValue(body, Map.class);
            Map<String, Object> jobMap = (Map<String, Object>) responseMap.get("accountJob");

            if (jobMap == null || jobMap.isEmpty()) {
                logger.debug("No jobs available to claim");
                return null;
            }

            AccountJob job = convertMapToAccountJob(jobMap);
            logger.info("Successfully claimed job: {}", job.getId());
            return job;

        } catch (Exception e) {
            logger.error("Error fetching and claiming job", e);
            return null;
        }
    }

    /**
     * Update AccountJob status via Cyborg API.
     * Used after job completion or failure.
     *
     * @param id Job ID
     * @param status Job status (COMPLETED, FAILED, etc.)
     * @param finishedAt Finish timestamp
     * @param error Error message if failed
     */
    public static void updateJobStatus(ObjectId id, JobStatus status, Integer finishedAt, String error) {
        try {
            Map<String, List<String>> headers = buildHeaders();
            BasicDBObject requestBody = new BasicDBObject();
            requestBody.put("jobId", id.toString());
            requestBody.put("status", status.name());
            requestBody.put("finishedAt", finishedAt);
            if (error != null) {
                requestBody.put("error", error);
            }

            OriginalHttpRequest request = new OriginalHttpRequest(
                url + "/updateAccountJobStatus",
                "", "POST",
                requestBody.toString(),
                headers, ""
            );

            logger.debug("Updating job status: jobId={}, status={}", id, status);
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() != 200) {
                logger.error("Failed to update job status. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
            } else {
                logger.debug("Successfully updated job status: jobId={}, status={}", id, status);
            }

        } catch (Exception e) {
            logger.error("Error updating job status", e);
        }
    }

    /**
     * Update AccountJob heartbeat via Cyborg API.
     * Called during job execution to indicate progress.
     *
     * @param id Job ID
     */
    public static void updateJobHeartbeat(ObjectId id) {
        try {
            Map<String, List<String>> headers = buildHeaders();
            BasicDBObject requestBody = new BasicDBObject();
            requestBody.put("jobId", id.toString());

            OriginalHttpRequest request = new OriginalHttpRequest(
                url + "/updateAccountJobHeartbeat",
                "", "POST",
                requestBody.toString(),
                headers, ""
            );

            logger.debug("Updating job heartbeat: jobId={}", id);
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() != 200) {
                logger.error("Failed to update job heartbeat. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
            } else {
                logger.debug("Successfully updated job heartbeat: jobId={}", id);
            }

        } catch (Exception e) {
            logger.error("Error updating job heartbeat", e);
        }
    }

    /**
     * General update method for AccountJob via Cyborg API.
     * Used for rescheduling and other updates.
     *
     * @param id Job ID
     * @param updates Map of field names to new values
     */
    public static void updateJob(ObjectId id, Map<String, Object> updates) {
        try {
            Map<String, List<String>> headers = buildHeaders();
            BasicDBObject requestBody = new BasicDBObject();
            requestBody.put("jobId", id.toString());
            requestBody.put("updates", updates);

            OriginalHttpRequest request = new OriginalHttpRequest(
                url + "/updateAccountJob",
                "", "POST",
                requestBody.toString(),
                headers, ""
            );

            logger.debug("Updating job: jobId={}, updates={}", id, updates);
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() != 200) {
                logger.error("Failed to update job. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
            } else {
                logger.debug("Successfully updated job: jobId={}", id);
            }

        } catch (Exception e) {
            logger.error("Error updating job", e);
        }
    }

    /**
     * Fetch specific AccountJob by ID via Cyborg API.
     *
     * @param id Job ID
     * @return AccountJob or null if not found
     */
    public static AccountJob findById(ObjectId id) {
        try {
            Map<String, List<String>> headers = buildHeaders();
            BasicDBObject requestBody = new BasicDBObject();
            requestBody.put("jobId", id.toString());

            OriginalHttpRequest request = new OriginalHttpRequest(
                url + "/fetchAccountJob",
                "", "POST",
                requestBody.toString(),
                headers, ""
            );

            logger.debug("Fetching job by ID: jobId={}", id);
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() != 200) {
                logger.error("Failed to fetch job. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
                return null;
            }

            String body = response.getBody();
            if (body == null || body.isEmpty() || body.equals("null") || body.equals("{}")) {
                logger.debug("Job not found: jobId={}", id);
                return null;
            }

            Map<String, Object> responseMap = mapper.readValue(body, Map.class);
            Map<String, Object> jobMap = (Map<String, Object>) responseMap.get("accountJob");

            if (jobMap == null || jobMap.isEmpty()) {
                logger.debug("Job not found: jobId={}", id);
                return null;
            }

            AccountJob job = convertMapToAccountJob(jobMap);
            logger.debug("Successfully fetched job: jobId={}", id);
            return job;

        } catch (Exception e) {
            logger.error("Error fetching job by ID", e);
            return null;
        }
    }

    /**
     * Convert Map to AccountJob object.
     * Handles type conversions for ObjectId, enums, and primitive types.
     *
     * @param map Map representation of AccountJob
     * @return AccountJob object
     */
    private static AccountJob convertMapToAccountJob(Map<String, Object> map) {
        AccountJob job = new AccountJob();

        // Handle ObjectId
        if (map.get("_id") != null) {
            Object idObj = map.get("_id");
            if (idObj instanceof String) {
                job.setId(new ObjectId((String) idObj));
            } else if (idObj instanceof Map) {
                Map<String, Object> idMap = (Map<String, Object>) idObj;
                job.setId(new ObjectId((String) idMap.get("$oid")));
            }
        }

        // Basic fields
        job.setAccountId(getIntValue(map, "accountId"));
        job.setJobType((String) map.get("jobType"));
        job.setSubType((String) map.get("subType"));
        job.setConfig((Map<String, Object>) map.get("config"));
        job.setRecurringIntervalSeconds(getIntValue(map, "recurringIntervalSeconds"));
        job.setCreatedAt(getIntValue(map, "createdAt"));
        job.setLastUpdatedAt(getIntValue(map, "lastUpdatedAt"));

        // Execution tracking fields
        if (map.get("jobStatus") != null) {
            String statusStr = (String) map.get("jobStatus");
            job.setJobStatus(JobStatus.valueOf(statusStr));
        }

        if (map.get("scheduleType") != null) {
            String typeStr = (String) map.get("scheduleType");
            job.setScheduleType(ScheduleType.valueOf(typeStr));
        }

        job.setScheduledAt(getIntValue(map, "scheduledAt"));
        job.setStartedAt(getIntValue(map, "startedAt"));
        job.setFinishedAt(getIntValue(map, "finishedAt"));
        job.setHeartbeatAt(getIntValue(map, "heartbeatAt"));
        job.setError((String) map.get("error"));

        return job;
    }

    /**
     * Helper method to safely extract integer values from map.
     */
    private static int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        return 0;
    }
}
