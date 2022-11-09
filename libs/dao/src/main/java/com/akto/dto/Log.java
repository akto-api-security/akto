package com.akto.dto;

public class Log {
    private String log;
    private String key;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public Log() {
    }

    public Log(String log, String key, int timestamp) {
        this.log = log;
        this.key = key;
        this.timestamp = timestamp;
    }

    public String getLog() {
        return this.log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "{" +
            " log='" + getLog() + "'" +
            ", key='" + getKey() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            "}";
    }

}
