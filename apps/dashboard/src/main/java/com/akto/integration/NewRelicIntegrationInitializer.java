package com.akto.integration;

import com.akto.integration.services.NewRelicMetricsExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * NewRelic Integration Initializer
 * Called from InitializerListener to start NewRelic integration services
 *
 * INTEGRATION NOTES:
 * Add the following to InitializerListener.contextInitialized():
 *
 * // Initialize NewRelic integration (if feature enabled)
 * NewRelicIntegrationInitializer.initializeNewRelicIntegration();
 */
public class NewRelicIntegrationInitializer {

    private static final Logger logger = LoggerFactory.getLogger(NewRelicIntegrationInitializer.class);
    private static ExecutorService newrelicExecutor;
    private static NewRelicMetricsExportService exportService;

    /**
     * Initialize NewRelic integration services
     * Called from InitializerListener on application startup
     */
    public static synchronized void initializeNewRelicIntegration() {
        try {
            // Get configuration
            NewRelicConfiguration config = NewRelicConfiguration.getInstance();

            if (!config.isIntegrationEnabled()) {
                logger.info("NewRelic integration is disabled - skipping initialization");
                return;
            }

            logger.info("Initializing NewRelic integration...");

            // Create API client
            NewRelicApiClient apiClient = new NewRelicApiClient(config);

            // Create metrics export service
            exportService = new NewRelicMetricsExportService(config, apiClient);

            // Create executor for consumer service
            newrelicExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "NewRelic-Metrics-Export-Consumer");
                    t.setDaemon(true);
                    return t;
                }
            });

            // Start metrics export service in background
            newrelicExecutor.submit(exportService);

            logger.info("NewRelic integration initialized successfully");

            // Register shutdown hook for graceful shutdown
            registerShutdownHook();

        } catch (Exception e) {
            logger.error("Failed to initialize NewRelic integration", e);
            // Don't fail application startup - log but continue
        }
    }

    /**
     * Register shutdown hook for graceful service shutdown
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down NewRelic integration services...");
            shutdownNewRelicIntegration();
        }, "NewRelic-Shutdown-Hook"));
    }

    /**
     * Shutdown NewRelic integration services
     */
    public static synchronized void shutdownNewRelicIntegration() {
        try {
            if (exportService != null) {
                logger.info("Stopping NewRelic metrics export service...");
                exportService.stop();
                // Wait for service to finish processing
                Thread.sleep(2000);
            }

            if (newrelicExecutor != null) {
                logger.info("Shutting down NewRelic executor...");
                newrelicExecutor.shutdown();
                if (!newrelicExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.warn("NewRelic executor did not terminate gracefully, forcing shutdown");
                    newrelicExecutor.shutdownNow();
                }
            }

            logger.info("NewRelic integration services shut down successfully");
        } catch (Exception e) {
            logger.error("Error during NewRelic integration shutdown", e);
        }
    }

    /**
     * Get service metrics (for monitoring)
     */
    public static Object getServiceMetrics() {
        if (exportService != null) {
            return exportService.getMetrics();
        }
        return null;
    }
}
