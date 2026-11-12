package com.akto.service.atlas;

/** Review state of a discovered AI tool, as shown on the Atlas discovery pages. */
public enum AtlasAssetStatus {
    APPROVED, UNDER_REVIEW, UNAPPROVED, BLOCKED;

    /** The status name, or null when there is none. */
    public static String nameOf(AtlasAssetStatus status) {
        return status == null ? null : status.name();
    }
}
