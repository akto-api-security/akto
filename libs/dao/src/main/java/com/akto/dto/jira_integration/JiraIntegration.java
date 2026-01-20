package com.akto.dto.jira_integration;

import java.util.List;
import java.util.Map;

import com.mongodb.BasicDBObject;

public class JiraIntegration {

    public enum JiraType {
        CLOUD,
        DATA_CENTER
    }

    private String baseUrl;
    private String projId;
    private String userEmail;
    private String apiToken;
    private String issueType;
    private int createdTs;
    private int updatedTs;
    private JiraType jiraType;
    private Map<String,List<BasicDBObject>> projectIdsMap;
    private Map<String, ProjectMapping> projectMappings;
    private Map<String, String> issueSeverityToPriorityMap;

    public static final String API_TOKEN = "apiToken";
    public static final String ISSUE_SEVERITY_TO_PRIORITY_MAP = "issueSeverityToPriorityMap";
    public static final String JIRA_TYPE = "jiraType";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProjId() {
        return projId;
    }

    public void setProjId(String projId) {
        this.projId = projId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
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

    public Map<String, List<BasicDBObject>> getProjectIdsMap() {
        return projectIdsMap;
    }

    public void setProjectIdsMap(Map<String, List<BasicDBObject>> projectIdsMap) {
        this.projectIdsMap = projectIdsMap;
    }

    public Map<String, ProjectMapping> getProjectMappings() {
        return projectMappings;
    }

    public void setProjectMappings(Map<String, ProjectMapping> projectMappings) {
        this.projectMappings = projectMappings;
    }

    public Map<String, String> getIssueSeverityToPriorityMap() {
        return issueSeverityToPriorityMap;
    }

    public void setIssueSeverityToPriorityMap(Map<String, String> issueSeverityToPriorityMap) {
        this.issueSeverityToPriorityMap = issueSeverityToPriorityMap;
    }

    public JiraType getJiraType() {
        return jiraType;
    }

    public void setJiraType(JiraType jiraType) {
        this.jiraType = jiraType;
    }

}
