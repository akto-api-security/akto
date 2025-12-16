package com.akto.account_job_executor;

import com.akto.account_job_executor.cron.AccountJobsCron;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;

/**
 * Main entry point for the AccountJob Executor service.
 * This service polls AccountJob collection via Cyborg API and executes jobs.
 *
 * Required environment variables:
 * - DATABASE_ABSTRACTOR_SERVICE_URL: URL of the Cyborg service (e.g., https://cyborg.akto.io)
 * - DATABASE_ABSTRACTOR_SERVICE_TOKEN: JWT token for authentication
 *
 * Optional environment variables:
 * - AKTO_ACCOUNT_ID: Account ID for context (if needed)
 */
public class Main {

    private static final LoggerMaker logger = new LoggerMaker(Main.class);

    public static void main(String[] args) {
        logger.info("===========================================");
        logger.info("Starting Account Job Executor Service");
        logger.info("===========================================");

        // Validate required environment variables
        String cyborgUrl = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");

        if (cyborgUrl == null || cyborgUrl.isEmpty()) {
            logger.error("FATAL: DATABASE_ABSTRACTOR_SERVICE_URL environment variable not set");
            logger.error("Please set DATABASE_ABSTRACTOR_SERVICE_URL to the Cyborg service URL");
            System.exit(1);
        }

        if (token == null || token.isEmpty()) {
            logger.error("FATAL: DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable not set");
            logger.error("Please set DATABASE_ABSTRACTOR_SERVICE_TOKEN to a valid JWT token");
            System.exit(1);
        }

        logger.info("Environment configuration:");
        logger.info("  Cyborg URL: {}", cyborgUrl);
        logger.info("  Token: {}...", token.substring(0, Math.min(20, token.length())));

        // Set account context if provided
        String accountIdStr = System.getenv("AKTO_ACCOUNT_ID");
        if (accountIdStr != null && !accountIdStr.isEmpty()) {
            try {
                int accountId = Integer.parseInt(accountIdStr);
                Context.accountId.set(accountId);
                logger.info("  Account ID: {}", accountId);
            } catch (NumberFormatException e) {
                logger.error("Invalid AKTO_ACCOUNT_ID: {}. Must be an integer.", accountIdStr);
                System.exit(1);
            }
        } else {
            logger.info("  Account ID: Not set (will be determined from job context)");
        }

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("===========================================");
            logger.info("Shutting down Account Job Executor Service");
            logger.info("===========================================");

            try {
                AccountJobsCron.instance.shutdown();
                logger.info("Shutdown complete");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Start the job scheduler
        try {
            logger.info("Starting AccountJobsCron scheduler...");
            AccountJobsCron.instance.startScheduler();

            logger.info("===========================================");
            logger.info("Account Job Executor Service started successfully (API-only mode)");
            logger.info("Service is now polling for jobs every 5 seconds");
            logger.info("Press Ctrl+C to stop");
            logger.info("===========================================");

        } catch (Exception e) {
            logger.error("FATAL: Failed to start AccountJobsCron scheduler", e);
            System.exit(1);
        }

        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted, shutting down...");
            Thread.currentThread().interrupt();
        }
    }
}
