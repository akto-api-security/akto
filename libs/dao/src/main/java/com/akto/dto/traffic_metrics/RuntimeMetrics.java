package com.akto.dto.traffic_metrics;

public class RuntimeMetrics {
    
    private String name;
    private int timestamp;
    private String instanceId;
    private Double val;

    public RuntimeMetrics() {
    }

    public RuntimeMetrics(String name, int timestamp, String instanceId, Double val) {
        this.name = name;
        this.timestamp = timestamp;
        this.instanceId = instanceId;
        this.val = val;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Double getVal() {
        return val;
    }

    public void setVal(Double val) {
        this.val = val;
    }

}
