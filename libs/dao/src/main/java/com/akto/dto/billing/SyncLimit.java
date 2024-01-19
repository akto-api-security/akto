package com.akto.dto.billing;

public class SyncLimit {
    private final boolean checkLimit;
    private int usageLeft;

    public SyncLimit(boolean checkLimit, int usageLeft) {
        this.checkLimit = checkLimit;
        this.usageLeft = usageLeft;
    }

    public boolean updateUsageLeftAndCheckSkip() {
        if (!checkLimit) {
            return false;
        }
        synchronized (this) {
            if (usageLeft >= 0) {
                usageLeft--;
            }
            return checkLimit && usageLeft < 0;
        }
    }
}