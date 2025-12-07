package com.akto.action;

import com.akto.dao.AIAgentConnectorInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AIAgentConnectorInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;

import java.util.HashMap;
import java.util.Map;

public class CopilotStudioImportAction extends UserAction {

    private String appInsightsAppId;
    private String appInsightsApiKey;
    private String dataIngestionUrl;
    private AIAgentConnectorInfo createdImportInfo;

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotStudioImportAction.class, LoggerMaker.LogDb.DASHBOARD);

    public String initiateCopilotStudioImport() {
        try {
            // Print the filled data to console
            loggerMaker.info("=== Copilot Studio Import Request ===", LogDb.DASHBOARD);
            loggerMaker.info("App Insights App ID: " + appInsightsAppId, LogDb.DASHBOARD);
            loggerMaker.info("App Insights API Key: " + appInsightsApiKey, LogDb.DASHBOARD);
            loggerMaker.info("Data Ingestion Service URL: " + dataIngestionUrl, LogDb.DASHBOARD);
            loggerMaker.info("========================", LogDb.DASHBOARD);

            // Print to standard output as well
            System.out.println("\n=== Copilot Studio Import Request ===");
            System.out.println("App Insights App ID: " + appInsightsAppId);
            System.out.println("App Insights API Key: " + appInsightsApiKey);
            System.out.println("Data Ingestion Service URL: " + dataIngestionUrl);
            System.out.println("========================\n");

            // Create the collection if it doesn't exist and set up indices
            AIAgentConnectorInfoDao.instance.createIndicesIfAbsent();

            // Get current timestamp
            int currentTimestamp = Context.now();

            // Create config map
            Map<String, String> config = new HashMap<>();
            config.put(AIAgentConnectorInfo.CONFIG_APPINSIGHTS_APP_ID, appInsightsAppId);
            config.put(AIAgentConnectorInfo.CONFIG_APPINSIGHTS_API_KEY, appInsightsApiKey);
            config.put(AIAgentConnectorInfo.CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

            // Create AIAgentConnectorInfo object with default status CREATED and type COPILOT_STUDIO
            createdImportInfo = new AIAgentConnectorInfo(
                AIAgentConnectorInfo.TYPE_COPILOT_STUDIO,
                config,
                currentTimestamp,
                currentTimestamp,
                AIAgentConnectorInfo.STATUS_CREATED,
                null
            );

            // Insert the document into the collection
            AIAgentConnectorInfoDao.instance.insertOne(createdImportInfo);

            loggerMaker.info("Successfully saved Copilot Studio Import data to collection: " + AIAgentConnectorInfoDao.COLLECTION_NAME + " with type: " + AIAgentConnectorInfo.TYPE_COPILOT_STUDIO + " and status: " + AIAgentConnectorInfo.STATUS_CREATED, LogDb.DASHBOARD);
            System.out.println("Successfully saved Copilot Studio Import data to collection: " + AIAgentConnectorInfoDao.COLLECTION_NAME);
            System.out.println("Type: " + AIAgentConnectorInfo.TYPE_COPILOT_STUDIO);
            System.out.println("Status: " + AIAgentConnectorInfo.STATUS_CREATED);
            System.out.println("Document ID: " + createdImportInfo.getHexId());

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating Copilot Studio Import. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();

            // Try to save error information to collection
            try {
                int currentTimestamp = Context.now();

                // Create config map
                Map<String, String> config = new HashMap<>();
                config.put(AIAgentConnectorInfo.CONFIG_APPINSIGHTS_APP_ID, appInsightsAppId);
                config.put(AIAgentConnectorInfo.CONFIG_APPINSIGHTS_API_KEY, appInsightsApiKey);
                config.put(AIAgentConnectorInfo.CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

                createdImportInfo = new AIAgentConnectorInfo(
                    AIAgentConnectorInfo.TYPE_COPILOT_STUDIO,
                    config,
                    currentTimestamp,
                    currentTimestamp,
                    AIAgentConnectorInfo.STATUS_FAILED_SCHEDULING,
                    e.getMessage()
                );
                AIAgentConnectorInfoDao.instance.insertOne(createdImportInfo);
            } catch (Exception insertException) {
                loggerMaker.error("Failed to save error information to collection: " + insertException.getMessage(), LogDb.DASHBOARD);
            }

            return Action.ERROR.toUpperCase();
        }
    }

    // Getters and Setters
    public String getAppInsightsAppId() {
        return appInsightsAppId;
    }

    public void setAppInsightsAppId(String appInsightsAppId) {
        this.appInsightsAppId = appInsightsAppId;
    }

    public String getAppInsightsApiKey() {
        return appInsightsApiKey;
    }

    public void setAppInsightsApiKey(String appInsightsApiKey) {
        this.appInsightsApiKey = appInsightsApiKey;
    }

    public String getDataIngestionUrl() {
        return dataIngestionUrl;
    }

    public void setDataIngestionUrl(String dataIngestionUrl) {
        this.dataIngestionUrl = dataIngestionUrl;
    }

    public AIAgentConnectorInfo getCreatedImportInfo() {
        return createdImportInfo;
    }

    public void setCreatedImportInfo(AIAgentConnectorInfo createdImportInfo) {
        this.createdImportInfo = createdImportInfo;
    }
}
