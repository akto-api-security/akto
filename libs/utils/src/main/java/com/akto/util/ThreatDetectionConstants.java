package com.akto.util;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;

/**
 * Constants related to threat detection and malicious event handling.
 * This class is shared between threat-detection and threat-detection-backend modules.
 */
public class ThreatDetectionConstants {

    // Status filter constants
    public static final String EVENTS_FILTER = "EVENTS";

    public static final String ACTIVE = MaliciousEventDto.Status.ACTIVE.toString();
    public static final String UNDER_REVIEW = MaliciousEventDto.Status.UNDER_REVIEW.toString();
    public static final String IGNORED = MaliciousEventDto.Status.IGNORED.toString();

    // Category constants
    public static final String THREAT_PROTECTION_SUCCESSFUL_EXPLOIT_CATEGORY = "SuccessfulExploit";
    public static final String THREAT_PROTECTION_IGNORED_EVENTS_CATEGORY = "IgnoredEvent";

    private ThreatDetectionConstants() {
        // Private constructor to prevent instantiation
    }
}
