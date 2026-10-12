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

    public static final String LAST_THREAT_SCORE_SYNC = "lastThreatScoreSync";
    private int lastThreatScoreSync;

    public static final String LAST_USER_ANALYSIS_CRON = "lastUserAnalysisCron";
    private int lastUserAnalysisCron;

    public static final String LAST_API_GROUPS_SYNC = "lastApiGroupsSync";
    private int lastApiGroupsSync;

    public static final String LAST_API_GROUPS_FULL_SYNC = "lastApiGroupsFullSync";
    private int lastApiGroupsFullSync;

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

    public int getLastThreatScoreSync() {
        return lastThreatScoreSync;
    }

    public void setLastThreatScoreSync(int lastThreatScoreSync) {
        this.lastThreatScoreSync = lastThreatScoreSync;
    }

    public int getLastUserAnalysisCron() {
        return lastUserAnalysisCron;
    }

    public void setLastUserAnalysisCron(int lastUserAnalysisCron) {
        this.lastUserAnalysisCron = lastUserAnalysisCron;
    }

    public int getLastApiGroupsSync() {
        return lastApiGroupsSync;
    }

    public void setLastApiGroupsSync(int lastApiGroupsSync) {
        this.lastApiGroupsSync = lastApiGroupsSync;
    }

    public int getLastApiGroupsFullSync() {
        return lastApiGroupsFullSync;
    }

    public void setLastApiGroupsFullSync(int lastApiGroupsFullSync) {
        this.lastApiGroupsFullSync = lastApiGroupsFullSync;
    }
}
