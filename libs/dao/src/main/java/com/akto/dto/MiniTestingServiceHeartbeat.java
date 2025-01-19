package com.akto.dto;

public class MiniTestingServiceHeartbeat {

    public static final String MINI_TESTING_SERVICE_NAME = "miniTestingServiceName";
    private String miniTestingServiceName;

    public static final String LAST_HEARTBEAT_TIME_STAMP = "lastHeartbeatTimeStamp";
    private int lastHeartbeatTimestamp;

    public MiniTestingServiceHeartbeat() {}

    public MiniTestingServiceHeartbeat(String miniTestingServiceName, int lastHeartbeatTimestamp) {
        this.miniTestingServiceName = miniTestingServiceName;
        this.lastHeartbeatTimestamp = lastHeartbeatTimestamp;
    }

    public String getMiniTestingServiceName() {
        return miniTestingServiceName;
    }

    public void setMiniTestingServiceName(String miniTestingServiceName) {
        this.miniTestingServiceName = miniTestingServiceName;
    }

    public int getLastHeartbeatTimestamp() {
        return lastHeartbeatTimestamp;
    }

    public void setLastHeartbeatTimestamp(int lastHeartbeatTimestamp) {
        this.lastHeartbeatTimestamp = lastHeartbeatTimestamp;
    }
}
