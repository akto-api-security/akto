package com.akto.standalone;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.crons.AiAgentDiscoveryCron;
import com.mongodb.ConnectionString;

public class AiAgentDiscoveryStandalone {

    private static final LoggerMaker logger = new LoggerMaker(AiAgentDiscoveryStandalone.class, LogDb.DASHBOARD);

    public static void main(String[] args) {
        logger.info("Starting AI Agent Discovery Standalone Application");

        // Get MongoDB connection string from environment variable
        String mongoUri = System.getenv("AKTO_MONGO_CONN");
        if (mongoUri == null || mongoUri.isEmpty()) {
            logger.error("AKTO_MONGO_CONN environment variable not set", LogDb.DASHBOARD);
            System.err.println("ERROR: AKTO_MONGO_CONN environment variable is required");
            System.err.println("Example: export AKTO_MONGO_CONN='mongodb://localhost:27017/admini'");
            System.exit(1);
        }

        // Get account ID from environment variable (default to 1000000 if not set)
        String accountIdStr = System.getenv("AKTO_ACCOUNT_ID");
        int accountId = 1000000;
        if (accountIdStr != null && !accountIdStr.isEmpty()) {
            try {
                accountId = Integer.parseInt(accountIdStr);
            } catch (NumberFormatException e) {
                logger.error("Invalid AKTO_ACCOUNT_ID: " + accountIdStr + ", using default: 1000000", LogDb.DASHBOARD);
            }
        }

        logger.info("MongoDB URI: " + mongoUri);
        logger.info("Account ID: " + accountId);

        try {
            // Initialize MongoDB connection
            logger.info("Initializing MongoDB connection...");
            DaoInit.init(new ConnectionString(mongoUri));
            logger.info("MongoDB connection initialized successfully");

            // Set account context
            Context.accountId.set(accountId);

            // Start the cron scheduler
            logger.info("Starting AI Agent Discovery Cron Scheduler...");
            AiAgentDiscoveryCron.instance.startScheduler();
            logger.info("AI Agent Discovery Cron Scheduler started successfully");

            // Keep the application running
            logger.info("Application is running. Press Ctrl+C to stop.");

            // Add shutdown hook to gracefully stop the scheduler
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down AI Agent Discovery Cron Scheduler...");
                AiAgentDiscoveryCron.instance.stopScheduler();
                logger.info("Shutdown complete");
            }));

            // Keep main thread alive
            while (true) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    logger.info("Application interrupted, shutting down...");
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Fatal error: " + e.getMessage(), LogDb.DASHBOARD);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
