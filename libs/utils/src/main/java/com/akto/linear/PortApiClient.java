package com.akto.linear;

import com.akto.linear.LinearApiClient.LinearIssue;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PortApiClient {

    public static final String API_BASE_URL = "https://api.getport.io/v1";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int MAX_POLLS = 60;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String RESULT_SUCCESS = "SUCCESS";

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    public static String fetchAccessToken(String clientId, String clientSecret) throws Exception {
        if (StringUtils.isAnyBlank(clientId, clientSecret)) {
            throw new Exception("Port client ID and client secret are required.");
        }

        BasicDBObject body = new BasicDBObject("clientId", clientId.trim())
            .append("clientSecret", clientSecret.trim());
        BasicDBObject response = call(new Request.Builder()
            .url(API_BASE_URL + "/auth/access_token")
            .post(RequestBody.create(body.toJson(), JSON_MEDIA_TYPE))
            .build());

        String accessToken = response.getString("accessToken");
        if (StringUtils.isBlank(accessToken)) {
            throw new Exception("Port did not return an access token.");
        }
        return accessToken;
    }

    public static LinearIssue createIssue(String accessToken, String workflowId, String teamId, String title,
        String description, int priority) throws Exception {
        BasicDBObject inputs = new BasicDBObject("title", title)
            .append("description", description)
            .append("teamId", teamId)
            .append("priority", priority);

        BasicDBObject started = call(authorized(accessToken, "/workflows/" + workflowId.trim() + "/runs")
            .post(RequestBody.create(new BasicDBObject("inputs", inputs).toJson(), JSON_MEDIA_TYPE))
            .build());
        String runId = asObject(started.get("workflowRun"), "workflowRun").getString("identifier");
        if (StringUtils.isBlank(runId)) {
            throw new Exception("Port did not return a workflow run.");
        }

        for (int poll = 0; poll < MAX_POLLS; poll++) {
            BasicDBObject run = asObject(
                call(authorized(accessToken, "/workflows/runs/" + runId).get().build()).get("workflowRun"),
                "workflowRun");

            if (STATUS_COMPLETED.equals(run.getString("status"))) {
                if (!RESULT_SUCCESS.equals(run.getString("result"))) {
                    throw new Exception("Port workflow run " + runId + " failed: " + failureMessage(run));
                }
                return extractIssue(run);
            }
            pause();
        }

        throw new Exception("Port workflow run " + runId + " did not finish in time.");
    }

    static LinearIssue extractIssue(BasicDBObject run) throws Exception {
        for (Object nodeRun : asList(run.get("nodeRuns"), "nodeRuns")) {
            BasicDBObject issue = findIssue(nodeRun);
            if (issue != null) {
                return new LinearIssue(issue.getString("id"), issue.getString("identifier"),
                    issue.getString("url"));
            }
        }
        throw new Exception("Port workflow finished without returning a Linear issue: " + failureMessage(run));
    }

    static String failureMessage(BasicDBObject run) {
        if (run.get("nodeRuns") instanceof List) {
            for (Object nodeRun : (List<?>) run.get("nodeRuns")) {
                BasicDBObject data = responseData(nodeRun);
                if (data != null && data.get("errors") instanceof List && !((List<?>) data.get("errors")).isEmpty()
                    && ((List<?>) data.get("errors")).get(0) instanceof BasicDBObject) {
                    String message = ((BasicDBObject) ((List<?>) data.get("errors")).get(0)).getString("message");
                    if (StringUtils.isNotBlank(message)) {
                        return message;
                    }
                }
            }
        }
        return "no error details from Port.";
    }

    private static BasicDBObject findIssue(Object nodeRun) {
        BasicDBObject data = responseData(nodeRun);
        if (data == null || !(data.get("data") instanceof BasicDBObject)) {
            return null;
        }
        Object issueCreate = ((BasicDBObject) data.get("data")).get("issueCreate");
        if (!(issueCreate instanceof BasicDBObject) || !((BasicDBObject) issueCreate).getBoolean("success", false)) {
            return null;
        }
        Object issue = ((BasicDBObject) issueCreate).get("issue");
        return issue instanceof BasicDBObject ? (BasicDBObject) issue : null;
    }

    private static BasicDBObject responseData(Object nodeRun) {
        if (!(nodeRun instanceof BasicDBObject) || !(((BasicDBObject) nodeRun).get("output") instanceof BasicDBObject)) {
            return null;
        }
        Object response = ((BasicDBObject) ((BasicDBObject) nodeRun).get("output")).get("response");
        if (!(response instanceof BasicDBObject) || !(((BasicDBObject) response).get("data") instanceof BasicDBObject)) {
            return null;
        }
        return (BasicDBObject) ((BasicDBObject) response).get("data");
    }

    private static Request.Builder authorized(String accessToken, String path) throws Exception {
        if (StringUtils.isBlank(accessToken)) {
            throw new Exception("Port access token is required.");
        }
        return new Request.Builder()
            .url(API_BASE_URL + path)
            .addHeader("Authorization", "Bearer " + accessToken.trim())
            .addHeader("Content-Type", "application/json");
    }

    private static BasicDBObject call(Request request) throws Exception {
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                String retryAfter = response.header("Retry-After");
                throw new Exception("Port rate limit reached."
                    + (retryAfter != null ? " Retry after " + retryAfter + " seconds." : ""));
            }

            BasicDBObject parsed = parse(responseBody);

            if (!response.isSuccessful()) {
                String message = parsed != null ? parsed.getString("message") : null;
                throw new Exception(response.code() == 401 || response.code() == 403
                    ? "Port rejected the credentials" + (message != null ? ": " + message : ".")
                    : "Port API returned HTTP " + response.code() + (message != null ? ": " + message : "."));
            }

            if (parsed == null) {
                throw new Exception("Port API returned an empty response.");
            }
            return parsed;
        }
    }

    private static void pause() throws Exception {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Interrupted while waiting for Port workflow run.");
        }
    }

    private static BasicDBObject parse(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        try {
            return BasicDBObject.parse(responseBody);
        } catch (Exception e) {
            return null;
        }
    }

    private static BasicDBObject asObject(Object value, String field) throws Exception {
        if (!(value instanceof BasicDBObject)) {
            throw new Exception("Unexpected Port response for " + field + ".");
        }
        return (BasicDBObject) value;
    }

    private static List<?> asList(Object value, String field) throws Exception {
        if (!(value instanceof List)) {
            throw new Exception("Unexpected Port response for " + field + ".");
        }
        return (List<?>) value;
    }
}
