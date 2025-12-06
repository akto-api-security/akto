package com.akto.listener;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.crons.AiAgentDiscoveryCron;

public class AiAgentDiscoveryListener extends AfterMongoConnectListener {

    private static final LoggerMaker logger = new LoggerMaker(AiAgentDiscoveryListener.class, LogDb.DASHBOARD);

    @Override
    public void runMainFunction() {
        try {
            logger.info("Starting AI Agent Discovery Cron Listener");
            AiAgentDiscoveryCron.instance.startScheduler();
            logger.info("AI Agent Discovery Cron Listener started successfully");
        } catch (Exception e) {
            logger.error("Error starting AI Agent Discovery Cron: " + e.getMessage(), LogDb.DASHBOARD);
            e.printStackTrace();
        }
    }

    @Override
    public int retryAfter() {
        // Retry after 60 seconds if initialization fails
        return 60;
    }
}
