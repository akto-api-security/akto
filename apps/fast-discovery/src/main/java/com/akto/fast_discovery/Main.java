package com.akto.fast_discovery;

import com.akto.RuntimeMode;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main - Entry point for Fast-Discovery Consumer.
 *
 * Sets up Kafka consumer and initializes all components:
 * - DataActor (ClientActor for hybrid mode, DbActor for normal mode)
 * - BloomFilterManager (duplicate detection)
 * - Hostname cache (collection ID resolution)
 * - FastDiscoveryConsumer (processing pipeline)
 *
 * Consumes from akto.api.logs topic with separate consumer group "fast-discovery".
 */
public class Main {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static void main(String[] args) {
        loggerMaker.infoAndAddToDb("Starting Fast-Discovery Consumer...");

        // Load configuration
        Config config = loadConfiguration();

        // Initialize components
        KafkaConsumer<String, String> kafkaConsumer = null;

        try {
            BloomFilterManager bloomFilter = initializeBloomFilter(config);
            Map<String, Integer> hostnameCache = buildHostnameCache();
            FastDiscoveryConsumer consumer = initializeFastDiscoveryConsumer(bloomFilter, hostnameCache);

            schedulePeriodicRefresh(consumer, bloomFilter);
            kafkaConsumer = setupKafkaConsumer(config);
            registerShutdownHook(kafkaConsumer);

            processMessages(kafkaConsumer, consumer);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Fatal error in Fast-Discovery Consumer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            cleanup(kafkaConsumer);
        }
    }

    /**
     * Initialize Bloom Filter Manager with configuration.
     * Loads existing APIs from database into Bloom filter for duplicate detection.
     */
    private static BloomFilterManager initializeBloomFilter(Config config) {
        BloomFilterManager bloomFilter = new BloomFilterManager(
                config.bloomFilterExpectedSize,
                config.bloomFilterFpp
        );
        loggerMaker.infoAndAddToDb("Initializing Bloom filter (this may take 10-30 seconds)...");
        bloomFilter.initialize();
        loggerMaker.infoAndAddToDb("BloomFilterManager initialized with " +
                bloomFilter.getEstimatedMemoryUsageMB() + " MB estimated memory");
        return bloomFilter;
    }

    /**
     * Build hostname → collectionId cache from database.
     * Returns empty map if fetch fails (collections will be created on-demand).
     */
    private static Map<String, Integer> buildHostnameCache() {
        Map<String, Integer> hostnameToCollectionId = new HashMap<>();

        try {
            List<ApiCollection> existingCollections = dataActor.fetchAllCollections();

            for (ApiCollection col : existingCollections) {
                if (col.getHostName() != null && !col.getHostName().isEmpty()) {
                    String normalizedHostname = col.getHostName().toLowerCase().trim();
                    hostnameToCollectionId.put(normalizedHostname, col.getId());
                }
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to build hostname cache: " + e.getMessage());
        }

        return hostnameToCollectionId;
    }

    /**
     * Initialize FastDiscoveryConsumer with Bloom filter and hostname cache.
     */
    private static FastDiscoveryConsumer initializeFastDiscoveryConsumer(
            BloomFilterManager bloomFilter,
            Map<String, Integer> hostnameCache) {
        return new FastDiscoveryConsumer(bloomFilter, hostnameCache);
    }

    /**
     * Schedule periodic refresh tasks for collections cache and Bloom filter.
     * Both refresh every 15 minutes to stay in sync with mini-runtime.
     */
    private static void schedulePeriodicRefresh(
            FastDiscoveryConsumer consumer,
            BloomFilterManager bloomFilter) {

        // Collections cache refresh: Every 15 minutes
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(Context.getActualAccountId());
                    consumer.refreshCollectionsCache();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error refreshing collections cache: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 15, 15, TimeUnit.MINUTES);

        // Bloom filter refresh: Every 15 minutes
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(Context.getActualAccountId());
                    bloomFilter.refresh();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error refreshing Bloom filter: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 15, 15, TimeUnit.MINUTES);
    }

    /**
     * Setup and subscribe Kafka consumer to topic.
     */
    private static KafkaConsumer<String, String> setupKafkaConsumer(Config config) {
        KafkaConsumer<String, String> kafkaConsumer = createKafkaConsumer(config);
        kafkaConsumer.subscribe(Collections.singletonList(config.kafkaTopicName));
        loggerMaker.infoAndAddToDb("Kafka consumer subscribed to topic: " + config.kafkaTopicName);
        return kafkaConsumer;
    }

    /**
     * Register shutdown hook to cleanup resources gracefully.
     * Stops scheduler, wakes up Kafka consumer, and waits for termination.
     */
    private static void registerShutdownHook(KafkaConsumer<String, String> kafkaConsumer) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            loggerMaker.infoAndAddToDb("Shutdown signal received, cleaning up...");
            running.set(false);

            // Shutdown scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }

            // Wake up Kafka consumer
            if (kafkaConsumer != null) {
                kafkaConsumer.wakeup();
            }

            loggerMaker.infoAndAddToDb("Fast-Discovery Consumer shut down gracefully");
        }));
    }

    /**
     * Main message processing loop.
     * Polls Kafka for messages and processes batches until shutdown signal received.
     */
    private static void processMessages(
            KafkaConsumer<String, String> kafkaConsumer,
            FastDiscoveryConsumer consumer) {

        loggerMaker.infoAndAddToDb("Fast-Discovery Consumer started successfully! Beginning to process messages...");
        long totalProcessed = 0;

        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(1000));
                if (records.isEmpty()) {
                    continue;
                }

                consumer.processBatch(records);
                totalProcessed += records.count();

                // Log progress every 100K messages
                if (totalProcessed % 100_000 == 0) {
                    loggerMaker.infoAndAddToDb("Total messages processed: " + totalProcessed);
                }
            } catch (WakeupException e) {
                // Expected on shutdown
                break;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error processing batch: " + e.getMessage());
                e.printStackTrace();
                // Continue processing despite errors
            }
        }

        loggerMaker.infoAndAddToDb("Fast-Discovery Consumer stopped. Total messages processed: " + totalProcessed);
    }

    /**
     * Cleanup resources on shutdown.
     * Closes Kafka consumer gracefully.
     */
    private static void cleanup(KafkaConsumer<String, String> kafkaConsumer) {
        if (kafkaConsumer != null) {
            try {
                kafkaConsumer.close();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error closing Kafka consumer: " + e.getMessage());
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
        config.kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "localhost:9092");
        config.kafkaTopicName = System.getenv().getOrDefault("AKTO_KAFKA_TOPIC_NAME", "akto.api.logs");
        config.kafkaGroupId = System.getenv().getOrDefault("AKTO_KAFKA_GROUP_ID_CONFIG", "fast-discovery");
        config.kafkaMaxPollRecords = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "1000"));

        // Note: Database-Abstractor configuration (URL and JWT token) is read by ClientActor from environment variables

        // Bloom Filter configuration
        config.bloomFilterExpectedSize = Long.parseLong(System.getenv().getOrDefault("BLOOM_FILTER_EXPECTED_SIZE", "10000000"));
        config.bloomFilterFpp = Double.parseDouble(System.getenv().getOrDefault("BLOOM_FILTER_FPP", "0.01"));

        return config;
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
