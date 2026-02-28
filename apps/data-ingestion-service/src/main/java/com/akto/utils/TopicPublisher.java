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

    public void publish(String message, String primaryTopic, boolean publishToGuardrails) {
        kafkaProducer.send(message, primaryTopic);
        IngestionAction.printLogs("Inserted to kafka: " + message);

        if (publishToGuardrails && config.isEnabled()) {
            kafkaProducer.send(message, config.getTopicName());
            IngestionAction.printLogs("Inserted to guardrails kafka: " + message);
        }
    }
}
