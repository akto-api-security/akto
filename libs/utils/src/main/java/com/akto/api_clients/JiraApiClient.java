package com.akto.api_clients;

import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.JsonUtils;
import com.mongodb.BasicDBObject;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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

    private static String getBasicAuthHeaders(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}
