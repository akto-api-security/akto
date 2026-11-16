package com.akto.service.posture;

/** Risk against activity: the four boxes under the Analytics plot. */
public enum ArgusInventoryQuadrant {
    HIGH_ACTIVE("hiA"),
    HIGH_DORMANT("hiD"),
    LOW_ACTIVE("loA"),
    LOW_DORMANT("loD");

    private final String key;

    ArgusInventoryQuadrant(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static ArgusInventoryQuadrant of(boolean high, boolean dormant) {
        if (high) return dormant ? HIGH_DORMANT : HIGH_ACTIVE;
        return dormant ? LOW_DORMANT : LOW_ACTIVE;
    }
}
