package com.akto.hybrid_runtime.consumer;

/**
 * Abstract base for traffic consumers. Implementations either pull from Kafka
 * ({@link KafkaTrafficConsumer}) or process from the shared in-memory queue
 * ({@link InMemoryTrafficConsumer}).
 */
public abstract class TrafficConsumer {
    public abstract void start();
    public abstract void stop();
}
