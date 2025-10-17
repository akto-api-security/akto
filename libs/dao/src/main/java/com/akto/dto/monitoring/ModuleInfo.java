package com.akto.dto.monitoring;

import java.util.Map;
import java.util.UUID;

public class ModuleInfo {
    public static final String MODULE_TYPE = "moduleType";
    private ModuleType moduleType;
    private String currentVersion;
    private String id;//UUID
    private int startedTs;
    public static final String LAST_HEARTBEAT_RECEIVED = "lastHeartbeatReceived";
    private int lastHeartbeatReceived;
    private String name;
    public static final String ADDITIONAL_DATA = "additionalData";
    private Map<String, Object> additionalData;

    public ModuleType getModuleType() {
        return moduleType;
    }

    public void setModuleType(ModuleType moduleType) {
        this.moduleType = moduleType;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public int getStartedTs() {
        return startedTs;
    }

    public void setStartedTs(int startedTs) {
        this.startedTs = startedTs;
    }

    public String getId() {
        if (id == null) {
            return UUID.randomUUID().toString();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getLastHeartbeatReceived() {
        return lastHeartbeatReceived;
    }

    public void setLastHeartbeatReceived(int lastHeartbeatReceived) {
        this.lastHeartbeatReceived = lastHeartbeatReceived;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    public enum ModuleType {
        MINI_RUNTIME,
        MINI_TESTING,
        K8S,
        EBPF,
        THREAT_DETECTION,
        MCP_ENDPOINT_SHIELD,
        DATA_INGESTION
    }

}
