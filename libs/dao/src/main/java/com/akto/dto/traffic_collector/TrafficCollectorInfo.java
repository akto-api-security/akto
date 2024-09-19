package com.akto.dto.traffic_collector;

public class TrafficCollectorInfo {

    private String id;
    public static final String RUNTIME_ID = "runtimeId";
    private String runtimeId;
    public static final String START_TIME = "startTime";
    private int startTime;
    public static final String  LAST_HEARTBEAT = "lastHeartbeat";
    private int lastHeartbeat;
    public static final String VERSION = "version";
    private String version;

    public TrafficCollectorInfo() {}

    public TrafficCollectorInfo(String id, String runtimeId, int startTime, int lastHeartbeat, String version) {
        this.id = id;
        this.runtimeId = runtimeId;
        this.startTime = startTime;
        this.lastHeartbeat = lastHeartbeat;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(int lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}