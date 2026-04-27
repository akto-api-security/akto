package com.akto.dto.linear_integration;

import org.bson.types.ObjectId;

public class LinearIssueMapping {

    public static final String ID = "_id";
    private ObjectId id;

    public static final String ACCOUNT_ID = "accountId";
    private int accountId;

    public static final String FINDING_ID = "findingId";
    private String findingId;

    public static final String FINDING_TYPE = "findingType";
    private String findingType;  // THREAT or TEST

    public static final String LINEAR_ISSUE_ID = "linearIssueId";
    private String linearIssueId;

    public static final String LINEAR_ISSUE_KEY = "linearIssueKey";
    private String linearIssueKey;

    public static final String LINEAR_ISSUE_URL = "linearIssueUrl";
    private String linearIssueUrl;

    public static final String API_ID = "apiId";
    private int apiId;

    public static final String API_NAME = "apiName";
    private String apiName;

    public static final String SEVERITY = "severity";
    private String severity;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public LinearIssueMapping() {
    }

    public LinearIssueMapping(int accountId, String findingId, String findingType, String linearIssueId,
                              String linearIssueKey, String linearIssueUrl, int apiId, String apiName,
                              String severity, int createdTs) {
        this.id = new ObjectId();
        this.accountId = accountId;
        this.findingId = findingId;
        this.findingType = findingType;
        this.linearIssueId = linearIssueId;
        this.linearIssueKey = linearIssueKey;
        this.linearIssueUrl = linearIssueUrl;
        this.apiId = apiId;
        this.apiName = apiName;
        this.severity = severity;
        this.createdTs = createdTs;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getFindingId() {
        return findingId;
    }

    public void setFindingId(String findingId) {
        this.findingId = findingId;
    }

    public String getFindingType() {
        return findingType;
    }

    public void setFindingType(String findingType) {
        this.findingType = findingType;
    }

    public String getLinearIssueId() {
        return linearIssueId;
    }

    public void setLinearIssueId(String linearIssueId) {
        this.linearIssueId = linearIssueId;
    }

    public String getLinearIssueKey() {
        return linearIssueKey;
    }

    public void setLinearIssueKey(String linearIssueKey) {
        this.linearIssueKey = linearIssueKey;
    }

    public String getLinearIssueUrl() {
        return linearIssueUrl;
    }

    public void setLinearIssueUrl(String linearIssueUrl) {
        this.linearIssueUrl = linearIssueUrl;
    }

    public int getApiId() {
        return apiId;
    }

    public void setApiId(int apiId) {
        this.apiId = apiId;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }
}
