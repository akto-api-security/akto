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
     * Valid values: FILTER_YAML_ONLY, HYPERSCAN_ONLY
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
            boolean hyperscanReady = initializeHyperscan();
            if (!hyperscanReady) {
                logger.errorAndAddToDb("Hyperscan failed to initialize, falling back to FILTER_YAML_ONLY mode");
                detectionMode = ThreatDetectionMode.FILTER_YAML_ONLY;
            }
        }

        initialized = true;
    }

    /**
     * Initialize Hyperscan threat matcher from pattern file.
     */
    private static boolean initializeHyperscan() {
        try {
            HyperscanThreatMatcher matcher = HyperscanThreatMatcher.getInstance();
            boolean hsInitialized = matcher.initializeFromClasspath("threat-patterns-example.txt");

            if (!hsInitialized) {
                logger.errorAndAddToDb("Failed to initialize Hyperscan matcher from classpath");
            } else {
                logger.infoAndAddToDb("Hyperscan matcher initialized successfully with " +
                    matcher.getPatternCount() + " patterns across " + matcher.getCategoryCount() + " categories");
            }
            return hsInitialized;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error initializing Hyperscan: " + e.getMessage());
            return false;
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

    public static boolean isHyperscanEnabled() {
        return getDetectionMode() == ThreatDetectionMode.HYPERSCAN_ONLY;
    }

    public static boolean isFilterYamlEnabled() {
        return getDetectionMode() == ThreatDetectionMode.FILTER_YAML_ONLY;
    }

    /**
     * Reset configuration (mainly for testing purposes).
     */
    public static void reset() {
        initialized = false;
        detectionMode = null;
    }
}
