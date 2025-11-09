package com.akto.action.threat_detection.utils;

import com.akto.ProtoMessageUtils;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest.Filter;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.UpdateMaliciousEventStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.UpdateMaliciousEventStatusResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.List;
import java.util.Optional;

/**
 * Helper class for threat detection operations, providing reusable methods
 * for updating malicious events via the threat detection backend service.
 */
public class ThreatDetectionHelper {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatDetectionHelper.class);

    /**
     * Result object returned from updateMaliciousEvent operations
     */
    public static class UpdateResult {
        private final boolean success;
        private final String message;
        private final int updatedCount;

        public UpdateResult(boolean success, String message, int updatedCount) {
            this.success = success;
            this.message = message;
            this.updatedCount = updatedCount;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getUpdatedCount() {
            return updatedCount;
        }
    }

    /**
     * Updates malicious event(s) with status and/or Jira ticket URL.
     * Supports three modes:
     * 1. Single event update (eventId)
     * 2. Bulk update by IDs (eventIds)
     * 3. Filter-based update (filter)
     *
     * @param httpClient      HTTP client for making requests
     * @param backendUrl      Backend URL for threat detection service
     * @param apiToken        API token for authentication
     * @param eventId         Single event ID to update (optional)
     * @param eventIds        List of event IDs for bulk update (optional)
     * @param filter          Filter for filtered updates (optional)
     * @param status          New status to set (optional)
     * @param jiraTicketUrl   Jira ticket URL to set (optional)
     * @return UpdateResult containing success status, message, and count
     */
    public static UpdateResult updateMaliciousEvent(
            CloseableHttpClient httpClient,
            String backendUrl,
            String apiToken,
            String eventId,
            List<String> eventIds,
            Filter.Builder filter,
            String status,
            String jiraTicketUrl) {

        try {
            HttpPost post = new HttpPost(
                    String.format("%s/api/dashboard/update_malicious_event_status", backendUrl));
            post.addHeader("Authorization", "Bearer " + apiToken);
            post.addHeader("Content-Type", "application/json");

            UpdateMaliciousEventStatusRequest.Builder requestBuilder = UpdateMaliciousEventStatusRequest.newBuilder();

            // Determine which type of update this is
            if (eventId != null && !eventId.isEmpty()) {
                // Single event update
                requestBuilder.setEventId(eventId);
            } else if (eventIds != null && !eventIds.isEmpty()) {
                // Bulk update by IDs
                requestBuilder.addAllEventIds(eventIds);
            } else if (filter != null) {
                // Filter-based update
                requestBuilder.setFilter(filter);
            } else {
                return new UpdateResult(false, "No event identifier provided", 0);
            }

            // Set optional fields
            if (!StringUtils.isEmpty(status)) {
                requestBuilder.setStatus(status);
            }

            if (!StringUtils.isEmpty(jiraTicketUrl)) {
                requestBuilder.setJiraTicketUrl(jiraTicketUrl);
            }

            UpdateMaliciousEventStatusRequest request = requestBuilder.build();

            String msg = ProtoMessageUtils.toString(request).orElse("{}");
            StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
            post.setEntity(requestEntity);

            try (CloseableHttpResponse resp = httpClient.execute(post)) {
                String responseBody = EntityUtils.toString(resp.getEntity());

                if (resp.getStatusLine().getStatusCode() != 200) {
                    String errorMsg = "Failed to update malicious event: " + responseBody;
                    loggerMaker.errorAndAddToDb(errorMsg, LoggerMaker.LogDb.DASHBOARD);
                    return new UpdateResult(false, errorMsg, 0);
                }

                Optional<UpdateMaliciousEventStatusResponse> responseOpt = ProtoMessageUtils
                        .<UpdateMaliciousEventStatusResponse>toProtoMessage(
                                UpdateMaliciousEventStatusResponse.class, responseBody);

                if (responseOpt.isPresent()) {
                    UpdateMaliciousEventStatusResponse response = responseOpt.get();
                    return new UpdateResult(
                            response.getSuccess(),
                            response.getMessage(),
                            response.getUpdatedCount()
                    );
                } else {
                    return new UpdateResult(false, "Failed to parse response", 0);
                }
            }

        } catch (Exception e) {
            String errorMsg = "Error updating malicious event: " + e.getMessage();
            loggerMaker.errorAndAddToDb(errorMsg, LoggerMaker.LogDb.DASHBOARD);
            return new UpdateResult(false, errorMsg, 0);
        }
    }

    /**
     * Convenience method to update a single malicious event's status
     */
    public static UpdateResult updateMaliciousEventStatus(
            CloseableHttpClient httpClient,
            String backendUrl,
            String apiToken,
            String eventId,
            String status) {
        return updateMaliciousEvent(httpClient, backendUrl, apiToken, eventId, null, null, status, null);
    }

    /**
     * Convenience method to update a single malicious event's Jira ticket URL
     */
    public static UpdateResult updateMaliciousEventJiraUrl(
            CloseableHttpClient httpClient,
            String backendUrl,
            String apiToken,
            String eventId,
            String jiraTicketUrl) {
        return updateMaliciousEvent(httpClient, backendUrl, apiToken, eventId, null, null, null, jiraTicketUrl);
    }


}
