package com.akto.util;

public class LastCronRunInfo {
    public static final String LAST_SYNCED_CRON = "lastSyncedCron";
    private int lastSyncedCron;

    public static final String LAST_UPDATED_SENSITIVE_MAP = "lastUpdatedSensitiveMap";
    private int lastUpdatedSensitiveMap;

    public static final String LAST_UPDATED_SEVERITY = "lastUpdatedSeverity";
    private int lastUpdatedSeverity;

    public static final String LAST_INFO_RESETTED = "lastInfoResetted";
    private int lastInfoResetted ;

    public LastCronRunInfo() {
    }

    public LastCronRunInfo(int lastSyncedCron, int lastUpdatedSensitiveMap, int lastUpdatedSeverity, int lastInfoResetted) {
        this.lastSyncedCron = lastSyncedCron;
        this.lastUpdatedSensitiveMap = lastUpdatedSensitiveMap;
        this.lastUpdatedSeverity = lastUpdatedSeverity;
        this.lastInfoResetted = lastInfoResetted;
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

    public int getLastInfoResetted() {
        return lastInfoResetted;
    }

    public void setLastInfoResetted(int lastInfoResetted) {
        this.lastInfoResetted = lastInfoResetted;
    }
}
