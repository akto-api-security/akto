package com.akto.dto.testing.config;
import com.akto.dto.testing.TestingRunConfig;

public class EditableTestingRunConfig extends TestingRunConfig {
    private int maxConcurrentRequests;
    private String testingRunHexId;
    private int testRunTime;
    private boolean sendSlackAlert;
    private boolean recurringDaily;
    private boolean continuousTesting;

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

    public boolean getSendSlackAlert() {
        return sendSlackAlert;
    }

    public void setSendSlackAlert(boolean sendSlackAlert) {
        this.sendSlackAlert = sendSlackAlert;
    }

    public boolean getRecurringDaily() {
        return recurringDaily;
    }

    public void setRecurringDaily(boolean recurringDaily) {
        this.recurringDaily = recurringDaily;
    }

    public boolean getContinuousTesting() {
        return continuousTesting;
    }

    public void setContinuousTesting(boolean continuousTesting) {
        this.continuousTesting = continuousTesting;
    }
}

