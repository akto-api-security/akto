package com.akto.dto.linear_integration;

import org.bson.types.ObjectId;

public class LinearProject {

    public static final String ID = "_id";
    private ObjectId id;

    public static final String ACCOUNT_ID = "accountId";
    private int accountId;

    public static final String PROJECT_ID = "projectId";
    private String projectId;

    public static final String PROJECT_NAME = "projectName";
    private String projectName;

    public static final String PROJECT_KEY = "projectKey";
    private String projectKey;

    public static final String TEAM_ID = "teamId";
    private String teamId;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public LinearProject() {
    }

    public LinearProject(int accountId, String projectId, String projectName, String projectKey,
                         String teamId, int createdTs) {
        this.id = new ObjectId();
        this.accountId = accountId;
        this.projectId = projectId;
        this.projectName = projectName;
        this.projectKey = projectKey;
        this.teamId = teamId;
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }
}
