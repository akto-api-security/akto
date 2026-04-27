package com.akto.utils.linear;

import com.akto.dto.linear_integration.LinearProject;
import com.akto.dto.linear_integration.LinearTeam;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LinearApiClient {

    private static final LoggerMaker logger = new LoggerMaker(LinearApiClient.class, LoggerMaker.LogDb.DASHBOARD);
    private static final String LINEAR_API_URL = "https://api.linear.app/graphql";
    private static final int API_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LinearApiClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates workspace and credentials by querying Linear API
     */
    public boolean validateWorkspace(String apiToken) throws Exception {
        String query = "{ viewer { id } }";
        JsonNode response = executeGraphQLQuery(apiToken, query);

        if (response == null) {
            return false;
        }

        JsonNode errors = response.get("errors");
        if (errors != null && errors.isArray() && errors.size() > 0) {
            logger.errorAndAddToDb("Linear API validation failed: " + errors.get(0).get("message"));
            return false;
        }

        JsonNode data = response.get("data");
        return data != null && data.get("viewer") != null;
    }

    /**
     * Fetches teams from Linear workspace
     */
    public List<LinearTeam> getTeams(String apiToken) throws Exception {
        List<LinearTeam> teams = new ArrayList<>();

        String query = "{ teams(first: 100) { nodes { id name } } }";
        JsonNode response = executeGraphQLQuery(apiToken, query);

        if (response == null) {
            return teams;
        }

        JsonNode errors = response.get("errors");
        if (errors != null && errors.isArray() && errors.size() > 0) {
            logger.errorAndAddToDb("Failed to fetch teams: " + errors.get(0).get("message"));
            return teams;
        }

        JsonNode data = response.get("data");
        if (data != null && data.get("teams") != null) {
            JsonNode nodes = data.get("teams").get("nodes");
            if (nodes != null && nodes.isArray()) {
                for (JsonNode node : nodes) {
                    LinearTeam team = new LinearTeam();
                    team.setTeamId(node.get("id").asText());
                    team.setTeamName(node.get("name").asText());
                    team.setTeamKey(node.get("id").asText()); // Use team ID as key
                    teams.add(team);
                }
            }
        }

        return teams;
    }

    /**
     * Fetches projects from Linear workspace
     */
    public List<LinearProject> getProjects(String apiToken) throws Exception {
        List<LinearProject> projects = new ArrayList<>();

        String query = "{ projects(first: 100) { nodes { id name teams(first: 1) { nodes { id } } } } }";
        JsonNode response = executeGraphQLQuery(apiToken, query);

        if (response == null) {
            return projects;
        }

        JsonNode errors = response.get("errors");
        if (errors != null && errors.isArray() && errors.size() > 0) {
            logger.errorAndAddToDb("Failed to fetch projects: " + errors.get(0).get("message"));
            return projects;
        }

        JsonNode data = response.get("data");
        if (data != null && data.get("projects") != null) {
            JsonNode nodes = data.get("projects").get("nodes");
            if (nodes != null && nodes.isArray()) {
                for (JsonNode node : nodes) {
                    LinearProject project = new LinearProject();
                    project.setProjectId(node.get("id").asText());
                    project.setProjectName(node.get("name").asText());
                    project.setProjectKey(node.get("id").asText()); // Use project ID as key
                    JsonNode teamsNode = node.get("teams");
                    if (teamsNode != null && teamsNode.get("nodes") != null) {
                        JsonNode teamNodesArray = teamsNode.get("nodes");
                        if (teamNodesArray.isArray() && teamNodesArray.size() > 0) {
                            project.setTeamId(teamNodesArray.get(0).get("id").asText());
                        }
                    }
                    projects.add(project);
                }
            }
        }

        return projects;
    }

    /**
     * Creates an issue in Linear
     *
     * @param apiToken Linear API token
     * @param teamId Team ID where issue should be created
     * @param projectId Project ID (optional)
     * @param title Issue title
     * @param description Issue description
     * @param priority Priority (urgent, high, medium, low)
     * @param labels Labels to apply
     * @return Map with linearIssueId, linearIssueKey, linearIssueUrl
     */
    public Map<String, String> createIssue(String apiToken, String teamId, String projectId,
                                           String title, String description, String priority,
                                           List<String> labels) throws Exception {
        Map<String, String> result = new HashMap<>();

        // Build GraphQL mutation
        StringBuilder labelsJson = new StringBuilder();
        if (labels != null && !labels.isEmpty()) {
            labelsJson.append("[");
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) labelsJson.append(",");
                labelsJson.append("\\\"").append(labels.get(i)).append("\\\"");
            }
            labelsJson.append("]");
        }

        String mutation = String.format(
            "mutation { issueCreate(input: { teamId: \\\"%s\\\", projectId: \\\"%s\\\", " +
            "title: \\\"%s\\\", description: \\\"%s\\\", priority: %s, labels: %s }) " +
            "{ issue { id identifier url } } }",
            escapeGraphQLString(teamId),
            projectId != null ? escapeGraphQLString(projectId) : "null",
            escapeGraphQLString(title),
            escapeGraphQLString(description),
            priority != null ? getPriorityValue(priority) : "3",
            labelsJson.length() > 0 ? labelsJson.toString() : "null"
        );

        JsonNode response = executeGraphQLQueryWithRetry(apiToken, mutation);

        if (response == null) {
            throw new RuntimeException("No response from Linear API");
        }

        JsonNode errors = response.get("errors");
        if (errors != null && errors.isArray() && errors.size() > 0) {
            String errorMessage = errors.get(0).get("message").asText();
            throw new RuntimeException("Linear API error: " + errorMessage);
        }

        JsonNode data = response.get("data");
        if (data != null && data.get("issueCreate") != null) {
            JsonNode issue = data.get("issueCreate").get("issue");
            if (issue != null) {
                result.put("linearIssueId", issue.get("id").asText());
                result.put("linearIssueKey", issue.get("identifier").asText());
                result.put("linearIssueUrl", issue.get("url").asText());
                return result;
            }
        }

        throw new RuntimeException("Failed to create issue in Linear");
    }

    /**
     * Execute GraphQL query with retry logic for transient errors
     */
    private JsonNode executeGraphQLQueryWithRetry(String apiToken, String query) throws Exception {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return executeGraphQLQuery(apiToken, query);
            } catch (Exception e) {
                String errorMsg = e.getMessage();

                // Check if this is a permanent error (don't retry)
                if (errorMsg != null && (errorMsg.contains("401") || errorMsg.contains("403"))) {
                    throw e;
                }

                // For transient errors, backoff and retry
                if (attempt < MAX_RETRIES - 1) {
                    long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt);
                    logger.infoAndAddToDb(String.format(
                        "Transient error calling Linear API (attempt %d/%d), backing off %dms: %s",
                        attempt + 1, MAX_RETRIES, backoffMs, errorMsg
                    ));
                    Thread.sleep(backoffMs);
                } else {
                    // Final attempt failed
                    throw e;
                }
            }
        }

        throw new RuntimeException("All retry attempts exhausted for Linear API call");
    }

    /**
     * Execute GraphQL query against Linear API
     */
    private JsonNode executeGraphQLQuery(String apiToken, String query) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(payload),
            MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
            .url(LINEAR_API_URL)
            .addHeader("Authorization", apiToken)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                logger.errorAndAddToDb(String.format(
                    "Linear API HTTP error %d: %s",
                    response.code(), errorBody
                ));
                throw new RuntimeException(String.format(
                    "Linear API HTTP %d: %s",
                    response.code(),
                    response.message()
                ));
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (responseBody.isEmpty()) {
                throw new RuntimeException("Empty response from Linear API");
            }

            return objectMapper.readTree(responseBody);
        }
    }

    /**
     * Convert priority name to Linear priority value
     * Linear uses: 0 (no priority), 1 (urgent), 2 (high), 3 (medium), 4 (low)
     */
    private int getPriorityValue(String priority) {
        if (priority == null) return 3; // default to medium

        switch (priority.toLowerCase()) {
            case "urgent":
                return 1;
            case "high":
                return 2;
            case "medium":
                return 3;
            case "low":
                return 4;
            default:
                return 3;
        }
    }

    /**
     * Escape special characters in GraphQL strings
     */
    private String escapeGraphQLString(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
