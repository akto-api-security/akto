package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dto.linear_integration.LinearProject;
import com.akto.dto.linear_integration.LinearTeam;
import com.akto.log.LoggerMaker;
import com.akto.utils.linear.LinearApiClient;
import com.opensymphony.xwork2.Action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestLinearConnectionAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(TestLinearConnectionAction.class, LoggerMaker.LogDb.DASHBOARD);

    private String workspaceUrl;
    private String apiToken;

    private Map<String, Object> testResult;

    @Override
    public String execute() {
        int userId = getSUser().getId();
        Context.accountId.set(userId);

        try {
            if (workspaceUrl == null || workspaceUrl.trim().isEmpty()) {
                addActionError("Workspace URL is required");
                return Action.ERROR.toUpperCase();
            }

            if (apiToken == null || apiToken.trim().isEmpty()) {
                addActionError("API token is required");
                return Action.ERROR.toUpperCase();
            }

            if (!workspaceUrl.matches("^https://app\\.linear\\.app$")) {
                addActionError("Invalid workspace URL format. Expected: https://app.linear.app");
                return Action.ERROR.toUpperCase();
            }

            // Call Linear API to validate and fetch teams/projects
            LinearApiClient apiClient = new LinearApiClient();
            boolean isValid = apiClient.validateWorkspace(apiToken);

            if (!isValid) {
                addActionError("Invalid Linear API token or workspace URL. Please verify your credentials.");
                return Action.ERROR.toUpperCase();
            }

            // Fetch teams and projects
            List<LinearTeam> teams = apiClient.getTeams(apiToken);
            List<LinearProject> projects = apiClient.getProjects(apiToken);

            // Build response
            testResult = new HashMap<>();
            testResult.put("success", true);
            testResult.put("message", "Connection successful");

            // Convert teams to response format
            List<Map<String, String>> teamsResponse = new ArrayList<>();
            for (LinearTeam team : teams) {
                Map<String, String> teamMap = new HashMap<>();
                teamMap.put("teamId", team.getTeamId());
                teamMap.put("teamName", team.getTeamName());
                teamMap.put("teamKey", team.getTeamKey());
                teamsResponse.add(teamMap);
            }
            testResult.put("teams", teamsResponse);

            // Convert projects to response format
            List<Map<String, String>> projectsResponse = new ArrayList<>();
            for (LinearProject project : projects) {
                Map<String, String> projectMap = new HashMap<>();
                projectMap.put("projectId", project.getProjectId());
                projectMap.put("projectName", project.getProjectName());
                projectMap.put("projectKey", project.getProjectKey());
                projectMap.put("teamId", project.getTeamId());
                projectsResponse.add(projectMap);
            }
            testResult.put("projects", projectsResponse);

            logger.infoAndAddToDb(String.format(
                "Linear connection test successful: %d teams, %d projects",
                teams.size(), projects.size()
            ));

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error testing Linear connection: " + e.getMessage());
            addActionError("Error testing connection: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String getWorkspaceUrl() {
        return workspaceUrl;
    }

    public void setWorkspaceUrl(String workspaceUrl) {
        this.workspaceUrl = workspaceUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public Map<String, Object> getTestResult() {
        return testResult;
    }

    public void setTestResult(Map<String, Object> testResult) {
        this.testResult = testResult;
    }
}
