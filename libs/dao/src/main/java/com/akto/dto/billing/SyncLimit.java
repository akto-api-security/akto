package com.akto.dto.billing;

public class SyncLimit {
    boolean checkLimit;
    int usageLeft;

    public SyncLimit(boolean checkLimit, int usageLeft) {
        this.checkLimit = checkLimit;
        this.usageLeft = usageLeft;
    }

    public synchronized boolean updateUsageLeftAndCheckSkip() {
        if(usageLeft >= 0){
            usageLeft--;
        }
        return checkLimit && usageLeft < 0;
    }
}