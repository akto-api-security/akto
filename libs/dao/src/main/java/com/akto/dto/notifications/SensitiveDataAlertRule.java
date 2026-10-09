package com.akto.dto.notifications;

/**
 * One rule of a sensitive data threshold alert: alert when a single API collection carries
 * {@link #subType} in more than {@link #threshold} events within the last {@link #windowDays} days.
 */
public class SensitiveDataAlertRule {

    /** Counts are kept for 40 days, so a window must fit inside that with margin. */
    public static final int MAX_WINDOW_DAYS = 30;

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
