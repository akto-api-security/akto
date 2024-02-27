package com.akto.dto.billing;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncLimit {
    public final boolean checkLimit;
    private AtomicInteger usageLeft;

    public static final SyncLimit noLimit = new SyncLimit(false, 0);

    public SyncLimit(boolean checkLimit, int usageLeft) {
        this.checkLimit = checkLimit;
        this.usageLeft = new AtomicInteger(usageLeft);
    }

    public boolean updateUsageLeftAndCheckSkip() {
        if (!checkLimit) {
            return false;
        }

        if (usageLeft.get() >= 0) {
            usageLeft.decrementAndGet();
        }

        return this.getUsageLeft() < 0;
    }

    public int getUsageLeft() {
        return usageLeft.get();
    }

}