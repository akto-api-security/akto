package com.akto.dto.usage;

public class UsageSync {
    
    private String service;
    public static final String SERVICE = "service";
    private int lastSyncStartEpoch;
    public static final String LAST_SYNC_START_EPOCH = "lastSyncStartEpoch";

    public UsageSync() {

    }

    public UsageSync(String service, int lastSyncStartEpoch) {
        this.service = service;
        this.lastSyncStartEpoch = lastSyncStartEpoch;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public int getLastSyncStartEpoch() {
       return lastSyncStartEpoch;
    }

    public void setLastSyncStartEpoch(int lastSyncStartEpoch) {
        this.lastSyncStartEpoch = lastSyncStartEpoch;
    }
}