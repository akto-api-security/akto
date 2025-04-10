package com.akto.dto.azure_boards_integration;

import java.util.List;
import java.util.Map;

public class AzureBoardsIntegration {

    public static final String BASE_URL = "baseUrl";
    private String baseUrl;
    public static final String ORGANIZATION = "organization";
    private String organization;
    public static final String PERSONAL_AUTH_TOKEN = "personalAuthToken";
    private String personalAuthToken;
    public static final String CREATED_TS = "createdTs";
    private int createdTs;
    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;
    public static final String PROJECT_LIST = "projectList";
    private List<String> projectList;
    public static final String PROJECT_TO_WORK_ITEMS_MAP = "projectToWorkItemsMap";
    private Map<String, List<String>> projectToWorkItemsMap;

    public AzureBoardsIntegration() {}

    public AzureBoardsIntegration(String baseUrl, String organization, String personalAuthToken, int createdTs, int updatedTs, List<String> projectList, Map<String, List<String>> projectToWorkItemsMap) {
        this.baseUrl = baseUrl;
        this.organization = organization;
        this.personalAuthToken = personalAuthToken;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
        this.projectList = projectList;
        this.projectToWorkItemsMap = projectToWorkItemsMap;
    }

    public enum AzureBoardsOperations {
        ADD,
        COPY,
        MOVE,
        REMOVE,
        REPLACE,
        TEST
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getPersonalAuthToken() {
        return personalAuthToken;
    }

    public void setPersonalAuthToken(String personalAuthToken) {
        this.personalAuthToken = personalAuthToken;
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

    public List<String> getProjectList() {
        return projectList;
    }

    public void setProjectList(List<String> projectList) {
        this.projectList = projectList;
    }

    public Map<String, List<String>> getProjectToWorkItemsMap() {
        return projectToWorkItemsMap;
    }

    public void setProjectToWorkItemsMap(Map<String, List<String>> projectToWorkItemsMap) {
        this.projectToWorkItemsMap = projectToWorkItemsMap;
    }
}
