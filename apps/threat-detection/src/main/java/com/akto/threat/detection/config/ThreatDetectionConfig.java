package com.akto.threat.detection.config;

import com.akto.threat.detection.enums.ThreatDetectionMode;
import com.akto.threat.detection.hyperscan.HyperscanThreatMatcher;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

/**
 * Configuration loader for threat detection settings.
 * Reads configuration from environment variables.
 */
public class ThreatDetectionConfig {

    private static final LoggerMaker logger = new LoggerMaker(ThreatDetectionConfig.class, LogDb.THREAT_DETECTION);

    /**
     * Environment variable name for threat detection mode.
     * Valid values: FILTER_YAML_ONLY, HYPERSCAN_ONLY, HYBRID
     * Default: FILTER_YAML_ONLY
     */
    public static final String ENV_THREAT_DETECTION_MODE = "THREAT_DETECTION_MODE";

    private static ThreatDetectionMode detectionMode;
    private static boolean initialized = false;

    /**
     * Initialize configuration from environment variables.
     * This should be called once at application startup.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        String modeEnv = System.getenv(ENV_THREAT_DETECTION_MODE);
        detectionMode = ThreatDetectionMode.fromString(modeEnv);

        logger.infoAndAddToDb(String.format(
            "Threat Detection Mode initialized: %s (env: %s)",
            detectionMode,
            modeEnv != null ? modeEnv : "not set, using default"
        ));

        // Only initialize Hyperscan when mode requires it
        if (detectionMode != ThreatDetectionMode.FILTER_YAML_ONLY) {
            initializeHyperscan();
        }

        initialized = true;
    }

    /**
     * Initialize Hyperscan threat matcher from pattern file.
     */
    private static void initializeHyperscan() {
        try {
            HyperscanThreatMatcher matcher = HyperscanThreatMatcher.getInstance();
            boolean initialized = false;

            // First, try from environment variable (file path)
            String envPath = System.getenv("HYPERSCAN_PATTERN_FILE");
            if (envPath != null && !envPath.isEmpty()) {
                logger.infoAndAddToDb("Using HYPERSCAN_PATTERN_FILE env var: " + envPath);
                initialized = matcher.initialize(envPath);
            }

            // Second, try from classpath resources
            if (!initialized) {
                logger.infoAndAddToDb("Trying to load pattern file from classpath: threat-patterns-example.txt");
                initialized = matcher.initializeFromClasspath("threat-patterns-example.txt");
            }

            // Third, try default file path
            if (!initialized) {
                String defaultPath = "apps/threat-detection/src/main/resources/threat-patterns-example.txt";
                logger.infoAndAddToDb("Trying default pattern file path: " + defaultPath);
                initialized = matcher.initialize(defaultPath);
            }

            if (!initialized) {
                logger.errorAndAddToDb("Failed to initialize Hyperscan matcher from all sources");
            } else {
                logger.infoAndAddToDb("Hyperscan matcher initialized successfully with " +
                    matcher.getPatternCount() + " patterns across " + matcher.getCategoryCount() + " categories");
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error initializing Hyperscan: " + e.getMessage());
        }
    }

    /**
     * Get the configured threat detection mode.
     * Initializes with default if not already initialized.
     *
     * @return The configured ThreatDetectionMode
     */
    public static ThreatDetectionMode getDetectionMode() {
        if (!initialized) {
            initialize();
        }
        return detectionMode;
    }

    /**
     * Check if Hyperscan should be used (either HYPERSCAN_ONLY or HYBRID mode).
     *
     * @return true if Hyperscan should be enabled
     */
    public static boolean isHyperscanEnabled() {
        ThreatDetectionMode mode = getDetectionMode();
        return mode == ThreatDetectionMode.HYPERSCAN_ONLY || mode == ThreatDetectionMode.HYBRID;
    }

    /**
     * Check if Filter YAML should be used (either FILTER_YAML_ONLY or HYBRID mode).
     *
     * @return true if Filter YAML should be enabled
     */
    public static boolean isFilterYamlEnabled() {
        ThreatDetectionMode mode = getDetectionMode();
        return mode == ThreatDetectionMode.FILTER_YAML_ONLY || mode == ThreatDetectionMode.HYBRID;
    }

    /**
     * Reset configuration (mainly for testing purposes).
     */
    public static void reset() {
        initialized = false;
        detectionMode = null;
    }
}
