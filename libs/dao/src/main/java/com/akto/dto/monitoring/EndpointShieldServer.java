package com.akto.dto.monitoring;

public class EndpointShieldServer {
    public static final String AGENT_ID = "agentId";
    public static final String DEVICE_ID = "deviceId";
    public static final String SERVER_NAME = "serverName";
    public static final String SERVER_URL = "serverUrl";
    public static final String DETECTED = "detected";
    public static final String LAST_SEEN = "lastSeen";
    public static final String COLLECTION_NAME = "collectionName";

    private String agentId;
    private String deviceId;
    private String serverName;
    private String serverUrl;
    private boolean detected;
    private int lastSeen;
    private String collectionName;

    public EndpointShieldServer() {}

    public EndpointShieldServer(String agentId, String deviceId, String serverName, String serverUrl, 
                     boolean detected, int lastSeen, String collectionName) {
        this.agentId = agentId;
        this.deviceId = deviceId;
        this.serverName = serverName;
        this.serverUrl = serverUrl;
        this.detected = detected;
        this.lastSeen = lastSeen;
        this.collectionName = collectionName;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public boolean isDetected() {
        return detected;
    }

    public void setDetected(boolean detected) {
        this.detected = detected;
    }

    public int getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
}