package com.akto.threat.detection.enums;

/**
 * Enum representing the threat detection modes.
 *
 * - FILTER_YAML_ONLY: Use only filter YAML templates (default)
 * - HYPERSCAN_ONLY: Use only Hyperscan pattern matching
 */
public enum ThreatDetectionMode {
    FILTER_YAML_ONLY("FILTER_YAML_ONLY"),
    HYPERSCAN_ONLY("HYPERSCAN_ONLY");

    private final String value;

    ThreatDetectionMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse detection mode from environment variable string.
     * Returns FILTER_YAML_ONLY as default if invalid or null.
     */
    public static ThreatDetectionMode fromString(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return FILTER_YAML_ONLY;
        }

        String normalized = mode.trim().toUpperCase();

        for (ThreatDetectionMode detectionMode : values()) {
            if (detectionMode.value.equals(normalized)) {
                return detectionMode;
            }
        }

        // Default to FILTER_YAML_ONLY if unrecognized
        return FILTER_YAML_ONLY;
    }

    @Override
    public String toString() {
        return value;
    }
}
