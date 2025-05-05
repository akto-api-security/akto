package com.akto.api_clients;

import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.jobs.utils.JobConstants;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JiraApiClient {

    private static final LoggerMaker logger = new LoggerMaker(JiraApiClient.class);

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    private static final String GET_TRANSITIONS_ENDPOINT = "/rest/api/3/issue/%s/transitions";
    private static final String PUT_TRANSITIONS_ENDPOINT = "/rest/api/3/issue/%s/transitions";
    private static final String JIRA_SEARCH_ENDPOINT = "/rest/api/3/search/jql";
    private static final String GET_BULK_TRANSITIONS_ENDPOINT = "/rest/api/3/bulk/issues/transition?issueIdsOrKeys=";
    private static final String POST_BULK_TRANSITIONS_ENDPOINT = "/rest/api/3/bulk/issues/transition";
    private static final String SEARCH_JQL = "project = \"%s\" AND updated >= \"-%dm\" AND labels = \"%s\" ORDER BY updated ASC";

    private static final String PROJECT_KEY_REGEX = "^[A-Z][A-Z0-9]+$";

    public static String getTransitionIdForStatus(JiraIntegration jira, String issueKey, String statusName)
        throws Exception {

        if (issueKey == null || issueKey.isEmpty()) {
            throw new Exception("Issue key cannot be empty.");
        }

        if (statusName == null || statusName.isEmpty()) {
            throw new Exception("Status name cannot be empty.");
        }

        String url = jira.getBaseUrl() + String.format(GET_TRANSITIONS_ENDPOINT, issueKey);

        Request request = new Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", getBasicAuthHeaders(jira.getUserEmail(), jira.getApiToken()))
            .addHeader("Accept", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Failed to get transitions: Url: {}, Response: {}", url, response);
                throw new IOException("Failed to get transitions: " + response);
            }

            if (response.body() == null) {
                logger.error("Response body is null. Url: {}, Response: {}", url, response);
                throw new Exception("Response body is null.");
            }

            String responseBody = response.body().string();

            Map<String, Object> responseMap = JsonUtils.getMap(responseBody);
            List<Map<String, Object>> transitions = (List<Map<String, Object>>) responseMap.get("transitions");

            String id = String.valueOf(transitions.stream()
                .filter(trMap -> statusName.equalsIgnoreCase(String.valueOf(trMap.get("name"))))
                .findFirst()
                .orElseThrow(() -> new Exception(
                    "Transition id for issueKey: " + issueKey + " to '" + statusName + "' not found."))
                .get("id"));

            logger.info("Transition id for issueKey: {} and statusName: {} is: {}", issueKey, statusName, id);
            return id;
        }
    }

    public static void transitionIssue(JiraIntegration jira, String issueKey, String transitionId) throws IOException {
        String url = jira.getBaseUrl() + String.format(PUT_TRANSITIONS_ENDPOINT, issueKey);

        BasicDBObject payload = new BasicDBObject();
        payload.put("transition", new BasicDBObject("id", transitionId));

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(payload.toString().getBytes()))
            .addHeader("Authorization", getBasicAuthHeaders(jira.getUserEmail(), jira.getApiToken()))
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Failed to transition issue. Url: {}, Response: {}", url, response);
                throw new IOException("Failed to transition issue: " + response);
            }

            logger.debug("Transitioned issue " + issueKey + " to status ID " + transitionId);
        }
    }


    public static Map<String, BasicDBObject> fetchUpdatedTickets(JiraIntegration jira, String projectKey,
        int updatedAfter) throws Exception {

        String jql = buildJql(projectKey, updatedAfter);

        boolean hasMore = true;
        Map<String, BasicDBObject> results = new HashMap<>();
        String nextPageToken = null;

        while (hasMore) {

            BasicDBObject body = new BasicDBObject("jql", jql)
                .append("fields", Arrays.asList("status", "updated"));
            if (nextPageToken != null) {
                body.append("nextPageToken", nextPageToken);
            }

            String url = jira.getBaseUrl() + JIRA_SEARCH_ENDPOINT;
            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toJson().getBytes()))
                .addHeader("Authorization", getBasicAuthHeaders(jira.getUserEmail(), jira.getApiToken()))
                .addHeader("Content-Type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.body() != null) {
                        logger.error("Failed to get Jira Tickets. Url: {}, Response body: {}", url,
                            response.body().string());
                    } else {
                        logger.error("Failed to get Jira tickets: Url: {}, Response: {}", url, response);
                    }
                    throw new IOException("Failed to get tickets: " + response);
                }

                if (response.body() == null) {
                    logger.error("Response body is null. Url: {}, Response: {}", url, response);
                    throw new Exception("Response body is null.");
                }

                String responseBody = response.body().string();

                BasicDBObject responseObj = BasicDBObject.parse(responseBody);

                List<?> issues = (List<?>) responseObj.get("issues");
                if (issues != null) {
                    for (Object obj : issues) {
                        BasicDBObject issue = (BasicDBObject) obj;
                        BasicDBObject fields = (BasicDBObject) issue.get("fields");
                        BasicDBObject statusObj = (BasicDBObject) fields.get("status");
                        String key = issue.getString("key");
                        BasicDBObject ticket = new BasicDBObject("ticketKey", key)
                            .append("ticketStatus", statusObj.getString("name"))
                            .append("ticketStatusId", statusObj.getString("id"))
                            .append("jiraUpdatedAt", covertToEpochSeconds(fields.getString("updated")));

                        results.put(key, ticket);
                    }
                }
                nextPageToken = (String) responseObj.get("nextPageToken");
                hasMore = nextPageToken != null;
            }
        }

        return results;
    }

    private static String buildJql(String projectKey, int updatedAfter) {
        if (!projectKey.matches(PROJECT_KEY_REGEX)) {
            throw new IllegalArgumentException("Invalid project key");
        }

        if (updatedAfter <= 0) {
            throw new IllegalArgumentException("Invalid time range. updatedAfter must be in past.");
        }

        return String.format(SEARCH_JQL, projectKey, updatedAfter, JobConstants.TICKET_LABEL_AKTO_SYNC);
    }

    // add pagination if issue size is greater than 1000
    public static Map<Integer, List<String>> getTransitions(JiraIntegration jira, List<String> issueKeys,
        String targetStatusName) throws Exception {
        if (issueKeys.size() > 1000) {
            throw new Exception(
                "Issue keys list size cannot exceed 1000. Jira transition API supports 1000 issues at a time.");
        }
        String joinedKeys = String.join(",", issueKeys);
        String url = jira.getBaseUrl() + GET_BULK_TRANSITIONS_ENDPOINT + joinedKeys;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", getBasicAuthHeaders(jira.getUserEmail(), jira.getApiToken()))
            .addHeader("Accept", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Failed to get transitions: Url: {}, Response: {}", url, response);
                throw new IOException("Failed to get transitions" + response);
            }

            if (response.body() == null) {
                logger.error("Response body is null. Url: {}, Response: {}", url, response);
                throw new Exception("Response body is null.");
            }

            JsonNode root = new ObjectMapper().readTree(response.body().string());
            return parseTransitions(root, targetStatusName);
        }
    }

    private static Map<Integer, List<String>> parseTransitions(JsonNode root, String statusNameFilter) {
        Map<Integer, List<String>> transitionMap = new HashMap<>();

        JsonNode availableTransitions = root.path("availableTransitions");
        for (JsonNode block : availableTransitions) {
            List<String> issues = new ArrayList<>();
            for (JsonNode issueNode : block.path("issues")) {
                issues.add(issueNode.asText());
            }

            for (JsonNode transition : block.path("transitions")) {
                String statusName = transition.path("to").path("statusName").asText();
                if (statusName.equalsIgnoreCase(statusNameFilter)) {
                    int transitionId = transition.path("transitionId").asInt();
                    transitionMap.computeIfAbsent(transitionId, k -> new ArrayList<>()).addAll(issues);
                }
            }
        }

        return transitionMap;
    }

    private static String getBasicAuthHeaders(String username, String password) {
        return Credentials.basic(username, password);
    }

    private static int covertToEpochSeconds(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        OffsetDateTime odt = OffsetDateTime.parse(date, formatter);
        long epochSeconds = odt.toEpochSecond();
        return (int) epochSeconds;
    }

    public static boolean bulkTransitionIssues(JiraIntegration jira, Map<Integer, List<String>> issueKeysToTransitionId) throws IOException {
        String url = jira.getBaseUrl() + POST_BULK_TRANSITIONS_ENDPOINT;

        // Prepare the request payload
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> bulkTransitionInputs = new ArrayList<>();

        // For each transition ID, create a bulk transition input with the associated issue keys
        for (Map.Entry<Integer, List<String>> entry : issueKeysToTransitionId.entrySet()) {
            Integer transitionId = entry.getKey();
            List<String> issueKeys = entry.getValue();

            if (issueKeys != null && !issueKeys.isEmpty()) {
                Map<String, Object> bulkTransitionInput = new HashMap<>();
                bulkTransitionInput.put("selectedIssueIdsOrKeys", issueKeys);
                bulkTransitionInput.put("transitionId", transitionId);
                bulkTransitionInputs.add(bulkTransitionInput);
            }
        }

        payload.put("bulkTransitionInputs", bulkTransitionInputs);
        payload.put("sendBulkNotification", false);

        // Convert payload to JSON
        String jsonPayload = new ObjectMapper().writeValueAsString(payload);

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(jsonPayload.getBytes()))
            .addHeader("Authorization", getBasicAuthHeaders(jira.getUserEmail(), jira.getApiToken()))
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Failed to perform bulk transition. Url: {}, Response: {}", url, response);
                if (response.body() != null) {
                    logger.error("Response body: {}", response.body().string());
                }
                return false;
            }

            logger.info("Successfully performed bulk transition for {} transition groups", bulkTransitionInputs.size());
            return true;
        }
    }
}
