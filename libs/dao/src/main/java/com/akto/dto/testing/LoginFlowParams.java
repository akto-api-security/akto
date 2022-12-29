package com.akto.dto.testing;

public class LoginFlowParams {
    
    private int userId;
    private Boolean fetchValueMap;
    private String nodeId;

    public LoginFlowParams() { }

    public LoginFlowParams(int userId, Boolean fetchValueMap, String nodeId) {
        this.userId = userId;
        this.fetchValueMap = fetchValueMap;
        this.nodeId = nodeId;
    }

    public int getUserId() {
        return this.userId;
    }

    public Boolean getFetchValueMap() {
        return this.fetchValueMap;
    }

    public String getNodeId() {
        return this.nodeId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setFetchValueMap(Boolean fetchValueMap) {
        this.fetchValueMap = fetchValueMap;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
