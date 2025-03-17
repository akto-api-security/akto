package com.akto.dto.testing.config;
import com.akto.dto.testing.TestingRunConfig;

public class EditableTestingRunConfig extends TestingRunConfig {
    private int maxConcurrentRequests;
    private String testingRunHexId;
    private int testRunTime;
    private boolean sendSlackAlert;
    private boolean recurringDaily;
    private boolean continuousTesting;
    private boolean sendMsTeamsAlert;
    private int periodInSeconds;
    private int scheduleTimestamp;
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

    public boolean getSendMsTeamsAlert() {
        return sendMsTeamsAlert;
    }

    public void setSendMsTeamsAlert(boolean sendMsTeamsAlert) {
        this.sendMsTeamsAlert = sendMsTeamsAlert;
    }

    public int getPeriodInSeconds() {
        return periodInSeconds;
    }

    public void setPeriodInSeconds(int periodInSeconds) {
        this.periodInSeconds = periodInSeconds;
    }

    public int getScheduleTimestamp() {
        return scheduleTimestamp;
    }

    public void setScheduleTimestamp(int scheduleTimestamp) {
        this.scheduleTimestamp = scheduleTimestamp;
    }
}

