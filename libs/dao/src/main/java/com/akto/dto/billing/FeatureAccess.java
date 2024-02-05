package com.akto.dto.billing;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import com.akto.dao.context.Context;

public class FeatureAccess {
    private boolean isGranted;
    public static final String IS_GRANTED = "isGranted";
    private int overageFirstDetected = -1;
    public static final String OVERAGE_FIRST_DETECTED = "overageFirstDetected";

    private static final int STANDARD_GRACE_PERIOD = 0;

    private int usageLimit;
    public static final String USAGE_LIMIT = "usageLimit";
    private int usage;
    public static final String USAGE = "usage";

    @BsonIgnore
    private int gracePeriod = 0;

    @BsonIgnore
    private int measureEpoch;

    public static final FeatureAccess noAccess = new FeatureAccess(false);
    public static final FeatureAccess fullAccess = new FeatureAccess(true);

    public FeatureAccess(boolean isGranted, int overageFirstDetected, int usageLimit, int usage) {
        this.isGranted = isGranted;
        this.overageFirstDetected = overageFirstDetected;
        this.usageLimit = usageLimit;
        this.usage = usage;
    }

    public FeatureAccess(boolean isGranted) {
        this(isGranted, -1, -1, 0);
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

    public int getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(int usageLimit) {
        this.usageLimit = usageLimit;
    }

    public int getUsage() {
        return usage;
    }

    public void setUsage(int usage) {
        this.usage = usage;
    }

    public boolean checkBooleanOrUnlimited() {
        return usageLimit == -1;
    }

    public static final String IS_OVERAGE_AFTER_GRACE = "isOverageAfterGrace";

    public int getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(int gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public int getMeasureEpoch() {
        return measureEpoch;
    }

    public void setMeasureEpoch(int measureEpoch) {
        this.measureEpoch = measureEpoch;
    }

    public boolean checkInvalidAccess() {

        if (!getIsGranted()) {
            return true;
        }

        if (checkBooleanOrUnlimited()) {
            return false;
        }

        if (usage >= usageLimit) {
            if (overageFirstDetected == -1) {
                overageFirstDetected = Context.now();
            }
        } else {
            overageFirstDetected = -1;
        }

        if (gracePeriod <= 0) {
            gracePeriod = STANDARD_GRACE_PERIOD;
        }

        return this.getOverageFirstDetected() != -1 &&
                 !( this.getOverageFirstDetected() + gracePeriod > Context.now() );
    }
}