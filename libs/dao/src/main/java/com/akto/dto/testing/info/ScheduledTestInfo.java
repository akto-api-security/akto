package com.akto.dto.testing.info;

public  class ScheduledTestInfo {
    private String name;
    private String scheduledTimestamp;

    public ScheduledTestInfo(String name, String scheduledTimestamp) {
        this.name = name;
        this.scheduledTimestamp = scheduledTimestamp;
    }

    public String getName() { return name; }
    public String getScheduledTimestamp() { return scheduledTimestamp; }
}