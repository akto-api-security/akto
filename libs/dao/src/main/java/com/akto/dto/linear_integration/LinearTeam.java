package com.akto.dto.linear_integration;

import org.bson.types.ObjectId;

public class LinearTeam {

    public static final String ID = "_id";
    private ObjectId id;

    public static final String ACCOUNT_ID = "accountId";
    private int accountId;

    public static final String TEAM_ID = "teamId";
    private String teamId;

    public static final String TEAM_NAME = "teamName";
    private String teamName;

    public static final String TEAM_KEY = "teamKey";
    private String teamKey;

    public static final String CREATED_TS = "createdTs";
    private int createdTs;

    public LinearTeam() {
    }

    public LinearTeam(int accountId, String teamId, String teamName, String teamKey, int createdTs) {
        this.id = new ObjectId();
        this.accountId = accountId;
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamKey = teamKey;
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

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamKey() {
        return teamKey;
    }

    public void setTeamKey(String teamKey) {
        this.teamKey = teamKey;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }
}
