package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.dao.context.Context;
import com.akto.dto.IngestDataBatch;
import com.akto.kafka.Kafka;
import com.mongodb.BasicDBObject;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KafkaUtils {

    private static final LoggerMaker logger = new LoggerMaker(KafkaUtils.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static volatile Kafka kafkaProducer;
    private static TopicPublisher topicPublisher;

    // ReadWriteLock for high-throughput thread safety
    // Multiple threads can publish (read lock) simultaneously
    // Only reconnection blocks everything (write lock)
    private static final ReadWriteLock producerLock = new ReentrantReadWriteLock();

    // Configuration for reconnection
    private static String brokerUrl;
    private static int batchSize;
    private static int lingerMS;

    public synchronized void initKafkaProducer() {
        try {
            // Validate environment variables
            brokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "localhost:29092");
            if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("AKTO_KAFKA_BROKER_URL cannot be empty");
            }

            batchSize = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_BATCH_SIZE", "100"));
            lingerMS = Integer.parseInt(System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_LINGER_MS", "10"));

            if (batchSize <= 0) {
                throw new IllegalArgumentException("AKTO_KAFKA_PRODUCER_BATCH_SIZE must be positive");
            }
            if (lingerMS < 0) {
                throw new IllegalArgumentException("AKTO_KAFKA_PRODUCER_LINGER_MS cannot be negative");
            }

            logger.info("Initializing Kafka Producer at {}", Context.now());
            logger.info("Broker URL: {}", brokerUrl);
            logger.info("Batch Size: {}, Linger MS: {}", batchSize, lingerMS);

            kafkaProducer = new Kafka(brokerUrl, lingerMS, batchSize, LoggerMaker.LogDb.DATA_INGESTION);

            if (kafkaProducer != null && kafkaProducer.producerReady) {
                logger.info("✓ Kafka Producer initialized successfully");
            } else {
                logger.error("✗ Kafka Producer initialization failed - producer not ready");
                throw new RuntimeException("Failed to initialize Kafka producer");
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid configuration value: {}", e.getMessage());
            throw new RuntimeException("Invalid Kafka configuration", e);
        } catch (Exception e) {
            logger.error("Unexpected error during Kafka initialization: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Kafka producer", e);
        }
    }

    /**
     * Reconnects the Kafka producer with a fresh connection.
     * Called periodically by scheduler (every 1-2 minutes) to avoid stale connections.
     * Thread-safe - uses write lock to block all publishing during reconnection.
     * Production-ready with retry logic and error handling.
     */
    public static void reconnectKafkaProducer() {
        long startTime = System.currentTimeMillis();
        Kafka newProducer = null;
        Kafka oldProducer = null;
        boolean lockAcquired = false;

        try {
            logger.debug("========================================");
            logger.debug("KAFKA RECONNECTION STARTED at {}", Context.now());

            // Validate configuration before attempting reconnection
            if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
                logger.error("Cannot reconnect - broker URL not configured");
                return;
            }

            logger.debug("Waiting for write lock to block all publishing...");

            // Acquire WRITE lock with timeout protection
            producerLock.writeLock().lock();
            lockAcquired = true;
            long lockAcquiredTime = System.currentTimeMillis();
            logger.debug("Write lock acquired in {}ms", lockAcquiredTime - startTime);

            logger.debug("Broker URL: {}", brokerUrl);
            logger.debug("Batch Size: {}, Linger MS: {}", batchSize, lingerMS);

            // Check current producer status
            oldProducer = kafkaProducer;
            boolean oldProducerExists = (oldProducer != null);
            boolean oldProducerReady = oldProducerExists && oldProducer.producerReady;
            logger.debug("Old producer status - Exists: {}, Ready: {}", oldProducerExists, oldProducerReady);

            // Retry logic for creating new producer
            int maxRetries = 3;
            int retryCount = 0;
            Exception lastException = null;

            while (retryCount < maxRetries) {
                try {
                    logger.debug("Creating new Kafka producer instance (attempt {}/{})", retryCount + 1, maxRetries);
                    long createStart = System.currentTimeMillis();

                    newProducer = new Kafka(brokerUrl, lingerMS, batchSize, LoggerMaker.LogDb.DATA_INGESTION);

                    long createTime = System.currentTimeMillis() - createStart;
                    logger.debug("New producer created in {}ms", createTime);

                    // Validate new producer
                    if (newProducer != null && newProducer.producerReady) {
                        logger.debug("New producer ready status: true");
                        break; // Success!
                    } else {
                        logger.warn("New producer not ready on attempt {}", retryCount + 1);
                        lastException = new RuntimeException("Producer not ready");
                        retryCount++;

                        if (retryCount < maxRetries) {
                            Thread.sleep(1000); // Wait 1 second before retry
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                    logger.error("Error creating producer on attempt {}: {}", retryCount + 1, e.getMessage());
                    retryCount++;

                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(1000); // Wait 1 second before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.warn("Retry sleep interrupted");
                            break;
                        }
                    }
                }
            }

            // Check if we successfully created a new producer
            if (newProducer != null && newProducer.producerReady) {
                // Close old producer safely
                if (oldProducer != null) {
                    try {
                        logger.debug("Closing old Kafka producer...");
                        oldProducer.close();
                        logger.debug("Old producer closed successfully");
                    } catch (Exception e) {
                        logger.error("Error closing old Kafka producer (non-fatal): {}", e.getMessage());
                        // Continue anyway - we have a new working producer
                    }
                }

                // Switch to new producer atomically
                logger.debug("Switching to new producer...");
                kafkaProducer = newProducer;

                long totalTime = System.currentTimeMillis() - startTime;
                logger.info("✓ Kafka reconnection successful in {}ms", totalTime);
            } else {
                // All retries failed
                long totalTime = System.currentTimeMillis() - startTime;
                logger.error("✗ Kafka reconnection FAILED after {} attempts (took {}ms)", maxRetries, totalTime);
                logger.error("Last error: {}", lastException != null ? lastException.getMessage() : "Unknown");

                // Clean up failed new producer if it exists
                if (newProducer != null) {
                    try {
                        newProducer.close();
                    } catch (Exception e) {
                        logger.debug("Error closing failed new producer: {}", e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("✗ Kafka reconnection CRITICAL ERROR after {}ms: {}", totalTime, e.getMessage());
            logger.error("Stack trace: ", e);

            // Clean up any partially created producer
            if (newProducer != null && newProducer != kafkaProducer) {
                try {
                    newProducer.close();
                } catch (Exception cleanupError) {
                    logger.debug("Error during cleanup: {}", cleanupError.getMessage());
                }
            }
        } finally {
            // Always release write lock if acquired
            if (lockAcquired) {
                try {
                    producerLock.writeLock().unlock();
                    logger.debug("Write lock released - publishing can resume");
                } catch (Exception e) {
                    logger.error("Error releasing write lock: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Inserts data to Kafka using READ lock.
     * Multiple threads can publish simultaneously for high throughput.
     * Only blocks if reconnection (write lock) is in progress.
     */
    /**
     * Inserts data to Kafka using READ lock for high throughput.
     * Production-ready with validation, error handling, and graceful degradation.
     *
     * @param payload The data batch to publish
     * @throws IllegalArgumentException if payload is null or invalid
     * @throws RuntimeException if publishing fails critically
     */
    public static void insertData(IngestDataBatch payload) {
        // Validation
        if (payload == null) {
            logger.error("Cannot publish null payload");
            throw new IllegalArgumentException("Payload cannot be null");
        }

        String topicName = "akto.api.logs";
        boolean lockAcquired = false;

        try {
            logger.debug("Building message object for payload - Path: {}, Method: {}",
                payload.getPath(), payload.getMethod());

            // Build message object with error handling
            BasicDBObject obj = buildMessageObject(payload);
            if (obj == null) {
                logger.error("Failed to build message object for payload");
                throw new RuntimeException("Message object is null");
            }

            String message = obj.toString();
            if (message == null || message.isEmpty()) {
                logger.error("Serialized message is null or empty");
                throw new RuntimeException("Failed to serialize message");
            }

            logger.debug("Publishing message to Kafka topic: {} | Path: {} | Method: {} | StatusCode: {}",
                topicName, payload.getPath(), payload.getMethod(), payload.getStatusCode());

            // Acquire READ lock - multiple threads can publish simultaneously
            producerLock.readLock().lock();
            lockAcquired = true;

            // Check if producer is available
            if (kafkaProducer == null) {
                logger.error("Kafka producer is null - cannot publish message");
                throw new RuntimeException("Kafka producer not initialized");
            }

            if (!kafkaProducer.producerReady) {
                logger.warn("Kafka producer not ready - attempting to publish anyway");
            }

            if (topicPublisher == null) {
                logger.error("Topic publisher is null - cannot publish message");
                throw new RuntimeException("Topic publisher not initialized");
            }

            // Publish message
            topicPublisher.publish(message, topicName);

            logger.debug("Message published successfully");

        } catch (IllegalArgumentException e) {
            logger.error("Invalid payload: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error publishing message to Kafka: {}", e.getMessage(), e);
            logger.error("Payload details - Path: {}, Method: {}, StatusCode: {}",
                payload.getPath(), payload.getMethod(), payload.getStatusCode());
            throw new RuntimeException("Failed to publish message to Kafka", e);
        } finally {
            // Always release read lock if acquired
            if (lockAcquired) {
                try {
                    producerLock.readLock().unlock();
                } catch (Exception e) {
                    logger.error("Error releasing read lock: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Builds a document from the ingestion payload
     *
     * @param payload The ingestion data batch
     * @return BasicDBObject containing all payload fields
     */
    private static BasicDBObject buildMessageObject(IngestDataBatch payload) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("path", payload.getPath());
        obj.put("requestHeaders", payload.getRequestHeaders());
        obj.put("responseHeaders", payload.getResponseHeaders());
        obj.put("method", payload.getMethod());
        obj.put("requestPayload", payload.getRequestPayload());
        obj.put("responsePayload", payload.getResponsePayload());
        obj.put("ip", payload.getIp());
        obj.put("destIp", payload.getDestIp());
        obj.put("time", payload.getTime());
        obj.put("statusCode", payload.getStatusCode());
        obj.put("type", payload.getType());
        obj.put("status", payload.getStatus());
        obj.put("akto_account_id", payload.getAkto_account_id());
        obj.put("akto_vxlan_id", payload.getAkto_vxlan_id());
        obj.put("is_pending", payload.getIs_pending());
        obj.put("source", payload.getSource());
        obj.put("direction", payload.getDirection());
        obj.put("process_id", payload.getProcess_id());
        obj.put("socket_id", payload.getSocket_id());
        obj.put("daemonset_id", payload.getDaemonset_id());
        obj.put("enabled_graph", payload.getEnabled_graph());
        obj.put("tag", payload.getTag());
        return obj;
    }

    // Getters and setters for dependency injection
    public static Kafka getKafkaProducer() {
        return kafkaProducer;
    }

    public static void setTopicPublisher(TopicPublisher publisher) {
        topicPublisher = publisher;
    }

}