package com.akto.dto;

public class JiraIntegration {
    
    private String baseUrl;
    private String projId;
    private String userEmail;
    private String apiToken;
    private String issueType;
    private int createdTs; 
    private int updatedTs;

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

}
