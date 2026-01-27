package com.akto.utils;

import com.akto.config.GuardrailsConfig;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;

/**
 * Handles publishing messages to multiple Kafka topics based on configuration.
 * Encapsulates the logic for conditional multi-topic publishing.
 */
public class TopicPublisher {

    private static final LoggerMaker logger = new LoggerMaker(TopicPublisher.class, LoggerMaker.LogDb.DATA_INGESTION);
    private final GuardrailsConfig config;

    public TopicPublisher(GuardrailsConfig config) {
        this.config = config;
    }

    /**
     * Publishes a message to the primary topic and optionally to guardrails topic.
     * Gets the current Kafka producer from KafkaUtils to support reconnection.
     *
     * @param message The message to publish
     * @param primaryTopic The primary destination topic
     */
    public void publish(String message, String primaryTopic) {
        // Get current producer (supports reconnection)
        Kafka currentProducer = KafkaUtils.getKafkaProducer();

        logger.debug("→ Sending message to PRIMARY topic: {}", primaryTopic);

        // Always publish to primary topic
        currentProducer.send(message, primaryTopic);

        logger.debug("✓ Message sent to topic '{}'", primaryTopic);

        // Conditionally publish to guardrails topic
        if (config != null && config.isEnabled()) {
            try {
                String guardrailsTopic = config.getTopicName();
                logger.debug("→ Sending message to GUARDRAILS topic: {}", guardrailsTopic);
                currentProducer.send(message, guardrailsTopic);
                logger.debug("✓ Message sent to guardrails topic '{}'", guardrailsTopic);
            } catch (Exception e) {
                // Don't fail the whole operation if guardrails send fails
                logger.error("Failed to send message to guardrails topic (non-fatal): {}", e.getMessage());
            }
        }
    }
}
