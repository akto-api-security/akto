package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.LinearIntegrationDao;
import com.akto.dao.LinearProjectDao;
import com.akto.dao.LinearTeamDao;
import com.akto.dao.context.Context;
import com.akto.dto.linear_integration.LinearIntegration;
import com.akto.dto.linear_integration.IssueTemplate;
import com.akto.dto.linear_integration.LinearProject;
import com.akto.dto.linear_integration.LinearTeam;
import com.akto.log.LoggerMaker;
import com.akto.utils.linear.LinearApiClient;
import com.opensymphony.xwork2.Action;

import java.util.List;
import java.util.Map;

public class SaveLinearIntegrationAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(SaveLinearIntegrationAction.class, LoggerMaker.LogDb.DASHBOARD);

    private String workspaceUrl;
    private String apiToken;
    private String defaultProjectId;
    private String defaultTeamId;
    private Map<String, String> severityToPriorityMap;
    private IssueTemplate issueTemplate;

    private LinearIntegration linearIntegration;

    @Override
    public String execute() {
        int userId = getSUser().getId();
        Context.accountId.set(userId);
        Context.userId.set(userId);

        try {
            // Validation
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

            if (defaultProjectId == null || defaultProjectId.trim().isEmpty()) {
                addActionError("Default project is required");
                return Action.ERROR.toUpperCase();
            }

            if (defaultTeamId == null || defaultTeamId.trim().isEmpty()) {
                addActionError("Default team is required");
                return Action.ERROR.toUpperCase();
            }

            // Test connection and fetch teams/projects
            LinearApiClient apiClient = new LinearApiClient();
            boolean isValid = apiClient.validateWorkspace(apiToken);

            if (!isValid) {
                addActionError("Invalid Linear API token or workspace URL");
                return Action.ERROR.toUpperCase();
            }

            // Fetch and cache teams and projects
            List<LinearTeam> teams = apiClient.getTeams(apiToken);
            List<LinearProject> projects = apiClient.getProjects(apiToken);

            // Store API token (encryption handled at database level)
            String encryptedToken = apiToken;

            long currentTs = System.currentTimeMillis() / 1000;

            // Create or update integration
            LinearIntegration newIntegration = new LinearIntegration(
                userId,
                workspaceUrl,
                encryptedToken,
                defaultProjectId,
                defaultTeamId,
                severityToPriorityMap,
                issueTemplate,
                (int) currentTs,
                (int) currentTs
            );

            // Upsert (replace existing or insert new)
            List<LinearIntegration> existing = LinearIntegrationDao.instance.findAll(null);
            if (existing != null && !existing.isEmpty()) {
                // Delete old one and insert new
                LinearIntegrationDao.instance.deleteAll(null);
            }

            LinearIntegrationDao.instance.insertOne(newIntegration);

            // Cache teams and projects (clear old ones first)
            LinearTeamDao.instance.deleteAll(null);
            LinearProjectDao.instance.deleteAll(null);

            for (LinearTeam team : teams) {
                team.setAccountId(userId);
                team.setCreatedTs((int) currentTs);
                LinearTeamDao.instance.insertOne(team);
            }

            for (LinearProject project : projects) {
                project.setAccountId(userId);
                project.setCreatedTs((int) currentTs);
                LinearProjectDao.instance.insertOne(project);
            }

            // Prepare response with masked token
            linearIntegration = new LinearIntegration(
                userId,
                workspaceUrl,
                "****",  // Mask token in response
                defaultProjectId,
                defaultTeamId,
                severityToPriorityMap,
                issueTemplate,
                (int) currentTs,
                (int) currentTs
            );

            logger.infoAndAddToDb(String.format(
                "Linear integration saved by user %d",
                userId
            ));

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error saving Linear integration: " + e.getMessage());
            addActionError("Error saving configuration: " + e.getMessage());
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

    public String getDefaultProjectId() {
        return defaultProjectId;
    }

    public void setDefaultProjectId(String defaultProjectId) {
        this.defaultProjectId = defaultProjectId;
    }

    public String getDefaultTeamId() {
        return defaultTeamId;
    }

    public void setDefaultTeamId(String defaultTeamId) {
        this.defaultTeamId = defaultTeamId;
    }

    public Map<String, String> getSeverityToPriorityMap() {
        return severityToPriorityMap;
    }

    public void setSeverityToPriorityMap(Map<String, String> severityToPriorityMap) {
        this.severityToPriorityMap = severityToPriorityMap;
    }

    public IssueTemplate getIssueTemplate() {
        return issueTemplate;
    }

    public void setIssueTemplate(IssueTemplate issueTemplate) {
        this.issueTemplate = issueTemplate;
    }

    public LinearIntegration getLinearIntegration() {
        return linearIntegration;
    }

    public void setLinearIntegration(LinearIntegration linearIntegration) {
        this.linearIntegration = linearIntegration;
    }
}
