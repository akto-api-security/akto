package com.akto.fast_discovery;

import com.akto.data_actor.ClientActor;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main - Entry point for Fast-Discovery Consumer.
 *
 * Sets up Kafka consumer and initializes all components:
 * - DatabaseAbstractorClient (HTTP client)
 * - BloomFilterManager (duplicate detection)
 * - ApiCollectionResolver (collection ID resolution)
 * - FastDiscoveryConsumer (processing pipeline)
 *
 * Consumes from akto.api.logs topic with separate consumer group "fast-discovery".
 */
public class Main {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);
    private static final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) {
        loggerMaker.infoAndAddToDb("Starting Fast-Discovery Consumer...");

        // Load configuration
        Config config = loadConfiguration();
        logConfig(config);

        // Initialize components
        DatabaseAbstractorClient dbAbstractorClient = null;
        KafkaConsumer<String, String> kafkaConsumer = null;

        try {
            // 1. Initialize ClientActor for database operations
            // Uses DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable
            ClientActor clientActor = new ClientActor();
            loggerMaker.infoAndAddToDb("ClientActor initialized");

            // 2. Initialize DatabaseAbstractorClient (using ClientActor for remote calls)
            dbAbstractorClient = new DatabaseAbstractorClient(clientActor);
            loggerMaker.infoAndAddToDb("DatabaseAbstractorClient initialized");

            // 3. Initialize Bloom Filter Manager
            BloomFilterManager bloomFilter = new BloomFilterManager(
                    dbAbstractorClient,
                    config.bloomFilterExpectedSize,
                    config.bloomFilterFpp
            );
            loggerMaker.infoAndAddToDb("Initializing Bloom filter (this may take 10-30 seconds)...");
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
            FastDiscoveryConsumer consumer = new FastDiscoveryConsumer(
                    bloomFilter,
                    collectionResolver,
                    dbAbstractorClient
            );
            loggerMaker.infoAndAddToDb("FastDiscoveryConsumer initialized");

            // 7. Set up Kafka consumer
            kafkaConsumer = createKafkaConsumer(config);
            kafkaConsumer.subscribe(Collections.singletonList(config.kafkaTopicName));
            loggerMaker.infoAndAddToDb("Kafka consumer subscribed to topic: " + config.kafkaTopicName);

            // 8. Set up shutdown hook
            final KafkaConsumer<String, String> finalKafkaConsumer = kafkaConsumer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                loggerMaker.infoAndAddToDb("Shutdown signal received, cleaning up...");
                running.set(false);
                if (finalKafkaConsumer != null) {
                    finalKafkaConsumer.wakeup();
                }
                loggerMaker.infoAndAddToDb("Fast-Discovery Consumer shut down gracefully");
            }));

            // 9. Start consuming messages
            loggerMaker.infoAndAddToDb("Fast-Discovery Consumer started successfully! Beginning to process messages...");
            long totalProcessed = 0;

            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
                    if (!records.isEmpty()) {
                        consumer.processBatch(records);
                        totalProcessed += records.count();

                        // Log progress every 10K messages
                        if (totalProcessed % 10_000 == 0) {
                            loggerMaker.infoAndAddToDb("Total messages processed: " + totalProcessed);
                        }
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    // Expected on shutdown
                    break;
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error processing batch: " + e.getMessage());
                    e.printStackTrace();
                    // Continue processing despite errors
                }
            }

            loggerMaker.infoAndAddToDb("Fast-Discovery Consumer stopped. Total messages processed: " + totalProcessed);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Fatal error in Fast-Discovery Consumer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Cleanup
            if (kafkaConsumer != null) {
                try {
                    kafkaConsumer.close();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error closing Kafka consumer: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Create Kafka consumer with configuration.
     */
    private static KafkaConsumer<String, String> createKafkaConsumer(Config config) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafkaBrokerUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.kafkaGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.kafkaMaxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        return new KafkaConsumer<>(props);
    }

    /**
     * Load configuration from environment variables.
     */
    private static Config loadConfiguration() {
        Config config = new Config();

        // Kafka configuration
        config.kafkaBrokerUrl = getEnv("AKTO_KAFKA_BROKER_URL", "localhost:9092");
        config.kafkaTopicName = getEnv("AKTO_KAFKA_TOPIC_NAME", "akto.api.logs");
        config.kafkaGroupId = getEnv("AKTO_KAFKA_GROUP_ID_CONFIG", "fast-discovery");
        config.kafkaMaxPollRecords = Integer.parseInt(getEnv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "1000"));

        // Note: Database-Abstractor configuration (URL and JWT token) is read by ClientActor from environment variables

        // Bloom Filter configuration
        config.bloomFilterExpectedSize = Long.parseLong(getEnv("BLOOM_FILTER_EXPECTED_SIZE", "10000000"));
        config.bloomFilterFpp = Double.parseDouble(getEnv("BLOOM_FILTER_FPP", "0.01"));

        return config;
    }

    /**
     * Get environment variable with default value.
     */
    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Log configuration (redact sensitive values).
     */
    private static void logConfig(Config config) {
        loggerMaker.infoAndAddToDb("=== Fast-Discovery Configuration ===");
        loggerMaker.infoAndAddToDb("Kafka Broker: " + config.kafkaBrokerUrl);
        loggerMaker.infoAndAddToDb("Kafka Topic: " + config.kafkaTopicName);
        loggerMaker.infoAndAddToDb("Kafka Group ID: " + config.kafkaGroupId);
        loggerMaker.infoAndAddToDb("Kafka Max Poll Records: " + config.kafkaMaxPollRecords);
        loggerMaker.infoAndAddToDb("Bloom Filter Expected Size: " + config.bloomFilterExpectedSize);
        loggerMaker.infoAndAddToDb("Bloom Filter FPP: " + config.bloomFilterFpp);
        loggerMaker.infoAndAddToDb("===================================");
    }

    /**
     * Configuration holder.
     */
    private static class Config {
        String kafkaBrokerUrl;
        String kafkaTopicName;
        String kafkaGroupId;
        int kafkaMaxPollRecords;
        long bloomFilterExpectedSize;
        double bloomFilterFpp;
    }
}
