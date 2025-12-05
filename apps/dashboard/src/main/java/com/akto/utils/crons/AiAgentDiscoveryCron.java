package com.akto.utils.crons;

import com.akto.dao.N8NImportInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.N8NImportInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AiAgentDiscoveryCron {

    public static final AiAgentDiscoveryCron instance = new AiAgentDiscoveryCron();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker logger = new LoggerMaker(AiAgentDiscoveryCron.class, LogDb.DASHBOARD);

    private static final int CRON_INTERVAL_SECONDS = 60; // Run every 60 seconds

    public void startScheduler() {
        logger.info("Starting AI Agent Discovery Cron Scheduler");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                logger.debug("Running AI Agent Discovery Cron");
                processConnectors();
            } catch (Exception e) {
                logger.error("Error in AI Agent Discovery Cron: " + e.getMessage(), LogDb.DASHBOARD);
                e.printStackTrace();
            }
        }, 0, CRON_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void processConnectors() {
        // Find all connectors that are not in SCHEDULING or SCHEDULED state
        Bson filter = Filters.and(
            Filters.nin("status", N8NImportInfo.STATUS_SCHEDULING, N8NImportInfo.STATUS_SCHEDULED)
        );

        List<N8NImportInfo> connectors = N8NImportInfoDao.instance.getMCollection()
            .find(filter)
            .into(new ArrayList<>());

        logger.info("Found " + connectors.size() + " connectors to schedule");

        for (N8NImportInfo connector : connectors) {
            try {
                scheduleConnector(connector);
            } catch (Exception e) {
                logger.error("Error scheduling connector " + connector.getHexId() + ": " + e.getMessage(), LogDb.DASHBOARD);
                updateConnectorStatus(connector.getId().toString(), N8NImportInfo.STATUS_FAILED_SCHEDULING, e.getMessage());
            }
        }
    }

    private void scheduleConnector(N8NImportInfo connector) {
        String connectorId = connector.getHexId();
        String type = connector.getType();
        Map<String, String> config = connector.getConfig();

        logger.info("Scheduling connector: " + connectorId + " of type: " + type);

        // Update status to SCHEDULING
        updateConnectorStatus(connectorId, N8NImportInfo.STATUS_SCHEDULING, null);

        // Build docker compose command
        String dockerComposeFile = "~/docker/docker-compose-" + type.toLowerCase() + ".yaml";
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("compose");
        command.add("-f");
        command.add(dockerComposeFile);
        command.add("up");
        command.add("-d");

        // Prepare environment variables
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> env = processBuilder.environment();

        // Add all config values as environment variables
        if (config != null) {
            for (Map.Entry<String, String> entry : config.entrySet()) {
                env.put(entry.getKey(), entry.getValue());
                logger.debug("Setting env var: " + entry.getKey() + " = " + entry.getValue());
            }
        }

        processBuilder.directory(new File(System.getProperty("user.home")));
        processBuilder.redirectErrorStream(true);

        try {
            logger.info("Executing docker compose command: " + String.join(" ", command));
            Process process = processBuilder.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                logger.debug("Docker output: " + line);
            }

            // Wait for process to complete
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Successfully scheduled connector: " + connectorId);
                logger.info("Docker output: " + output.toString());
                updateConnectorStatus(connectorId, N8NImportInfo.STATUS_SCHEDULED, null);
            } else {
                String errorMsg = "Docker compose failed with exit code: " + exitCode + ". Output: " + output.toString();
                logger.error(errorMsg, LogDb.DASHBOARD);
                updateConnectorStatus(connectorId, N8NImportInfo.STATUS_FAILED_SCHEDULING, errorMsg);
            }

        } catch (Exception e) {
            String errorMsg = "Exception executing docker compose: " + e.getMessage();
            logger.error(errorMsg, LogDb.DASHBOARD);
            e.printStackTrace();
            updateConnectorStatus(connectorId, N8NImportInfo.STATUS_FAILED_SCHEDULING, errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    private void updateConnectorStatus(String connectorId, String status, String errorMessage) {
        try {
            Bson filter = Filters.eq("_id", new org.bson.types.ObjectId(connectorId));
            Bson update = Updates.combine(
                Updates.set("status", status),
                Updates.set("updatedTimestamp", Context.now()),
                Updates.set("errorMessage", errorMessage)
            );

            N8NImportInfoDao.instance.getMCollection().updateOne(filter, update);
            logger.info("Updated connector " + connectorId + " status to: " + status);
        } catch (Exception e) {
            logger.error("Failed to update connector status: " + e.getMessage(), LogDb.DASHBOARD);
        }
    }

    public void stopScheduler() {
        logger.info("Stopping AI Agent Discovery Cron Scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
