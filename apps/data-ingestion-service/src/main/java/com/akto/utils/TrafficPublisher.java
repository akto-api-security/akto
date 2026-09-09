package com.akto.utils;

/**
 * Abstraction over where traffic messages are delivered.
 * Implementations: {@link TopicPublisher} (Kafka), {@link HttpTrafficPublisher} (HTTP ingest API).
 */
public interface TrafficPublisher {
    void publish(String message, String primaryTopic, boolean publishToGuardrails);
}
