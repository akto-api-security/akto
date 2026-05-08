package com.akto.dto.monitoring;

import com.akto.dto.Log;

public class EndpointShieldLog extends Log {
    public static final String AGENT_ID = "agentId";
    public static final String DEVICE_ID = "deviceId";
    public static final String LEVEL = "level";
    
    private String agentId;
    private String deviceId;
    private String level;

    public EndpointShieldLog() {
        super();
    }

    public EndpointShieldLog(String log, String key, int timestamp, String agentId, String deviceId, String level) {
        super(log, key, timestamp);
        this.agentId = agentId;
        this.deviceId = deviceId;
        this.level = level;
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

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}