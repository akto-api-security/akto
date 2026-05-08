package com.akto.threat.backend.constants;

import com.akto.util.ThreatDetectionConstants;

/**
 * @deprecated Use {@link ThreatDetectionConstants} instead.
 * This class is kept for backward compatibility.
 */
@Deprecated
public class StatusConstants {

    // Status filter constants
    public static final String EVENTS_FILTER = ThreatDetectionConstants.EVENTS_FILTER;

    public static final String ACTIVE = ThreatDetectionConstants.ACTIVE;
    public static final String UNDER_REVIEW = ThreatDetectionConstants.UNDER_REVIEW;
    public static final String IGNORED = ThreatDetectionConstants.IGNORED;

    private StatusConstants() {
        // Private constructor to prevent instantiation
    }
}