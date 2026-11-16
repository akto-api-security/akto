package com.akto.service.posture;

/** How long ago an asset last had traffic, in the buckets the Analytics tab charts. */
public enum ArgusInventoryUse {
    TODAY("today", "Today"),
    WEEK("w", "1–7 days"),
    MONTH("m", "8–30 days"),
    QUARTER("q", "31–90 days"),
    OLDER("old", "90+ days"),
    NEVER("never", "Never");

    private static final int DAY_SECONDS = 86400;
    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;
    private static final int QUARTER_DAYS = 90;

    private final String key;
    private final String label;

    ArgusInventoryUse(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** The bucket for a last-traffic time; zero means none was ever recorded. */
    public static ArgusInventoryUse of(int seenTs, int now) {
        if (seenTs == 0) return NEVER;
        int days = (now - seenTs) / DAY_SECONDS;
        if (days < 1) return TODAY;
        if (days <= WEEK_DAYS) return WEEK;
        if (days <= MONTH_DAYS) return MONTH;
        if (days <= QUARTER_DAYS) return QUARTER;
        return OLDER;
    }
}
