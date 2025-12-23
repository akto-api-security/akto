package com.akto.dto.monitoring;

import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

public class ModuleInfo {

    public static final String MODULE_TYPE = "moduleType";
    private ModuleType moduleType;
    public static final String CURRENT_VERSION = "currentVersion";
    private String currentVersion;
    private String id;//UUID
    public static final String STARTED_TS = "startedTs";
    private int startedTs;
    public static final String LAST_HEARTBEAT_RECEIVED = "lastHeartbeatReceived";
    private int lastHeartbeatReceived;
    public static final String NAME = "name";
    private String name;
    public static final String ADDITIONAL_DATA = "additionalData";
    private Map<String, Object> additionalData;

    @Getter @Setter
    private boolean reboot;
    public static final String _REBOOT = "reboot";

    @Getter @Setter
    private boolean rebootContainer;
    public static final String _REBOOT_CONTAINER = "rebootContainer";

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
