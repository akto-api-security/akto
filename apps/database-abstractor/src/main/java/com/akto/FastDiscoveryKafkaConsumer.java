package com.akto;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.KafkaUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FastDiscoveryKafkaConsumer - Dedicated consumer for fast-discovery writes.
 *
 * Subscribes to: akto.fast-discovery.writes
 * Consumer Group: fast-discovery-consumer
 * Processing: Minimal validation, direct DB writes (STI + API Info only)
 *
 * This consumer runs independently from the main consumer, processing only
 * fast-discovery traffic for quick API discovery with <1 second latency.
 */
public class FastDiscoveryKafkaConsumer implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FastDiscoveryKafkaConsumer.class, LogDb.DB_ABS);

    private final KafkaConsumer<String, String> consumer;
    private final AtomicBoolean running;
    private final String topicName;
    private long totalProcessed = 0;

    public FastDiscoveryKafkaConsumer(
            String brokerUrl,
            String topicName,
            String groupId,
            int maxPollRecords
    ) {
        this.topicName = topicName;
        this.running = new AtomicBoolean(true);

        // Create Kafka consumer with appropriate configuration
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");  // Manual commit for reliability
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");  // Start from latest for new consumer

        this.consumer = new KafkaConsumer<>(props);

        loggerMaker.infoAndAddToDb("FastDiscoveryKafkaConsumer initialized: topic=" + topicName +
            ", group=" + groupId + ", broker=" + brokerUrl, LogDb.DB_ABS);
    }

    public void start() {
        // Subscribe to fast-discovery topic
        consumer.subscribe(Collections.singletonList(topicName));
        loggerMaker.infoAndAddToDb("FastDiscoveryKafkaConsumer subscribed to topic: " + topicName, LogDb.DB_ABS);

        // Start consumer thread
        Thread consumerThread = new Thread(this, "fast-discovery-consumer");
        consumerThread.setDaemon(false);
        consumerThread.start();

        loggerMaker.infoAndAddToDb("FastDiscoveryKafkaConsumer thread started", LogDb.DB_ABS);
    }

    @Override
    public void run() {
        loggerMaker.infoAndAddToDb("FastDiscoveryKafkaConsumer: Starting consumer loop", LogDb.DB_ABS);

        try {
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));

                    if (!records.isEmpty()) {
                        loggerMaker.infoAndAddToDb("FastDiscoveryConsumer: Processing " +
                            records.count() + " messages", LogDb.DB_ABS);

                        for (ConsumerRecord<String, String> record : records) {
                            try {
                                processMessage(record);
                                totalProcessed++;
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb(e, "Error processing single message: " +
                                    e.getMessage(), LogDb.DB_ABS);
                                // Continue processing other messages
                            }
                        }

                        // Commit offset after processing batch
                        try {
                            consumer.commitSync();
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error committing offset: " + e.getMessage(), LogDb.DB_ABS);
                        }

                        // Log progress every 10K messages
                        if (totalProcessed % 10_000 == 0) {
                            loggerMaker.infoAndAddToDb("FastDiscoveryConsumer: Total processed: " +
                                totalProcessed, LogDb.DB_ABS);
                        }
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    // Expected on shutdown
                    break;
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in consumer loop: " +
                        e.getMessage(), LogDb.DB_ABS);
                    // Continue processing despite errors
                }
            }
        } finally {
            try {
                consumer.close();
                loggerMaker.infoAndAddToDb("FastDiscoveryKafkaConsumer stopped. Total processed: " +
                    totalProcessed, LogDb.DB_ABS);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error closing consumer: " + e.getMessage(), LogDb.DB_ABS);
            }
        }
    }

    /**
     * Process a single Kafka message from fast-discovery topic.
     * Message format matches the standard format used by KafkaUtils:
     * {
     *   "triggerMethod": "bulkWriteSti" or "bulkWriteApiInfo",
     *   "payload": "<JSON string of BulkUpdates>",
     *   "accountId": <int>,
     *   "source": "fast-discovery"
     * }
     */
    private void processMessage(ConsumerRecord<String, String> record) {
        String message = record.value();
        try {
            // Reuse shared message parsing and trigger logic from KafkaUtils
            KafkaUtils.parseAndTriggerWrites(message);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to process fast-discovery message: " +
                e.getMessage(), LogDb.DB_ABS);
        }
    }

    public void shutdown() {
        loggerMaker.infoAndAddToDb("FastDiscoveryKafkaConsumer shutdown initiated", LogDb.DB_ABS);
        running.set(false);
        consumer.wakeup();
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }
}
