package com.akto.util;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;

import java.util.Arrays;
import java.util.List;

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
    public static final String TRAINING = MaliciousEventDto.Status.TRAINING.toString();

    // Category constants
    public static final String THREAT_PROTECTION_SUCCESSFUL_EXPLOIT_CATEGORY = "SuccessfulExploit";
    public static final String THREAT_PROTECTION_IGNORED_EVENTS_CATEGORY = "IgnoredEvent";

    // Filter ID constants
    public static final String PII_DATA_LEAK_FILTER_ID = "PIIDataLeak";

    // PII-related subcategories for sensitive data event detection
    // These correspond to SingleTypeInfo.SubType values that represent sensitive data types
    public static final List<String> PII_SUBCATEGORIES = Arrays.asList(
        "EMAIL",
        "URL",
        "ADDRESS",
        "SSN",
        "CREDIT_CARD",
        "VIN",
        "PHONE_NUMBER",
        "UUID",
        "JWT",
        "IP_ADDRESS"
    );

    private ThreatDetectionConstants() {
        // Private constructor to prevent instantiation
    }
}
