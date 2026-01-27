package com.akto.listener;

import javax.servlet.ServletContextListener;

import com.akto.config.GuardrailsConfig;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.utils.KafkaUtils;
import com.akto.utils.TopicPublisher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class InitializerListener implements ServletContextListener {

    private static final LoggerMaker logger = new LoggerMaker(InitializerListener.class, LoggerMaker.LogDb.DATA_INGESTION);
    private ScheduledExecutorService kafkaReconnectScheduler;

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        // Initialize Kafka
        KafkaUtils kafkaUtils = new KafkaUtils();
        kafkaUtils.initKafkaProducer();

        // Initialize GuardrailsConfig and TopicPublisher
        GuardrailsConfig guardrailsConfig = GuardrailsConfig.getInstance();
        logger.error("Guardrails configuration: " + guardrailsConfig,
            LoggerMaker.LogDb.DATA_INGESTION);

        // Store publisher for use in KafkaUtils
        // TopicPublisher will get the current producer from KafkaUtils dynamically
        TopicPublisher topicPublisher = new TopicPublisher(guardrailsConfig);
        KafkaUtils.setTopicPublisher(topicPublisher);

        // Initialize DataActor
        DataActor dataActor = DataActorFactory.fetchInstance();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.DATA_INGESTION, dataActor);

        // Start periodic Kafka reconnection (every 2 minutes)
        startKafkaReconnectionScheduler();
    }

    /**
     * Starts a scheduled task to reconnect Kafka producer periodically
     * to maintain healthy connections and avoid stale connections.
     * Default: 60 seconds (configurable via AKTO_KAFKA_RECONNECT_INTERVAL_SECONDS env variable)
     */
    /**
     * Starts a scheduled task to reconnect Kafka producer periodically.
     * Production-ready with validation and error handling.
     */
    private void startKafkaReconnectionScheduler() {
        try {
            int reconnectIntervalSeconds;

            try {
                reconnectIntervalSeconds = Integer.parseInt(
                    System.getenv().getOrDefault("AKTO_KAFKA_RECONNECT_INTERVAL_SECONDS", "60")
                );
            } catch (NumberFormatException e) {
                logger.error("Invalid AKTO_KAFKA_RECONNECT_INTERVAL_SECONDS value, using default 60");
                reconnectIntervalSeconds = 60;
            }

            if (reconnectIntervalSeconds <= 0) {
                logger.info("Kafka reconnection disabled (interval <= 0)");
                return;
            }

            // Validate reasonable range (10 seconds to 1 hour)
            if (reconnectIntervalSeconds < 10) {
                logger.warn("Reconnection interval {} too short, setting to minimum 10 seconds", reconnectIntervalSeconds);
                reconnectIntervalSeconds = 10;
            }
            if (reconnectIntervalSeconds > 3600) {
                logger.warn("Reconnection interval {} too long, setting to maximum 3600 seconds", reconnectIntervalSeconds);
                reconnectIntervalSeconds = 3600;
            }

            final int finalInterval = reconnectIntervalSeconds;

            kafkaReconnectScheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "kafka-reconnection-scheduler");
                t.setDaemon(true); // Don't prevent JVM shutdown
                t.setUncaughtExceptionHandler((thread, throwable) -> {
                    logger.error("Uncaught exception in kafka reconnection thread: {}", throwable.getMessage(), throwable);
                });
                return t;
            });

            kafkaReconnectScheduler.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            logger.debug(">>> Kafka reconnection scheduler triggered");
                            KafkaUtils.reconnectKafkaProducer();
                        } catch (Throwable t) {
                            // Catch Throwable to prevent scheduler from dying
                            logger.error("Critical error in Kafka reconnection scheduler: {}", t.getMessage(), t);
                            // Don't rethrow - keep scheduler alive
                        }
                    }
                },
                finalInterval, // Initial delay
                finalInterval, // Period
                TimeUnit.SECONDS
            );

            logger.info("✓ Kafka reconnection scheduler started successfully");
            logger.info("Reconnection interval: {} second(s)", finalInterval);
            logger.info("Next reconnection scheduled in {} seconds", finalInterval);

        } catch (Exception e) {
            logger.error("Failed to start Kafka reconnection scheduler: {}", e.getMessage(), e);
            // Don't throw - service can still function without periodic reconnection
        }
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // Shutdown scheduler gracefully
        if (kafkaReconnectScheduler != null && !kafkaReconnectScheduler.isShutdown()) {
            logger.info("Shutting down Kafka reconnection scheduler...");
            kafkaReconnectScheduler.shutdown();
            try {
                if (!kafkaReconnectScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("Scheduler did not terminate in time, forcing shutdown");
                    kafkaReconnectScheduler.shutdownNow();
                }
                logger.info("✓ Kafka reconnection scheduler shut down successfully");
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for scheduler shutdown", e);
                kafkaReconnectScheduler.shutdownNow();
            }
        }
    }

}
