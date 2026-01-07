package com.akto.utils;

import com.akto.config.GuardrailsConfig;
import com.akto.kafka.Kafka;
import com.akto.action.IngestionAction;

/**
 * Handles publishing messages to multiple Kafka topics based on configuration.
 * Encapsulates the logic for conditional multi-topic publishing.
 */
public class TopicPublisher {

    private final Kafka kafkaProducer;
    private final GuardrailsConfig config;

    public TopicPublisher(Kafka kafkaProducer, GuardrailsConfig config) {
        this.kafkaProducer = kafkaProducer;
        this.config = config;
    }

    /**
     * Publishes a message to the primary topic and optionally to guardrails topic
     *
     * @param message The message to publish
     * @param primaryTopic The primary destination topic
     */
    public void publish(String message, String primaryTopic) {
        // Always publish to primary topic
        kafkaProducer.send(message, primaryTopic);
        IngestionAction.printLogs("Inserted to kafka: " + message);

        // Conditionally publish to guardrails topic
        if (config.isEnabled()) {
            kafkaProducer.send(message, config.getTopicName());
            IngestionAction.printLogs("Inserted to guardrails kafka: " + message);
        }
    }
}
