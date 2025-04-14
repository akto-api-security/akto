package com.akto.dto.agents;

public class AgentLog {
    String log;
    int eventTimestamp;

    public AgentLog(String log, int eventTimestamp) {
        this.log = log;
        this.eventTimestamp = eventTimestamp;
    }

    public AgentLog() {
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public int getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(int eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

}
