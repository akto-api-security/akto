package com.akto.threat.backend.constants;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;

public class StatusConstants {

    // Status filter constants
    public static final String EVENTS_FILTER = "EVENTS";

    public static final String ACTIVE = MaliciousEventDto.Status.ACTIVE.toString();
    public static final String UNDER_REVIEW = MaliciousEventDto.Status.UNDER_REVIEW.toString();
    public static final String IGNORED = MaliciousEventDto.Status.IGNORED.toString();

    private StatusConstants() {
        // Private constructor to prevent instantiation
    }
}