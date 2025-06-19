package com.akto.dto.testing.info;

import java.util.List;

public  class ScheduledTestsDetails {
    private int countPendingTests;
    private List<ScheduledTestInfo> scheduledTestInfo;

    public int getCountPendingTests() {
        return countPendingTests;
    }

    public void setCountPendingTests(int countPendingTests) {
        this.countPendingTests = countPendingTests;
    }

    public List<ScheduledTestInfo> getScheduledTestInfo() {
        return scheduledTestInfo;
    }

    public void setScheduledTestInfo(List<ScheduledTestInfo> scheduledTestInfo) {
        this.scheduledTestInfo = scheduledTestInfo;
    }

    public ScheduledTestsDetails(int countPendingTests, List<ScheduledTestInfo> scheduledTestInfo) {
        this.countPendingTests = countPendingTests;
        this.scheduledTestInfo = scheduledTestInfo;
    }
}
