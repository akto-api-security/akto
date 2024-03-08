package com.akto.dto;

public class APIConfig {

    private String name;
    private String userIdentifier;
    private int threshold;
    private int sync_threshold_count;
    private int sync_threshold_time; // in seconds

    public APIConfig(String name, String userIdentifier, int threshold, int sync_threshold_count, int sync_threshold_time) {
        this.userIdentifier = userIdentifier;
        this.threshold = threshold;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        this.name = name;
    }

    public APIConfig() {
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public void setUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getSync_threshold_count() {
        return sync_threshold_count;
    }

    public void setSync_threshold_count(int sync_threshold_count) {
        this.sync_threshold_count = sync_threshold_count;
    }

    public int getSync_threshold_time() {
        return sync_threshold_time;
    }

    public void setSync_threshold_time(int sync_threshold_time) {
        this.sync_threshold_time = sync_threshold_time;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
