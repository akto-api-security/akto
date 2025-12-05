package com.akto.action;

import com.akto.dao.N8NImportInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.N8NImportInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;

public class N8NImportAction extends UserAction {

    private String n8nUrl;
    private String apiKey;
    private String dataIngestionUrl;
    private String dashboardUrl;
    private N8NImportInfo createdImportInfo;

    private static final LoggerMaker loggerMaker = new LoggerMaker(N8NImportAction.class, LoggerMaker.LogDb.DASHBOARD);

    public String initiateN8NImport() {
        try {
            // Print the filled data to console
            loggerMaker.info("=== N8N Import Request ===", LogDb.DASHBOARD);
            loggerMaker.info("N8N URL: " + n8nUrl, LogDb.DASHBOARD);
            loggerMaker.info("API Key: " + apiKey, LogDb.DASHBOARD);
            loggerMaker.info("Data Ingestion Service URL: " + dataIngestionUrl, LogDb.DASHBOARD);
            loggerMaker.info("Dashboard URL: " + dashboardUrl, LogDb.DASHBOARD);
            loggerMaker.info("========================", LogDb.DASHBOARD);

            // Print to standard output as well
            System.out.println("\n=== N8N Import Request ===");
            System.out.println("N8N URL: " + n8nUrl);
            System.out.println("API Key: " + apiKey);
            System.out.println("Data Ingestion Service URL: " + dataIngestionUrl);
            System.out.println("Dashboard URL: " + dashboardUrl);
            System.out.println("========================\n");

            // Create the collection if it doesn't exist and set up indices
            N8NImportInfoDao.instance.createIndicesIfAbsent();

            // Get current timestamp
            int currentTimestamp = Context.now();

            // Create N8NImportInfo object with default status CREATED
            createdImportInfo = new N8NImportInfo(
                n8nUrl,
                apiKey,
                dataIngestionUrl,
                dashboardUrl,
                currentTimestamp,
                currentTimestamp,
                N8NImportInfo.STATUS_CREATED,
                null
            );

            // Insert the document into the collection
            N8NImportInfoDao.instance.insertOne(createdImportInfo);

            loggerMaker.info("Successfully saved N8N Import data to collection: " + N8NImportInfoDao.COLLECTION_NAME + " with status: " + N8NImportInfo.STATUS_CREATED, LogDb.DASHBOARD);
            System.out.println("Successfully saved N8N Import data to collection: " + N8NImportInfoDao.COLLECTION_NAME);
            System.out.println("Status: " + N8NImportInfo.STATUS_CREATED);
            System.out.println("Document ID: " + createdImportInfo.getHexId());

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating N8N Import. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();

            // Try to save error information to collection
            try {
                int currentTimestamp = Context.now();
                createdImportInfo = new N8NImportInfo(
                    n8nUrl,
                    apiKey,
                    dataIngestionUrl,
                    dashboardUrl,
                    currentTimestamp,
                    currentTimestamp,
                    N8NImportInfo.STATUS_FAILED_SCHEDULING,
                    e.getMessage()
                );
                N8NImportInfoDao.instance.insertOne(createdImportInfo);
            } catch (Exception insertException) {
                loggerMaker.error("Failed to save error information to collection: " + insertException.getMessage(), LogDb.DASHBOARD);
            }

            return Action.ERROR.toUpperCase();
        }
    }

    // Getters and Setters
    public String getN8nUrl() {
        return n8nUrl;
    }

    public void setN8nUrl(String n8nUrl) {
        this.n8nUrl = n8nUrl;
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

    public String getDashboardUrl() {
        return dashboardUrl;
    }

    public void setDashboardUrl(String dashboardUrl) {
        this.dashboardUrl = dashboardUrl;
    }

    public N8NImportInfo getCreatedImportInfo() {
        return createdImportInfo;
    }

    public void setCreatedImportInfo(N8NImportInfo createdImportInfo) {
        this.createdImportInfo = createdImportInfo;
    }
}
