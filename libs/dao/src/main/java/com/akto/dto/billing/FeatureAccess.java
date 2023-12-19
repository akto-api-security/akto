package com.akto.dto.billing;

import com.akto.dao.context.Context;

public class FeatureAccess {
    public boolean isGranted;
    public static final String IS_GRANTED = "isGranted";
    public int overageFirstDetected = -1;
    public static final String OVERAGE_FIRST_DETECTED = "overageFirstDetected";

    private static final int GRACE_PERIOD = 60 * 60 * 24 * 7; // 7 days

    public FeatureAccess(boolean isGranted, int overageFirstDetected) {
        this.isGranted = isGranted;
        this.overageFirstDetected = overageFirstDetected;
    }

    public FeatureAccess() {
    }

    public int getOverageFirstDetected() {
        return overageFirstDetected;
    }

    public void setOverageFirstDetected(int overageFirstDetected) {
        this.overageFirstDetected = overageFirstDetected;
    }

    public boolean getIsGranted() {
        return isGranted;
    }

    public void setIsGranted(boolean isGranted) {
        this.isGranted = isGranted;
    }

    public static final String IS_OVERAGE_AFTER_GRACE = "isOverageAfterGrace";

    public boolean checkOverageAfterGrace() {
        return this.getOverageFirstDetected() != -1 &&
                 !( this.getOverageFirstDetected() + GRACE_PERIOD > Context.now() );
    }
}