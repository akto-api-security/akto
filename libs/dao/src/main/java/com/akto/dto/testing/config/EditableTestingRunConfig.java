package com.akto.dto.testing.config;
import com.akto.dto.testing.TestingRunConfig;

public class EditableTestingRunConfig extends TestingRunConfig {
    private int maxConcurrentRequests;
    private String testingRunHexId;
    private int testRunTime;

    public EditableTestingRunConfig() {

    }

    public int getTestRunTime() {
        return testRunTime;
    }

    public void setTestRunTime(int testRunTime) {
        this.testRunTime = testRunTime;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }
}

