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
     * Production-ready with validation and error handling.
     *
     * @param message The message to publish
     * @param primaryTopic The primary destination topic
     * @throws RuntimeException if publishing fails critically
     */
    public void publish(String message, String primaryTopic) {
        // Validation
        if (message == null || message.isEmpty()) {
            logger.error("Cannot publish null or empty message");
            throw new IllegalArgumentException("Message cannot be null or empty");
        }

        if (primaryTopic == null || primaryTopic.trim().isEmpty()) {
            logger.error("Cannot publish to null or empty topic");
            throw new IllegalArgumentException("Topic cannot be null or empty");
        }

        // Get current producer (supports reconnection)
        Kafka currentProducer = KafkaUtils.getKafkaProducer();

        if (currentProducer == null) {
            logger.error("Kafka producer not initialized, cannot publish message");
            throw new RuntimeException("Kafka producer not available");
        }

        if (!currentProducer.producerReady) {
            logger.warn("Kafka producer not ready - attempting to send anyway");
        }

        logger.debug("Getting current Kafka producer - Ready: {}", currentProducer.producerReady);

        // Publish to primary topic with error handling
        try {
            logger.debug("→ Sending message to PRIMARY topic: {}", primaryTopic);
            long sendStart = System.currentTimeMillis();

            currentProducer.send(message, primaryTopic);

            long sendTime = System.currentTimeMillis() - sendStart;
            logger.debug("✓ Message sent to topic '{}' in {}ms | Size: {} bytes",
                primaryTopic, sendTime, message.length());

        } catch (Exception e) {
            logger.error("Failed to send message to primary topic '{}': {}", primaryTopic, e.getMessage(), e);
            throw new RuntimeException("Failed to publish to primary topic: " + primaryTopic, e);
        }

        // Conditionally publish to guardrails topic
        if (config != null && config.isEnabled()) {
            try {
                String guardrailsTopic = config.getTopicName();

                if (guardrailsTopic == null || guardrailsTopic.trim().isEmpty()) {
                    logger.warn("Guardrails enabled but topic name is empty, skipping");
                    return;
                }

                logger.debug("→ Sending message to GUARDRAILS topic: {}", guardrailsTopic);
                long sendStart = System.currentTimeMillis();

                currentProducer.send(message, guardrailsTopic);

                long sendTime = System.currentTimeMillis() - sendStart;
                logger.debug("✓ Message sent to guardrails topic '{}' in {}ms",
                    guardrailsTopic, sendTime);

            } catch (Exception e) {
                // Don't fail the whole operation if guardrails send fails
                logger.error("Failed to send message to guardrails topic (non-fatal): {}", e.getMessage(), e);
            }
        } else {
            logger.debug("Guardrails disabled, skipping secondary topic");
        }
    }
}
