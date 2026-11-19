package com.akto.dto.notifications;

import org.bson.codecs.pojo.annotations.BsonIgnore;

/**
 * One rule of a sensitive data threshold alert: alert when a single API collection carries
 * {@link #subType} in more than {@link #threshold} events within the last {@link #windowDays} days.
 */
public class SensitiveDataAlertRule {

    /** Counts are kept for 40 days, so a window must fit inside that with margin. */
    /**
     * Longest window a rule may ask for. A window longer than what
     * {@code SensitiveDataCountDao.RETENTION_DAYS} keeps would silently sum only the buckets that
     * survived the TTL and report a total below the real one, so the threshold would never trip -
     * a wrong answer with no error. Retention is derived from this, which keeps the two in step.
     */
    public static final int MAX_WINDOW_DAYS = 90;

    public static final String SUB_TYPE = "subType";
    private String subType;

    public static final String THRESHOLD = "threshold";
    private int threshold;

    public static final String WINDOW_DAYS = "windowDays";
    private int windowDays;

    public SensitiveDataAlertRule() {
    }

    public SensitiveDataAlertRule(String subType, int threshold, int windowDays) {
        this.subType = subType;
        this.threshold = threshold;
        this.windowDays = windowDays;
    }

    /** Not a stored field: the codec would otherwise persist it as "valid". */
    @BsonIgnore
    public boolean isValid() {
        return subType != null && !subType.trim().isEmpty()
                && threshold > 0
                && windowDays >= 1 && windowDays <= MAX_WINDOW_DAYS;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(int windowDays) {
        this.windowDays = windowDays;
    }
}
