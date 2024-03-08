package com.akto.dto.dependency_flow;

public class ModifyHostDetail {

    private String currentHost;
    public static final String CURRENT_HOST = "currentHost";
    private String newHost;
    public static final String NEW_HOST = "newHost";

    public ModifyHostDetail() { }

    public ModifyHostDetail(String currentHost, String newHost) {
        this.currentHost = currentHost;
        this.newHost = newHost;
    }

    public String getCurrentHost() {
        return currentHost;
    }
    public void setCurrentHost(String currentHost) {
        this.currentHost = currentHost;
    }
    public String getNewHost() {
        return newHost;
    }
    public void setNewHost(String newHost) {
        this.newHost = newHost;
    }

    
}
