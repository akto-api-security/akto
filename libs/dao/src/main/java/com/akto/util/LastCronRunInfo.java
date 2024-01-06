package com.akto.util;

public class LastCronRunInfo {
    public static final String LAST_SYNCED_CRON = "lastSyncedCron";
    private int lastSyncedCron;

    public static final String LAST_UPDATED_SENSITIVE_MAP = "lastUpdatedSensitiveMap";
    private int lastUpdatedSensitiveMap;

    public static final String LAST_UPDATED_SEVERITY = "lastUpdatedSeverity";
    private int lastUpdatedSeverity;

    public LastCronRunInfo() {
    }

    public LastCronRunInfo(int lastSyncedCron, int lastUpdatedSensitiveMap, int lastUpdatedSeverity) {
        this.lastSyncedCron = lastSyncedCron;
        this.lastUpdatedSensitiveMap = lastUpdatedSensitiveMap;
        this.lastUpdatedSeverity = lastUpdatedSeverity;
    }

    public int getLastSyncedCron() {
        return lastSyncedCron;
    }

    public void setLastSyncedCron(int lastSyncedCron) {
        this.lastSyncedCron = lastSyncedCron;
    }

    public int getLastUpdatedSensitiveMap() {
        return lastUpdatedSensitiveMap;
    }

    public void setLastUpdatedSensitiveMap(int lastUpdatedSensitiveMap) {
        this.lastUpdatedSensitiveMap = lastUpdatedSensitiveMap;
    }

    public int getLastUpdatedSeverity() {
        return lastUpdatedSeverity;
    }

    public void setLastUpdatedSeverity(int lastUpdatedSeverity) {
        this.lastUpdatedSeverity = lastUpdatedSeverity;
    }
}
