package com.akto.hybrid_runtime.consumer;

import com.akto.hybrid_runtime.Main;
import com.akto.ingest.TrafficIngestQueue;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs {@link Main#kafkaSubscribeAndProcess} on a background daemon thread,
 * enqueuing each Kafka record into {@link TrafficIngestQueue}.
 *
 * The {@link Consumer} instance is owned by the caller (Main) so that existing
 * shutdown-hook and metrics-scheduler code in Main can still reference it directly.
 */
public class KafkaTrafficConsumer extends TrafficConsumer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaTrafficConsumer.class, LogDb.RUNTIME);

    private final Consumer<String, String> consumer;
    private final String topicName;
    private final TrafficIngestQueue queue;
    private final AtomicBoolean exceptionOnCommitSync;

    public KafkaTrafficConsumer(Consumer<String, String> consumer, String topicName,
            TrafficIngestQueue queue, AtomicBoolean exceptionOnCommitSync) {
        this.consumer = consumer;
        this.topicName = topicName;
        this.queue = queue;
        this.exceptionOnCommitSync = exceptionOnCommitSync;
    }

    @Override
    public void start() {
        Thread thread = new Thread(
                () -> Main.kafkaSubscribeAndProcess(topicName, queue, consumer, exceptionOnCommitSync),
                "kafka-traffic-consumer");
        thread.setDaemon(true);
        thread.start();
        loggerMaker.infoAndAddToDb("KafkaTrafficConsumer started for topic: " + topicName);
    }

    @Override
    public void stop() {
        consumer.wakeup();
    }

    public Map<MetricName, ? extends Metric> getMetrics() {
        return consumer.metrics();
    }
}
