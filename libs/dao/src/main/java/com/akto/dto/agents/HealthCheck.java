package com.akto.dto.agents;

public class HealthCheck {

    /*
     * Random UUID generated on client side.
     */
    String instanceId;
    public static final String INSTANCE_ID = "instanceId";
    // Instance name -> make random english names using word conjunction
    // String instanceName;
    int lastHealthCheckTimestamp;
    public static final String LAST_HEALTH_CHECK_TIMESTAMP = "lastHealthCheckTimestamp";
    /*
     * Eventually version can help with what all agents are supported on a module.
     */
    String version;
    public static final String _VERSION = "version";
    /*
     * If the instance is running a process,
     * processId is filled.
     * Else, empty.
     */
    String processId;
    public static final String PROCESS_ID = "processId";

    // Health check is sent every 2 seconds.
    // So taking 1/10 calls as health alive.
    final public static int HEALTH_CHECK_TIMEOUT = 20;

    public HealthCheck(String instanceId, int lastHealthCheckTimestamp, String version) {
        this.instanceId = instanceId;
        this.lastHealthCheckTimestamp = lastHealthCheckTimestamp;
        this.version = version;
    }

    public HealthCheck() {
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getLastHealthCheckTimestamp() {
        return lastHealthCheckTimestamp;
    }

    public void setLastHealthCheckTimestamp(int lastHealthCheckTimestamp) {
        this.lastHealthCheckTimestamp = lastHealthCheckTimestamp;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

}
