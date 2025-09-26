package com.akto.threat.backend.constants;

import com.akto.threat.backend.db.MaliciousEventModel;

public class StatusConstants {

    // Status filter constants
    public static final String EVENTS_FILTER = "EVENTS";

    // Convenience constants for MaliciousEventModel.Status
    public static final String ACTIVE = MaliciousEventModel.Status.ACTIVE.toString();
    public static final String UNDER_REVIEW = MaliciousEventModel.Status.UNDER_REVIEW.toString();
    public static final String IGNORED = MaliciousEventModel.Status.IGNORED.toString();

    private StatusConstants() {
        // Private constructor to prevent instantiation
    }
}