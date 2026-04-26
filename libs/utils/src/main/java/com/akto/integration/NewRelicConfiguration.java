package com.akto.integration;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * NewRelic Integration Configuration
 * Manages environment variables and properties for NewRelic metrics export
 */
@Getter
public class NewRelicConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(NewRelicConfiguration.class);
    private static NewRelicConfiguration instance;

    // Configuration values
    private final boolean integrationEnabled;
    private final String encryptionKey;
    private final int apiTimeoutMs;
    private final int maxBatchSize;
    private final int metricsExportIntervalSec;
    private final String kafkaBootstrapServers;
    private final String kafkaConsumerGroup;
    private final String mongodbUri;
    private final String logLevel;

    // Validation constants
    private static final int MIN_API_TIMEOUT_MS = 1000;
    private static final int MAX_API_TIMEOUT_MS = 60000;
    private static final int MIN_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 10000;
    private static final int MIN_EXPORT_INTERVAL_SEC = 5;
    private static final int MAX_EXPORT_INTERVAL_SEC = 3600;
    private static final int ENCRYPTION_KEY_SIZE_BYTES = 32;

    /**
     * Private constructor - use getInstance() to access
     */
    private NewRelicConfiguration() {
        // Load from environment variables
        this.integrationEnabled = parseBoolean(
            getEnvOrDefault("NEWRELIC_INTEGRATION_ENABLED", "false")
        );

        this.encryptionKey = System.getenv("NEWRELIC_ENCRYPTION_KEY");

        this.apiTimeoutMs = parseInt(
            getEnvOrDefault("NEWRELIC_API_TIMEOUT_MS", "10000"),
            10000,
            MIN_API_TIMEOUT_MS,
            MAX_API_TIMEOUT_MS
        );

        this.maxBatchSize = parseInt(
            getEnvOrDefault("NEWRELIC_MAX_BATCH_SIZE", "1000"),
            1000,
            MIN_BATCH_SIZE,
            MAX_BATCH_SIZE
        );

        this.metricsExportIntervalSec = parseInt(
            getEnvOrDefault("NEWRELIC_METRICS_EXPORT_INTERVAL_SEC", "60"),
            60,
            MIN_EXPORT_INTERVAL_SEC,
            MAX_EXPORT_INTERVAL_SEC
        );

        this.kafkaBootstrapServers = getEnvOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS",
            "localhost:9092"
        );

        this.kafkaConsumerGroup = getEnvOrDefault(
            "KAFKA_CONSUMER_GROUP",
            "akto-newrelic-metrics-export"
        );

        this.mongodbUri = getEnvOrDefault(
            "MONGODB_URI",
            "mongodb://localhost:27017/akto"
        );

        this.logLevel = getEnvOrDefault("NEWRELIC_LOG_LEVEL", "INFO");

        // Validate configuration
        validate();
    }

    /**
     * Get singleton instance
     */
    public static synchronized NewRelicConfiguration getInstance() {
        if (instance == null) {
            instance = new NewRelicConfiguration();
        }
        return instance;
    }

    /**
     * Validate configuration on startup
     */
    private void validate() {
        logger.info("Validating NewRelic configuration...");

        // If feature enabled, encryption key is required
        if (integrationEnabled && encryptionKey == null) {
            throw new IllegalStateException(
                "NEWRELIC_ENCRYPTION_KEY must be set when NEWRELIC_INTEGRATION_ENABLED=true"
            );
        }

        // Validate encryption key size if present
        if (encryptionKey != null) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encryptionKey);
                if (decoded.length != ENCRYPTION_KEY_SIZE_BYTES) {
                    throw new IllegalStateException(
                        "NEWRELIC_ENCRYPTION_KEY must be exactly 32 bytes (256-bit AES), got " + decoded.length
                    );
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                    "NEWRELIC_ENCRYPTION_KEY is not valid base64", e
                );
            }
        }

        // Log configuration summary
        logger.info("NewRelic Integration Configuration:");
        logger.info("  Integration Enabled: {}", integrationEnabled);
        logger.info("  API Timeout: {}ms", apiTimeoutMs);
        logger.info("  Max Batch Size: {}", maxBatchSize);
        logger.info("  Export Interval: {}s", metricsExportIntervalSec);
        logger.info("  Kafka Servers: {}", kafkaBootstrapServers);
        logger.info("  Kafka Consumer Group: {}", kafkaConsumerGroup);
        logger.info("  MongoDB URI: {}", mongodbUri);
        logger.info("  Log Level: {}", logLevel);

        if (integrationEnabled) {
            logger.warn("NewRelic integration is ENABLED - metrics will be exported");
        } else {
            logger.info("NewRelic integration is DISABLED - metrics will NOT be exported");
        }
    }

    /**
     * Get environment variable or default
     */
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Parse boolean from string
     */
    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Parse integer with validation
     */
    private static int parseInt(String value, int defaultValue, int min, int max) {
        try {
            int intValue = Integer.parseInt(value);
            if (intValue < min || intValue > max) {
                logger.warn("Value {} is outside valid range [{}, {}], using default {}",
                    intValue, min, max, defaultValue);
                return defaultValue;
            }
            return intValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value: {}, using default {}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get encryption key bytes (decoded from base64)
     */
    public byte[] getEncryptionKeyBytes() {
        if (encryptionKey == null) {
            throw new IllegalStateException("Encryption key not configured");
        }
        return Base64.getDecoder().decode(encryptionKey);
    }

    /**
     * Check if feature should be enabled for this organization
     */
    public boolean isEnabledForOrg(Integer orgId) {
        if (!integrationEnabled) {
            return false;
        }
        // Further org-specific checks can be added here
        // For now, feature is globally enabled/disabled
        return true;
    }
}
