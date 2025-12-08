package com.akto.action;

import com.akto.dao.AIAgentConnectorInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.AIAgentConnectorInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;

import java.util.HashMap;
import java.util.Map;

public class LangchainImportAction extends UserAction {

    private String langsmithUrl;
    private String apiKey;
    private String dataIngestionUrl;
    private AIAgentConnectorInfo createdImportInfo;

    private static final LoggerMaker loggerMaker = new LoggerMaker(LangchainImportAction.class, LoggerMaker.LogDb.DASHBOARD);

    public String initiateLangchainImport() {
        try {
            loggerMaker.info("=== Langchain Import Request ===", LogDb.DASHBOARD);
            loggerMaker.info("LangSmith URL: " + langsmithUrl, LogDb.DASHBOARD);
            loggerMaker.info("Data Ingestion Service URL: " + dataIngestionUrl, LogDb.DASHBOARD);

            // Create the collection if it doesn't exist and set up indices
            AIAgentConnectorInfoDao.instance.createIndicesIfAbsent();

            // Get current timestamp
            int currentTimestamp = Context.now();

            // Create config map
            Map<String, String> config = new HashMap<>();
            config.put(AIAgentConnectorInfo.CONFIG_LANGSMITH_BASE_URL, langsmithUrl);
            config.put(AIAgentConnectorInfo.CONFIG_LANGSMITH_API_KEY, apiKey);
            config.put(AIAgentConnectorInfo.CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

            // Create AIAgentConnectorInfo object with default status CREATED and type LANGCHAIN
            createdImportInfo = new AIAgentConnectorInfo(
                AIAgentConnectorInfo.TYPE_LANGCHAIN,
                config,
                currentTimestamp,
                currentTimestamp,
                AIAgentConnectorInfo.STATUS_CREATED,
                null
            );

            // Insert the document into the collection
            AIAgentConnectorInfoDao.instance.insertOne(createdImportInfo);

            loggerMaker.info("Successfully saved Langchain Import data to collection: " + AIAgentConnectorInfoDao.COLLECTION_NAME + " with type: " + AIAgentConnectorInfo.TYPE_LANGCHAIN + " and status: " + AIAgentConnectorInfo.STATUS_CREATED + ", Document ID: " + createdImportInfo.getHexId(), LogDb.DASHBOARD);

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating Langchain Import. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();

            // Try to save error information to collection
            try {
                int currentTimestamp = Context.now();

                // Create config map
                Map<String, String> config = new HashMap<>();
                config.put(AIAgentConnectorInfo.CONFIG_LANGSMITH_BASE_URL, langsmithUrl);
                config.put(AIAgentConnectorInfo.CONFIG_LANGSMITH_API_KEY, apiKey);
                config.put(AIAgentConnectorInfo.CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

                createdImportInfo = new AIAgentConnectorInfo(
                    AIAgentConnectorInfo.TYPE_LANGCHAIN,
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
    public String getLangsmithUrl() {
        return langsmithUrl;
    }

    public void setLangsmithUrl(String langsmithUrl) {
        this.langsmithUrl = langsmithUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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
