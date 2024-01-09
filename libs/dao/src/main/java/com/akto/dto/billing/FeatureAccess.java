package com.akto.dto.billing;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import com.akto.dao.context.Context;

public class FeatureAccess {
    boolean isGranted;
    public static final String IS_GRANTED = "isGranted";
    int overageFirstDetected = -1;
    public static final String OVERAGE_FIRST_DETECTED = "overageFirstDetected";
    int usageLimit;
    public static final String USAGE_LIMIT = "usageLimit";
    int usage;
    public static final String USAGE = "usage";

    @BsonIgnore
    int measureEpoch;

    @BsonIgnore
    int gracePeriod = 0;

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

    public int getMeasureEpoch() {
        return measureEpoch;
    }

    public void setMeasureEpoch(int measureEpoch) {
        this.measureEpoch = measureEpoch;
    }

    public int getGracePeriod() {
        return gracePeriod;
    }

    public void setGracePeriod(int gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public boolean checkUnlimited() {
        return usageLimit == -1;
    }

    public static final String IS_OVERAGE_AFTER_GRACE = "isOverageAfterGrace";

    public boolean checkOverageAfterGrace() {
        return checkOverageAfterGrace(gracePeriod);
    }

    public boolean checkOverageAfterGrace(int gracePeriod) {

        // if not granted, then consider it as overage, i.e. cannot use the feature
        if(!this.getIsGranted()) {
            return true;
        }

        // if usage limit is unlimited, then consider it as not overage
        if(this.checkUnlimited()) {
            return false;
        }

        gracePeriod = Math.max(gracePeriod, 0);

        if (usage >= usageLimit) {
            if (overageFirstDetected == -1) {
                overageFirstDetected = Context.now();
            }
        } else {
            overageFirstDetected = -1;
        }

        return this.getOverageFirstDetected() != -1 &&
                 !( this.getOverageFirstDetected() + gracePeriod > Context.now() );
    }
}