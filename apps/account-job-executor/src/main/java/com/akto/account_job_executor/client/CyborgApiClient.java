package com.akto.account_job_executor.client;

import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.HashMap;
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
        // Don't use logger during static initialization - can cause NullPointerException
        System.out.println("[CyborgApiClient] Cyborg URL configured: " + dbAbsHost + "/api");
        return dbAbsHost + "/api";
    }

    /**
     * Get JWT authentication token from environment.
     */
    private static String getAuthToken() {
        String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable not set");
        }
        return token;
    }

    /**
     * Make HTTP POST request to Cyborg API using Apache HttpClient.
     * This bypasses ApiExecutor's SSRF protection since we're making trusted internal calls.
     */
    private static String makePostRequest(String endpoint, Map<String, Object> requestBody) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url + endpoint);

            // Set headers
            httpPost.setHeader("Authorization", getAuthToken());
            httpPost.setHeader("Content-Type", "application/json");

            // Set body
            String jsonBody = mapper.writeValueAsString(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody));

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    logger.error("API request failed. Status: {}, Body: {}", statusCode, responseBody);
                    throw new IOException("API request failed with status: " + statusCode);
                }

                return responseBody;
            }
        }
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
        logger.info("=== fetchAndClaimJob called: now={}, threshold={} ===", now, heartbeatThreshold);
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("now", now);
            requestBody.put("heartbeatThreshold", heartbeatThreshold);

            logger.info("Fetching and claiming job from Cyborg API: now={}, threshold={}", now, heartbeatThreshold);
            String body = makePostRequest("/fetchAndClaimAccountJob", requestBody);

            logger.info("API response received, body length: {}", body != null ? body.length() : 0);
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
            logger.error("!!! EXCEPTION in fetchAndClaimJob: {}", e.getMessage(), e);
            e.printStackTrace();
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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jobId", id.toString());
            requestBody.put("status", status.name());
            requestBody.put("finishedAt", finishedAt);
            if (error != null) {
                requestBody.put("error", error);
            }

            logger.debug("Updating job status: jobId={}, status={}", id, status);
            makePostRequest("/updateAccountJobStatus", requestBody);
            logger.debug("Successfully updated job status: jobId={}, status={}", id, status);

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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jobId", id.toString());

            logger.debug("Updating job heartbeat: jobId={}", id);
            makePostRequest("/updateAccountJobHeartbeat", requestBody);
            logger.debug("Successfully updated job heartbeat: jobId={}", id);

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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jobId", id.toString());
            requestBody.put("updates", updates);

            logger.debug("Updating job: jobId={}, updates={}", id, updates);
            makePostRequest("/updateAccountJob", requestBody);
            logger.debug("Successfully updated job: jobId={}", id);

        } catch (Exception e) {
            logger.error("Failed to update job: jobId={}", id, e);
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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jobId", id.toString());

            logger.debug("Fetching job by ID: jobId={}", id);
            String body = makePostRequest("/fetchAccountJob", requestBody);

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
            logger.error("Error fetching job by ID: jobId={}", id, e);
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

        // Handle ObjectId - check both "_id" and "id" fields
        Object idObj = map.get("_id");
        if (idObj == null) {
            idObj = map.get("id");  // Try "id" without underscore
        }

        if (idObj != null) {
            if (idObj instanceof String) {
                job.setId(new ObjectId((String) idObj));
            } else if (idObj instanceof Map) {
                Map<String, Object> idMap = (Map<String, Object>) idObj;
                // Handle different ObjectId formats: {"$oid": "..."} or {"timestamp": ..., "date": ...}
                if (idMap.containsKey("$oid")) {
                    job.setId(new ObjectId((String) idMap.get("$oid")));
                } else if (idMap.containsKey("timestamp")) {
                    int timestamp = getIntValue(idMap, "timestamp");
                    job.setId(new ObjectId(timestamp, 0));
                }
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
