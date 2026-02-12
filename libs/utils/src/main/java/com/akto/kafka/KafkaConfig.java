package com.akto.kafka;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized Kafka configuration class that manages all Kafka-related
 * environment variables, constants, and property creation.
 */
public class KafkaConfig {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    // Environment variable names
    public static final String ENV_KAFKA_AUTH_ENABLED = "KAFKA_AUTH_ENABLED";
    public static final String ENV_KAFKA_USERNAME = "KAFKA_USERNAME";
    public static final String ENV_KAFKA_PASSWORD = "KAFKA_PASSWORD";
    public static final String ENV_KAFKA_BROKER_URL = "AKTO_KAFKA_BROKER_URL";
    public static final String ENV_IS_KUBERNETES = "IS_KUBERNETES";

    // Kafka configuration constants
    public static final String SECURITY_PROTOCOL = "security.protocol";
    public static final String SASL_MECHANISM = "sasl.mechanism";
    public static final String SASL_JAAS_CONFIG = "sasl.jaas.config";

    public static final String SECURITY_PROTOCOL_PLAINTEXT = "PLAINTEXT";
    public static final String SECURITY_PROTOCOL_SSL = "SSL";
    public static final String SECURITY_PROTOCOL_SASL_PLAINTEXT = "SASL_PLAINTEXT";
    public static final String SECURITY_PROTOCOL_SASL_SSL = "SASL_SSL";
    public static final String SASL_MECHANISM_PLAIN = "PLAIN";

    // New environment variable for SASL mechanism
    public static final String ENV_KAFKA_SASL_MECHANISM = "AKTO_KAFKA_SASL_MECHANISM";

    // Environment variable for security protocol
    public static final String ENV_KAFKA_SECURITY_PROTOCOL = "AKTO_KAFKA_SECURITY_PROTOCOL";

    // New SASL mechanism constants
    public static final String SASL_MECHANISM_SCRAM_SHA_256 = "SCRAM-SHA-256";
    public static final String SASL_MECHANISM_SCRAM_SHA_512 = "SCRAM-SHA-512";

    // Default SASL mechanism (for backward compatibility)
    public static final String DEFAULT_SASL_MECHANISM = SASL_MECHANISM_PLAIN;

    // Default Kafka broker URLs
    public static final String DEFAULT_KAFKA_BROKER_URL = "kafka1:19092";
    public static final String DEFAULT_KUBERNETES_KAFKA_BROKER_URL = "127.0.0.1:29092";

    /**
     * Gets the Kafka authentication enabled status from environment.
     *
     * @return true if Kafka authentication is enabled, false otherwise
     */
    public static boolean isKafkaAuthenticationEnabled() {
        String authEnabled = System.getenv(ENV_KAFKA_AUTH_ENABLED);
        return authEnabled != null && authEnabled.equalsIgnoreCase("true");
    }

    /**
     * Gets the Kafka username from environment.
     *
     * @return Kafka username or null if not set
     */
    public static String getKafkaUsername() {
        return System.getenv(ENV_KAFKA_USERNAME);
    }

    /**
     * Gets the Kafka password from environment.
     *
     * @return Kafka password or null if not set
     */
    public static String getKafkaPassword() {
        return System.getenv(ENV_KAFKA_PASSWORD);
    }

    /**
     * Gets the Kafka broker URL from environment, with fallback to defaults.
     * Automatically handles Kubernetes vs non-Kubernetes deployments.
     *
     * @return Kafka broker URL
     */
    public static String getKafkaBrokerUrl() {
        String kafkaBrokerUrl = System.getenv().getOrDefault(ENV_KAFKA_BROKER_URL, DEFAULT_KAFKA_BROKER_URL);
        String isKubernetes = System.getenv(ENV_IS_KUBERNETES);

        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            kafkaBrokerUrl = System.getenv().getOrDefault(ENV_KAFKA_BROKER_URL, DEFAULT_KUBERNETES_KAFKA_BROKER_URL);
        }

        return kafkaBrokerUrl;
    }

    /**
     * Gets the configured SASL mechanism from environment variable with validation.
     *
     * @return SASL mechanism (PLAIN, SCRAM-SHA-256, or SCRAM-SHA-512)
     */
    public static String getKafkaSaslMechanism() {
        String mechanism = System.getenv().getOrDefault(ENV_KAFKA_SASL_MECHANISM, DEFAULT_SASL_MECHANISM);

        // Validate mechanism
        if (!mechanism.equals(SASL_MECHANISM_PLAIN) &&
            !mechanism.equals(SASL_MECHANISM_SCRAM_SHA_256) &&
            !mechanism.equals(SASL_MECHANISM_SCRAM_SHA_512)) {
            logger.error("Invalid SASL mechanism: {}. Using default: {}", mechanism, DEFAULT_SASL_MECHANISM);
            return DEFAULT_SASL_MECHANISM;
        }

        return mechanism;
    }

    /**
     * Gets the configured security protocol from environment variable with validation.
     *
     * @param hasCredentials Whether SASL credentials (username/password) are provided
     * @return Security protocol (PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL)
     */
    public static String getKafkaSecurityProtocol(boolean hasCredentials) {
        String protocol = System.getenv(ENV_KAFKA_SECURITY_PROTOCOL);

        // If explicitly set, validate it
        if (protocol != null && !protocol.isEmpty()) {
            // Validate: SASL protocols require credentials
            if ((protocol.equals(SECURITY_PROTOCOL_SASL_PLAINTEXT) ||
                 protocol.equals(SECURITY_PROTOCOL_SASL_SSL)) && !hasCredentials) {
                logger.warn("SASL security protocol '{}' set but no credentials provided. Falling back to PLAINTEXT.", protocol);
                return SECURITY_PROTOCOL_PLAINTEXT;
            }
            return protocol;
        }

        // Auto-detect based on credentials
        return hasCredentials ? SECURITY_PROTOCOL_SASL_PLAINTEXT : SECURITY_PROTOCOL_PLAINTEXT;
    }

    /**
     * Gets the configured security protocol from environment variable.
     * Assumes credentials are provided (backward compatibility).
     *
     * @return Security protocol (defaults to SASL_PLAINTEXT)
     * @deprecated Use {@link #getKafkaSecurityProtocol(boolean)} instead
     */
    @Deprecated
    public static String getKafkaSecurityProtocol() {
        return getKafkaSecurityProtocol(true);
    }

    /**
     * Adds Kafka authentication properties to the given Properties object.
     *
     * @param properties The Properties object to add authentication to
     * @param username   Kafka username
     * @param password   Kafka password
     */
    public static void addAuthenticationProperties(Properties properties, String username, String password) {
        addAuthenticationProperties(properties, username, password, SECURITY_PROTOCOL_SASL_PLAINTEXT);
    }

    /**
     * Adds Kafka authentication properties to the given Properties object with specified security protocol.
     *
     * @param properties       The Properties object to add authentication to
     * @param username         Kafka username
     * @param password         Kafka password
     * @param securityProtocol Security protocol (SASL_PLAINTEXT, SASL_SSL)
     */
    public static void addAuthenticationProperties(Properties properties, String username, String password, String securityProtocol) {
        properties.put(SECURITY_PROTOCOL, securityProtocol);

        String saslMechanism = getKafkaSaslMechanism();
        properties.put(SASL_MECHANISM, saslMechanism);

        String jaasConfig;
        if (saslMechanism.equals(SASL_MECHANISM_PLAIN)) {
            jaasConfig = String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    username, password);
        } else {
            // SCRAM-SHA-256 or SCRAM-SHA-512 use the same ScramLoginModule
            jaasConfig = String.format(
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                    username, password);
        }

        properties.put(SASL_JAAS_CONFIG, jaasConfig);

        // If using SSL, disable hostname verification for NLB with SSL termination
        if (SECURITY_PROTOCOL_SASL_SSL.equals(securityProtocol)) {
            properties.put("ssl.endpoint.identification.algorithm", "");
        }
    }

    /**
     * Creates Kafka AdminClient properties with authentication support.
     *
     * @param brokerUrl     The Kafka broker URL
     * @param isAuthEnabled Whether Kafka authentication is enabled
     * @param username      Kafka username (required if auth is enabled)
     * @param password      Kafka password (required if auth is enabled)
     * @return Properties configured for Kafka AdminClient, or null if auth is
     *         enabled but credentials are missing
     */
    public static Properties createAdminProperties(String brokerUrl, boolean isAuthEnabled, String username,
            String password) {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", brokerUrl);

        if (isAuthEnabled) {
            if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
                logger.error("Kafka authentication enabled but credentials not provided");
                return null;
            }
            // Read security protocol from environment, default to SASL_PLAINTEXT
            String securityProtocol = System.getenv().getOrDefault(ENV_KAFKA_SECURITY_PROTOCOL, SECURITY_PROTOCOL_SASL_PLAINTEXT);
            addAuthenticationProperties(adminProps, username, password, securityProtocol);
        }

        return adminProps;
    }

    /**
     * Creates Kafka AdminClient properties using environment variables.
     *
     * @return Properties configured for Kafka AdminClient, or null if auth is
     *         enabled but credentials are missing
     */
    public static Properties createAdminPropertiesFromEnv() {
        return createAdminProperties(
                getKafkaBrokerUrl(),
                isKafkaAuthenticationEnabled(),
                getKafkaUsername(),
                getKafkaPassword());
    }

    /**
     * Creates Kafka Producer properties with authentication support.
     *
     * @param brokerUrl         The Kafka broker URL
     * @param lingerMS          Linger time in milliseconds
     * @param batchSize         Batch size
     * @param maxRequestTimeout Maximum request timeout
     * @param maxRetries        Maximum number of retries
     * @param isAuthEnabled     Whether Kafka authentication is enabled
     * @param username          Kafka username (required if auth is enabled)
     * @param password          Kafka password (required if auth is enabled)
     * @return Properties configured for Kafka Producer, or null if auth is enabled
     *         but credentials are missing
     */
    public static Properties createProducerProperties(String brokerUrl, int lingerMS, int batchSize,
            int maxRequestTimeout, int maxRetries,
            boolean isAuthEnabled, String username, String password) {
        if (isAuthEnabled && (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))) {
            logger.error("Kafka authentication credentials not provided");
            return null;
        }

        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMS);
        kafkaProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        kafkaProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, maxRequestTimeout);
        kafkaProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, lingerMS + maxRequestTimeout);

        if (maxRetries > 0) {
            kafkaProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        }

        if (isAuthEnabled) {
            // Read security protocol from environment, default to SASL_PLAINTEXT
            String securityProtocol = System.getenv().getOrDefault(ENV_KAFKA_SECURITY_PROTOCOL, SECURITY_PROTOCOL_SASL_PLAINTEXT);
            addAuthenticationProperties(kafkaProps, username, password, securityProtocol);
        }

        return kafkaProps;
    }

    public static String getTopicName() {
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topicName == null) {
            topicName = "akto.api.logs";
        }
        return topicName;
    }
}
