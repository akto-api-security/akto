package com.akto.hybrid_runtime.consumer;

import com.akto.ingest.TrafficIngestQueue;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * Reads raw traffic messages from a Kafka topic and enqueues them into
 * {@link TrafficIngestQueue} for processing by {@link InMemoryTrafficConsumer}.
 *
 * Runs in a background daemon thread. On Kafka failure the thread logs the error
 * and exits — the HTTP ingest path continues to work independently.
 */
public class KafkaTrafficConsumer extends TrafficConsumer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaTrafficConsumer.class, LogDb.RUNTIME);

    private final KafkaConsumer<String, String> consumer;
    private final String topicName;
    private final TrafficIngestQueue queue;

    public KafkaTrafficConsumer(Properties kafkaProperties, String topicName, TrafficIngestQueue queue) {
        this.consumer = new KafkaConsumer<>(kafkaProperties);
        this.topicName = topicName;
        this.queue = queue;
    }

    @Override
    public void start() {
        Thread thread = new Thread(this::pollLoop, "kafka-traffic-consumer");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        consumer.wakeup();
    }

    /** Exposes Kafka consumer metrics for the existing metrics scheduler in Main. */
    public Map<MetricName, ? extends Metric> getMetrics() {
        return consumer.metrics();
    }

    private void pollLoop() {
        try {
            consumer.subscribe(Arrays.asList(topicName));
            loggerMaker.infoAndAddToDb("KafkaTrafficConsumer subscribed to " + topicName);
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10_000));
                try {
                    consumer.commitSync();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "KafkaTrafficConsumer commitSync failed: " + e.getMessage());
                    throw e;
                }
                for (ConsumerRecord<String, String> r : records) {
                    String value = r.value();
                    if (value != null && !value.isEmpty()) {
                        if (!queue.offer(value)) {
                            loggerMaker.errorAndAddToDb("TrafficIngestQueue full — dropping Kafka message");
                        }
                    }
                }
            }
        } catch (WakeupException ignored) {
            loggerMaker.infoAndAddToDb("KafkaTrafficConsumer stopped");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "KafkaTrafficConsumer error, thread exiting: " + e.getMessage());
        } finally {
            consumer.close();
        }
    }
}
