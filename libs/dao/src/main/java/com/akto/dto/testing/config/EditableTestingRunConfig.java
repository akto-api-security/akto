package com.akto.dto.testing.config;
import com.akto.dto.testing.TestingRunConfig;

import lombok.Getter;
import lombok.Setter;

public class EditableTestingRunConfig extends TestingRunConfig {
    private int maxConcurrentRequests;
    private String testingRunHexId;
    private int testRunTime;
    private boolean sendSlackAlert;
    private boolean recurringDaily;
    private boolean recurringWeekly;
    private boolean recurringMonthly;
    private boolean continuousTesting;
    private boolean sendMsTeamsAlert;
    private int scheduleTimestamp;
    private int selectedSlackChannelId;

    @Getter
    @Setter
    private String miniTestingServiceName;
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

    public boolean getRecurringWeekly() {
        return recurringWeekly;
    }

    public void setRecurringWeekly(boolean recurringWeekly) {
        this.recurringWeekly = recurringWeekly;
    }

    public boolean getRecurringMonthly() {
        return recurringMonthly;
    }

    public void setRecurringMonthly(boolean recurringMonthly) {
        this.recurringMonthly = recurringMonthly;
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

    public int getScheduleTimestamp() {
        return scheduleTimestamp;
    }

    public void setScheduleTimestamp(int scheduleTimestamp) {
        this.scheduleTimestamp = scheduleTimestamp;
    }

    public int getSelectedSlackChannelId() {
        return selectedSlackChannelId;
    }

    public void setSelectedSlackChannelId(int selectedSlackChannelId) {
        this.selectedSlackChannelId = selectedSlackChannelId;
    }
}

