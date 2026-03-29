package com.akto.threat.detection.enums;

/**
 * Enum representing the three threat detection modes.
 *
 * - FILTER_YAML_ONLY: Use only filter YAML templates (default)
 * - HYPERSCAN_ONLY: Use only Hyperscan pattern matching
 * - HYBRID: Use both Hyperscan and filter YAML (Hyperscan first for performance, fallback to YAML)
 */
public enum ThreatDetectionMode {
    /**
     * Use only filter YAML templates for threat detection.
     * This is the default and most comprehensive mode.
     */
    FILTER_YAML_ONLY("FILTER_YAML_ONLY"),

    /**
     * Use only Hyperscan for threat detection.
     * Fast but limited to pattern matching only.
     */
    HYPERSCAN_ONLY("HYPERSCAN_ONLY"),

    /**
     * Use both Hyperscan and filter YAML.
     * Hyperscan runs first for fast pattern detection,
     * then filter YAML for complex validation logic.
     */
    HYBRID("HYBRID");

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
