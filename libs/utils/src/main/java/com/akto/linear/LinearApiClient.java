package com.akto.linear;

import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinearApiClient {

    public static final String GRAPHQL_URL = "https://api.linear.app/graphql";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final int TEAMS_PAGE_SIZE = 100;
    private static final int MAX_TEAM_PAGES = 20;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    private static final String TEAMS_QUERY =
        "query Teams($first: Int!, $after: String) { teams(first: $first, after: $after) { nodes { id key name } pageInfo { hasNextPage endCursor } } }";
    private static final String TEAM_CREATE_MUTATION =
        "mutation TeamCreate($input: TeamCreateInput!) { teamCreate(input: $input) { success team { id key name } } }";
    private static final String ISSUE_CREATE_MUTATION =
        "mutation IssueCreate($input: IssueCreateInput!) { issueCreate(input: $input) { success issue { id identifier url } } }";
    private static final String ISSUES_UPDATED_QUERY =
        "query IssuesUpdated($first: Int!, $after: String, $filter: IssueFilter) { issues(first: $first, after: $after, filter: $filter) { nodes { id identifier updatedAt state { id type } team { id } } pageInfo { hasNextPage endCursor } } }";
    private static final String ISSUE_QUERY =
        "query IssueByIdentifier($id: String!) { issue(id: $id) { id identifier updatedAt state { id type } team { id } } }";
    private static final String TEAM_STATES_QUERY =
        "query TeamStates($id: String!) { team(id: $id) { states { nodes { id type position } } } }";
    private static final String ISSUE_UPDATE_MUTATION =
        "mutation IssueUpdate($id: String!, $input: IssueUpdateInput!) { issueUpdate(id: $id, input: $input) { success } }";
    private static final int ISSUES_PAGE_SIZE = 100;
    private static final int MAX_ISSUE_PAGES = 50;
    private static final String ATTACHMENT_CREATE_MUTATION =
        "mutation AttachmentCreate($input: AttachmentCreateInput!) { attachmentCreate(input: $input) { success attachment { id } } }";

    @Getter
    @AllArgsConstructor
    public static class LinearTeam {
        private final String id;
        private final String key;
        private final String name;

        public String getDisplayName() {
            return name + " (" + key + ")";
        }
    }

    @Getter
    @AllArgsConstructor
    public static class LinearIssue {
        private final String id;
        private final String identifier;
        private final String url;
    }

    @Getter
    @AllArgsConstructor
    public static class LinearIssueState {
        private final String id;
        private final String identifier;
        private final int updatedAt;
        private final String stateType;
        private final String teamId;
    }

    @Getter
    @AllArgsConstructor
    public static class LinearWorkflowState {
        private final String id;
        private final String type;
        private final double position;
    }

    public static List<LinearTeam> fetchTeams(String token) throws Exception {
        List<LinearTeam> teams = new ArrayList<>();
        String cursor = null;

        for (int page = 0; page < MAX_TEAM_PAGES; page++) {
            BasicDBObject variables = new BasicDBObject("first", TEAMS_PAGE_SIZE).append("after", cursor);
            BasicDBObject connection = asObject(execute(token, TEAMS_QUERY, variables).get("teams"), "teams");

            for (Object node : asList(connection.get("nodes"), "teams")) {
                teams.add(toTeam(asObject(node, "team")));
            }

            BasicDBObject pageInfo = asObject(connection.get("pageInfo"), "pageInfo");
            if (!pageInfo.getBoolean("hasNextPage", false)) {
                break;
            }
            cursor = pageInfo.getString("endCursor");
        }

        return teams;
    }

    public static LinearTeam createTeam(String token, String name, String key) throws Exception {
        BasicDBObject input = new BasicDBObject("name", name);
        if (StringUtils.isNotBlank(key)) {
            input.append("key", key);
        }

        BasicDBObject payload = mutationPayload(
            execute(token, TEAM_CREATE_MUTATION, new BasicDBObject("input", input)), "teamCreate");
        return toTeam(asObject(payload.get("team"), "team"));
    }

    public static LinearIssue createIssue(String token, String teamId, String title, String description,
        int priority, String stateId) throws Exception {
        BasicDBObject input = new BasicDBObject("teamId", teamId)
            .append("title", title)
            .append("description", description)
            .append("priority", priority);
        if (StringUtils.isNotBlank(stateId)) {
            input.append("stateId", stateId);
        }

        BasicDBObject payload = mutationPayload(
            execute(token, ISSUE_CREATE_MUTATION, new BasicDBObject("input", input)), "issueCreate");
        BasicDBObject issue = asObject(payload.get("issue"), "issue");
        return new LinearIssue(issue.getString("id"), issue.getString("identifier"), issue.getString("url"));
    }

    public static List<LinearIssueState> fetchIssuesUpdatedAfter(String token, int sinceEpochSeconds,
        List<String> teamIds) throws Exception {
        List<LinearIssueState> issues = new ArrayList<>();
        if (teamIds == null || teamIds.isEmpty()) {
            return issues;
        }

        BasicDBObject filter = new BasicDBObject("updatedAt",
            new BasicDBObject("gt", Instant.ofEpochSecond(sinceEpochSeconds).toString()))
            .append("team", new BasicDBObject("id", new BasicDBObject("in", teamIds)));
        String cursor = null;

        for (int page = 0; page < MAX_ISSUE_PAGES; page++) {
            BasicDBObject variables = new BasicDBObject("first", ISSUES_PAGE_SIZE)
                .append("after", cursor)
                .append("filter", filter);
            BasicDBObject connection = asObject(execute(token, ISSUES_UPDATED_QUERY, variables).get("issues"), "issues");

            for (Object node : asList(connection.get("nodes"), "issues")) {
                issues.add(toIssueState(asObject(node, "issue")));
            }

            BasicDBObject pageInfo = asObject(connection.get("pageInfo"), "pageInfo");
            if (!pageInfo.getBoolean("hasNextPage", false)) {
                break;
            }
            cursor = pageInfo.getString("endCursor");
        }

        return issues;
    }

    public static LinearIssueState fetchIssue(String token, String identifier) throws Exception {
        return toIssueState(asObject(
            execute(token, ISSUE_QUERY, new BasicDBObject("id", identifier)).get("issue"), "issue"));
    }

    public static List<LinearWorkflowState> fetchTeamStates(String token, String teamId) throws Exception {
        BasicDBObject team = asObject(
            execute(token, TEAM_STATES_QUERY, new BasicDBObject("id", teamId)).get("team"), "team");
        BasicDBObject states = asObject(team.get("states"), "states");

        List<LinearWorkflowState> workflowStates = new ArrayList<>();
        for (Object node : asList(states.get("nodes"), "states")) {
            BasicDBObject state = asObject(node, "state");
            Number position = state.get("position") instanceof Number ? (Number) state.get("position") : 0;
            workflowStates.add(new LinearWorkflowState(state.getString("id"), state.getString("type"),
                position.doubleValue()));
        }
        return workflowStates;
    }

    public static void updateIssueState(String token, String issueId, String stateId) throws Exception {
        BasicDBObject variables = new BasicDBObject("id", issueId)
            .append("input", new BasicDBObject("stateId", stateId));
        mutationPayload(execute(token, ISSUE_UPDATE_MUTATION, variables), "issueUpdate");
    }

    private static LinearIssueState toIssueState(BasicDBObject issue) {
        BasicDBObject state = issue.get("state") instanceof BasicDBObject ? (BasicDBObject) issue.get("state") : null;
        BasicDBObject team = issue.get("team") instanceof BasicDBObject ? (BasicDBObject) issue.get("team") : null;
        String updatedAt = issue.getString("updatedAt");
        return new LinearIssueState(issue.getString("id"), issue.getString("identifier"),
            StringUtils.isBlank(updatedAt) ? 0 : (int) Instant.parse(updatedAt).getEpochSecond(),
            state != null ? state.getString("type") : null,
            team != null ? team.getString("id") : null);
    }

    public static void createAttachment(String token, String issueId, String title, String subtitle, String url,
        BasicDBObject metadata) throws Exception {
        BasicDBObject input = new BasicDBObject("issueId", issueId)
            .append("title", title)
            .append("url", url);
        if (StringUtils.isNotBlank(subtitle)) {
            input.append("subtitle", subtitle);
        }
        if (metadata != null && !metadata.isEmpty()) {
            input.append("metadata", metadata);
        }

        mutationPayload(execute(token, ATTACHMENT_CREATE_MUTATION, new BasicDBObject("input", input)),
            "attachmentCreate");
    }

    private static BasicDBObject execute(String token, String query, BasicDBObject variables) throws Exception {
        if (StringUtils.isBlank(token)) {
            throw new Exception("Linear API key is required.");
        }

        BasicDBObject body = new BasicDBObject("query", query).append("variables", variables);
        Request request = new Request.Builder()
            .url(GRAPHQL_URL)
            .post(RequestBody.create(body.toJson(), JSON_MEDIA_TYPE))
            .addHeader("Authorization", token.trim())
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                String retryAfter = response.header("Retry-After");
                throw new Exception("Linear rate limit reached."
                    + (retryAfter != null ? " Retry after " + retryAfter + " seconds." : ""));
            }

            BasicDBObject parsed = parse(responseBody);

            String error = firstError(parsed);
            if (error != null) {
                throw new Exception(error);
            }

            if (!response.isSuccessful()) {
                throw new Exception(response.code() == 401 || response.code() == 403
                    ? "Linear rejected the API key."
                    : "Linear API returned HTTP " + response.code() + ".");
            }

            if (parsed == null || !(parsed.get("data") instanceof BasicDBObject)) {
                throw new Exception("Linear API returned an empty response.");
            }

            return (BasicDBObject) parsed.get("data");
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

    private static String firstError(BasicDBObject parsed) {
        if (parsed == null || !(parsed.get("errors") instanceof List)) {
            return null;
        }
        List<?> errors = (List<?>) parsed.get("errors");
        if (errors.isEmpty() || !(errors.get(0) instanceof BasicDBObject)) {
            return null;
        }
        BasicDBObject first = (BasicDBObject) errors.get(0);
        BasicDBObject extensions = first.get("extensions") instanceof BasicDBObject
            ? (BasicDBObject) first.get("extensions")
            : null;
        String presentable = extensions != null ? extensions.getString("userPresentableMessage") : null;
        return StringUtils.firstNonBlank(presentable, first.getString("message"), "Linear API returned an error.");
    }

    private static BasicDBObject mutationPayload(BasicDBObject data, String mutationName) throws Exception {
        BasicDBObject payload = asObject(data.get(mutationName), mutationName);
        if (!payload.getBoolean("success", false)) {
            throw new Exception("Linear did not complete " + mutationName + ".");
        }
        return payload;
    }

    private static LinearTeam toTeam(BasicDBObject team) {
        return new LinearTeam(team.getString("id"), team.getString("key"), team.getString("name"));
    }

    private static BasicDBObject asObject(Object value, String field) throws Exception {
        if (!(value instanceof BasicDBObject)) {
            throw new Exception("Unexpected Linear response for " + field + ".");
        }
        return (BasicDBObject) value;
    }

    private static List<?> asList(Object value, String field) throws Exception {
        if (!(value instanceof List)) {
            throw new Exception("Unexpected Linear response for " + field + ".");
        }
        return (List<?>) value;
    }
}
