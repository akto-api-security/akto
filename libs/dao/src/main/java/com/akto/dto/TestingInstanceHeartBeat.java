package com.akto.dto;

public class TestingInstanceHeartBeat {
    
    private int ts;
    private String instanceId;

    public TestingInstanceHeartBeat() {
    }

    public TestingInstanceHeartBeat(String instanceId, int ts) {
        this.instanceId = instanceId;
        this.ts = ts;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getTs() {
        return ts;
    }

    public void setTs(int ts) {
        this.ts = ts;
    }

}

