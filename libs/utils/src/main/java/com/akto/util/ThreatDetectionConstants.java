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
    // These represent security misconfigurations that expose credentials, secrets, and sensitive data
    public static final List<String> PII_SUBCATEGORIES = Arrays.asList(
        "SENSITIVE_DATA_EXPOSURE_PASSWORD",
        "LARAVEL_ENV",
        "PARAMETERS_CONFIG",
        "FIREBASE_CONFIG_EXPOSURE",
        "GIT_CREDENTIALS_DISCLOSURE",
        "GIT_CONFIG",
        "GITHUB_WORKFLOW_DISCLOSURE",
        "AIRFLOW_CONFIGURATION_EXPOSURE",
        "WPCONFIG_AWS_KEY",
        "SPRING_BOOT_ENV_ACTUATOR_EXPOSED",
        "ORACLE_EBS_CREDENTIALS",
        "ROBOMONGO_CREDENTIAL",
        "SSH_AUTHORIZED_KEYS",
        "SSH_KNOWN_HOSTS",
        "SERVER_PRIVATE_KEYS",
        "FTP_CREDENTIALS_EXPOSURE",
        "SFTP_CONFIG_EXPOSURE",
        "MSMTP_CONFIG",
        "ESMTPRC_CONFIG",
        "DOCKERFILE_HIDDEN_DISCLOSURE",
        "DOCKER_COMPOSE_CONFIG",
        "AMAZON_DOCKER_CONFIG",
        "MISCONFIGURED_DOCKER"
    );

    private ThreatDetectionConstants() {
        // Private constructor to prevent instantiation
    }
}
