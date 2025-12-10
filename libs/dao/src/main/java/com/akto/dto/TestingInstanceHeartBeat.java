package com.akto.dto;

public class TestingInstanceHeartBeat {
    
    public static final String INSTANCE_ID = "instanceId";
    public static final String TS = "ts";
    public static final String TESTING_RUN_ID = "testingRunId";
    private int ts;
    private String instanceId;
    private String testingRunId;

    public TestingInstanceHeartBeat() {
    }

    public TestingInstanceHeartBeat(String instanceId, int ts, String testingRunId) {
        this.instanceId = instanceId;
        this.ts = ts;
        this.testingRunId = testingRunId;
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

    public String getTestingRunId() {
        return testingRunId;
    }

    public void setTestingRunId(String testingRunId) {
        this.testingRunId = testingRunId;
    }


}

