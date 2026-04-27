package com.akto.dto.linear_integration;

import java.util.Map;

public class LinearIntegration {

    public static final String ACCOUNT_ID = "accountId";
    private int accountId;

    public static final String WORKSPACE_URL = "workspaceUrl";
    private String workspaceUrl;

    public static final String API_KEY = "apiKey";
    private String apiKey;

    public static final String DEFAULT_PROJECT_ID = "defaultProjectId";
    private String defaultProjectId;

    public static final String DEFAULT_TEAM_ID = "defaultTeamId";
    private String defaultTeamId;

    public static final String SEVERITY_TO_PRIORITY_MAP = "severityToPriorityMap";
    private Map<String, String> severityToPriorityMap;

    public static final String ISSUE_TEMPLATE = "issueTemplate";
    private IssueTemplate issueTemplate;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    public LinearIntegration() {
    }

    public LinearIntegration(int accountId, String workspaceUrl, String apiKey, String defaultProjectId,
                             String defaultTeamId, Map<String, String> severityToPriorityMap,
                             IssueTemplate issueTemplate, int createdTs, int updatedTs) {
        this.accountId = accountId;
        this.workspaceUrl = workspaceUrl;
        this.apiKey = apiKey;
        this.defaultProjectId = defaultProjectId;
        this.defaultTeamId = defaultTeamId;
        this.severityToPriorityMap = severityToPriorityMap;
        this.issueTemplate = issueTemplate;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getWorkspaceUrl() {
        return workspaceUrl;
    }

    public void setWorkspaceUrl(String workspaceUrl) {
        this.workspaceUrl = workspaceUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }

    public int getUpdatedTs() {
        return updatedTs;
    }

    public void setUpdatedTs(int updatedTs) {
        this.updatedTs = updatedTs;
    }
}
