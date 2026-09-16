package com.akto.utils;

import com.akto.config.GuardrailsConfig;
import com.akto.kafka.Kafka;

/**
 * Handles publishing messages to multiple Kafka topics based on configuration.
 * Encapsulates the logic for conditional multi-topic publishing.
 */
public class TopicPublisher implements TrafficPublisher {

    private final Kafka kafkaProducer;
    private final GuardrailsConfig config;

    public TopicPublisher(Kafka kafkaProducer, GuardrailsConfig config) {
        this.kafkaProducer = kafkaProducer;
        this.config = config;
    }

    @Override
    public void publish(String message, String primaryTopic, boolean publishToGuardrails) {
        send(message, primaryTopic);

        if (publishToGuardrails && config.isEnabled()) {
            send(message, config.getTopicName());
        }
    }

    private void send(String message, String topic) {
        final String account = OperationalAlerts.deploymentAccountId();
        kafkaProducer.send(message, topic, (metadata, error) -> {
            if (error != null) {
                OperationalAlerts.send("kafka:" + account + ":" + topic,
                        "Kafka message delivery failed\nAccount: " + account
                        + "\nTopic: " + OperationalAlerts.label(topic)
                        + "\nError type: " + error.getClass().getSimpleName());
            }
        });
    }
}
