package com.akto.hybrid_runtime;

import com.akto.data_actor.ClientActor;
import com.akto.dto.ApiCollection;
import com.akto.fast_discovery.*;
import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FastDiscoveryService - Wrapper service for running fast-discovery within mini-runtime.
 *
 * Encapsulates fast-discovery's lifecycle (initialize, run, stop, cleanup) as a Runnable
 * service that runs in a dedicated background thread.
 *
 * When ENABLE_FAST_DISCOVERY=true, mini-runtime creates an instance of this class,
 * calls initialize(), and starts it in a background thread.
 */
public class FastDiscoveryService implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FastDiscoveryService.class);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private KafkaConsumer<String, String> kafkaConsumer;
    private FastDiscoveryConsumer consumer;

    // Configuration from environment
    private final String kafkaBrokerUrl;
    private final String kafkaTopicName;
    private final String kafkaGroupId;
    private final int kafkaMaxPollRecords;
    private final long bloomFilterExpectedSize;
    private final double bloomFilterFpp;

    /**
     * Constructor - loads configuration from environment variables.
     * Uses same environment variables as standalone fast-discovery.
     */
    public FastDiscoveryService() {
        this.kafkaBrokerUrl = getEnv("AKTO_KAFKA_BROKER_URL", "localhost:9092");
        this.kafkaTopicName = getEnv("AKTO_KAFKA_TOPIC_NAME", "akto.api.logs");
        // Use dedicated consumer group for fast-discovery to avoid conflicts with mini-runtime
        this.kafkaGroupId = getEnv("FAST_DISCOVERY_KAFKA_GROUP_ID", "fast-discovery");
        this.kafkaMaxPollRecords = Integer.parseInt(getEnv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "1000"));
        this.bloomFilterExpectedSize = Long.parseLong(getEnv("BLOOM_FILTER_EXPECTED_SIZE", "10000000"));
        this.bloomFilterFpp = Double.parseDouble(getEnv("BLOOM_FILTER_FPP", "0.01"));

        logConfig();
    }

    /**
     * Initialize fast-discovery components synchronously in calling thread.
     * This allows initialization failures to be caught before starting the background thread.
     *
     * Initialization sequence (matches fast-discovery Main.java):
     * 1. ClientActor (database operations)
     * 2. DatabaseAbstractorClient (HTTP wrapper)
     * 3. BloomFilterManager (10-30 second initialization)
     * 4. ApiCollectionResolver (pre-populate collection cache)
     * 5. FastDiscoveryConsumer
     * 6. Kafka consumer setup
     *
     * @throws Exception if any component fails to initialize
     */
    public void initialize() throws Exception {
        loggerMaker.infoAndAddToDb("Initializing Fast-Discovery Service...");

        // 1. Initialize ClientActor for database operations
        ClientActor clientActor = new ClientActor();
        loggerMaker.infoAndAddToDb("ClientActor initialized");

        // 2. Initialize DatabaseAbstractorClient
        DatabaseAbstractorClient dbAbstractorClient = new DatabaseAbstractorClient(clientActor);
        loggerMaker.infoAndAddToDb("DatabaseAbstractorClient initialized");

        // 3. Initialize Bloom Filter Manager (10-30 seconds)
        loggerMaker.infoAndAddToDb("Initializing Bloom filter (this may take 10-30 seconds)...");
        BloomFilterManager bloomFilter = new BloomFilterManager(
                dbAbstractorClient,
                bloomFilterExpectedSize,
                bloomFilterFpp
        );
        bloomFilter.initialize();
        loggerMaker.infoAndAddToDb("BloomFilterManager initialized with " +
                bloomFilter.getEstimatedMemoryUsageMB() + " MB estimated memory");

        // 4. Initialize API Collection Resolver
        ApiCollectionResolver collectionResolver = new ApiCollectionResolver(clientActor);
        loggerMaker.infoAndAddToDb("ApiCollectionResolver initialized");

        // 5. Pre-populate collection cache from database
        loggerMaker.infoAndAddToDb("Pre-populating collection cache...");
        try {
            List<ApiCollection> existingCollections = dbAbstractorClient.fetchAllCollections();
            collectionResolver.prePopulateCollections(existingCollections);
            loggerMaker.infoAndAddToDb("Collection cache pre-populated with " +
                    existingCollections.size() + " collections (~" +
                    (collectionResolver.getCacheSize() * 40 / 1024) + " KB)");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to pre-populate collections: " + e.getMessage());
            loggerMaker.infoAndAddToDb("Continuing anyway - collections will be created on-demand");
        }

        // 6. Initialize Fast Discovery Consumer
        this.consumer = new FastDiscoveryConsumer(
                bloomFilter,
                collectionResolver,
                dbAbstractorClient
        );
        loggerMaker.infoAndAddToDb("FastDiscoveryConsumer initialized");

        // 7. Set up Kafka consumer
        this.kafkaConsumer = createKafkaConsumer();
        kafkaConsumer.subscribe(Collections.singletonList(kafkaTopicName));
        loggerMaker.infoAndAddToDb("Kafka consumer subscribed to topic: " + kafkaTopicName);

        loggerMaker.infoAndAddToDb("Fast-Discovery Service initialized successfully");
    }

    /**
     * Main run loop - runs in dedicated background thread.
     * Polls Kafka, processes batches, logs progress, handles errors.
     */
    @Override
    public void run() {
        loggerMaker.infoAndAddToDb("Fast-Discovery Service started, beginning to process messages...");
        long totalProcessed = 0;

        try {
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
                    if (!records.isEmpty()) {
                        consumer.processBatch(records);
                        totalProcessed += records.count();

                        // Log progress every 10K messages
                        if (totalProcessed % 10_000 == 0) {
                            loggerMaker.infoAndAddToDb("Fast-Discovery: Total messages processed: " + totalProcessed);
                        }
                    }
                } catch (WakeupException e) {
                    // Expected on shutdown
                    break;
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Fast-Discovery: Error processing batch: " + e.getMessage());
                    e.printStackTrace();
                    // Continue processing despite errors
                }
            }
        } finally {
            cleanup();
        }

        loggerMaker.infoAndAddToDb("Fast-Discovery Service stopped. Total messages processed: " + totalProcessed);
    }

    /**
     * Stop the service gracefully.
     * Called by mini-runtime's shutdown hook.
     */
    public void stop() {
        loggerMaker.infoAndAddToDb("Fast-Discovery Service stop() called");
        running.set(false);
        if (kafkaConsumer != null) {
            kafkaConsumer.wakeup();
        }
    }

    /**
     * Cleanup resources (close Kafka consumer).
     * Called automatically in finally block of run().
     */
    private void cleanup() {
        if (kafkaConsumer != null) {
            try {
                kafkaConsumer.close();
                loggerMaker.infoAndAddToDb("Fast-Discovery Kafka consumer closed");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error closing Fast-Discovery Kafka consumer: " + e.getMessage());
            }
        }
    }

    /**
     * Create Kafka consumer with configuration.
     */
    private KafkaConsumer<String, String> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaMaxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        return new KafkaConsumer<>(props);
    }

    /**
     * Get environment variable with default value.
     */
    private String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Log configuration.
     */
    private void logConfig() {
        loggerMaker.infoAndAddToDb("=== Fast-Discovery Configuration ===");
        loggerMaker.infoAndAddToDb("Kafka Broker: " + kafkaBrokerUrl);
        loggerMaker.infoAndAddToDb("Kafka Topic: " + kafkaTopicName);
        loggerMaker.infoAndAddToDb("Kafka Group ID: " + kafkaGroupId);
        loggerMaker.infoAndAddToDb("Kafka Max Poll Records: " + kafkaMaxPollRecords);
        loggerMaker.infoAndAddToDb("Bloom Filter Expected Size: " + bloomFilterExpectedSize);
        loggerMaker.infoAndAddToDb("Bloom Filter FPP: " + bloomFilterFpp);
        loggerMaker.infoAndAddToDb("===================================");
    }
}
