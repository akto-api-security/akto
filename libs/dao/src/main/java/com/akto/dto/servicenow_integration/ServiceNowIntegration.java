package com.akto.dto.servicenow_integration;

import java.util.List;

public class ServiceNowIntegration {

    public static final String INSTANCE_URL = "instanceUrl";
    private String instanceUrl;
    public static final String CLIENT_ID = "clientId";
    private String clientId;
    public static final String CLIENT_SECRET = "clientSecret";
    private String clientSecret;
    public static final String TABLE_NAMES = "tableNames";
    private List<String> tableNames;
    public static final String CREATED_TS = "createdTs";
    private int createdTs;
    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    public ServiceNowIntegration() {}

    public ServiceNowIntegration(String instanceUrl, String clientId, String clientSecret, List<String> tableNames, int createdTs, int updatedTs) {
        this.instanceUrl = instanceUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tableNames = tableNames;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public List<String> getTableNames() {
        return tableNames;
    }

    public void setTableNames(List<String> tableNames) {
        this.tableNames = tableNames;
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
