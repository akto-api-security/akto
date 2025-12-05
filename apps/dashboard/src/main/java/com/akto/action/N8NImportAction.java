package com.akto.action;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;

public class N8NImportAction extends UserAction {

    private String n8nUrl;
    private String apiKey;
    private String dataIngestionUrl;
    private String dashboardUrl;

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

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating N8N Import. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
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
}
