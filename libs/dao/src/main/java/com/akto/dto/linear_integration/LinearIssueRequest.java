package com.akto.dto.linear_integration;

public class LinearIssueRequest {

    private int accountId;
    private String findingType;  // THREAT or TEST
    private String findingId;
    private int apiId;
    private String apiName;
    private String apiMethod;
    private String apiPath;
    private String severityLevel;  // HIGH, MEDIUM, LOW
    private String description;
    private String findingUrl;
    private String requestPayload;
    private String responsePayload;
    private long timestamp;

    public LinearIssueRequest() {
    }

    public LinearIssueRequest(int accountId, String findingType, String findingId, int apiId, String apiName,
                              String apiMethod, String apiPath, String severityLevel, String description,
                              String findingUrl, String requestPayload, String responsePayload, long timestamp) {
        this.accountId = accountId;
        this.findingType = findingType;
        this.findingId = findingId;
        this.apiId = apiId;
        this.apiName = apiName;
        this.apiMethod = apiMethod;
        this.apiPath = apiPath;
        this.severityLevel = severityLevel;
        this.description = description;
        this.findingUrl = findingUrl;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.timestamp = timestamp;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getFindingType() {
        return findingType;
    }

    public void setFindingType(String findingType) {
        this.findingType = findingType;
    }

    public String getFindingId() {
        return findingId;
    }

    public void setFindingId(String findingId) {
        this.findingId = findingId;
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

    public String getApiMethod() {
        return apiMethod;
    }

    public void setApiMethod(String apiMethod) {
        this.apiMethod = apiMethod;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getSeverityLevel() {
        return severityLevel;
    }

    public void setSeverityLevel(String severityLevel) {
        this.severityLevel = severityLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFindingUrl() {
        return findingUrl;
    }

    public void setFindingUrl(String findingUrl) {
        this.findingUrl = findingUrl;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
