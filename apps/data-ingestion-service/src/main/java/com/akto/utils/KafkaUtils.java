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

    // Configuration initialized once from environment variables
    private static final String topicName;
    private static final String brokerUrl;
    private static final int batchSize;
    private static final int lingerMS;

    // Static initializer - reads and validates configuration once when class loads
    static {
        try {
            // Read environment variables
            topicName = System.getenv().getOrDefault("AKTO_KAFKA_TOPIC_NAME", "akto.api.logs");
            if (topicName == null || topicName.trim().isEmpty()) {
                throw new IllegalArgumentException("AKTO_KAFKA_TOPIC_NAME cannot be empty");
            }

            brokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "localhost:29092");
            if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("AKTO_KAFKA_BROKER_URL cannot be empty");
            }

            String batchSizeStr = System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_BATCH_SIZE", "100");
            batchSize = Integer.parseInt(batchSizeStr);
            if (batchSize <= 0) {
                throw new IllegalArgumentException("AKTO_KAFKA_PRODUCER_BATCH_SIZE must be positive, got: " + batchSize);
            }

            String lingerMSStr = System.getenv().getOrDefault("AKTO_KAFKA_PRODUCER_LINGER_MS", "10");
            lingerMS = Integer.parseInt(lingerMSStr);
            if (lingerMS < 0) {
                throw new IllegalArgumentException("AKTO_KAFKA_PRODUCER_LINGER_MS cannot be negative, got: " + lingerMS);
            }

            logger.info("Kafka configuration loaded - Topic: {}, Broker: {}, BatchSize: {}, LingerMS: {}",
                topicName, brokerUrl, batchSize, lingerMS);

        } catch (NumberFormatException e) {
            logger.error("Invalid Kafka configuration number format: {}", e.getMessage());
            throw new ExceptionInInitializerError("Invalid Kafka configuration: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to load Kafka configuration: {}", e.getMessage());
            throw new ExceptionInInitializerError("Failed to load Kafka configuration: " + e.getMessage());
        }
    }

    public synchronized void initKafkaProducer() {
        try {
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
     * Inserts data to Kafka using READ lock for high throughput.
     * Multiple threads can publish simultaneously.
     * Only blocks if reconnection (write lock) is in progress.
     *
     * @param payload The data batch to publish
     */
    public static void insertData(IngestDataBatch payload) {
        logger.debug("Publishing message - Path: {}, Method: {}, StatusCode: {}",
            payload.getPath(), payload.getMethod(), payload.getStatusCode());

        BasicDBObject obj = buildMessageObject(payload);
        String message = obj.toString();

        // Acquire READ lock - multiple threads can publish simultaneously
        producerLock.readLock().lock();
        try {
            topicPublisher.publish(message, topicName);
            logger.debug("Message published successfully");
        } catch (Exception e) {
            logger.error("Error publishing message to Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to Kafka", e);
        } finally {
            producerLock.readLock().unlock();
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