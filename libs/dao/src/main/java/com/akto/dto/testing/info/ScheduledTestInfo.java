package com.akto.dto.testing.info;

public  class ScheduledTestInfo {
    private String name;
    private int scheduledTimestamp;

    public ScheduledTestInfo(String name, int scheduledTimestamp) {
        this.name = name;
        this.scheduledTimestamp = scheduledTimestamp;
    }

    public String getName() { return name; }
    public int getScheduledTimestamp() { return scheduledTimestamp; }
}