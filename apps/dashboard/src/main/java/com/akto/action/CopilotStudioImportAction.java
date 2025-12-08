package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dto.jobs.AIAgentConnectorSyncJobParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.jobs.JobScheduler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;

import java.util.HashMap;
import java.util.Map;

public class CopilotStudioImportAction extends UserAction {

    private static final String CONNECTOR_TYPE_COPILOT_STUDIO = "COPILOT_STUDIO";
    private static final String CONFIG_APPINSIGHTS_APP_ID = "APPINSIGHTS_APP_ID";
    private static final String CONFIG_APPINSIGHTS_API_KEY = "APPINSIGHTS_API_KEY";
    private static final String CONFIG_DATA_INGESTION_SERVICE_URL = "DATA_INGESTION_SERVICE_URL";

    private String appInsightsAppId;
    private String appInsightsApiKey;
    private String dataIngestionUrl;
    private String jobId;

    private static final LoggerMaker loggerMaker = new LoggerMaker(CopilotStudioImportAction.class, LoggerMaker.LogDb.DASHBOARD);
    private static final int CONNECTOR_SYNC_JOB_RECURRING_INTERVAL_SECONDS = 3600;

    public String initiateCopilotStudioImport() {
        try {
            loggerMaker.info("=== Copilot Studio Import Request ===", LogDb.DASHBOARD);
            loggerMaker.info("App Insights App ID: " + appInsightsAppId, LogDb.DASHBOARD);
            loggerMaker.info("Data Ingestion Service URL: " + dataIngestionUrl, LogDb.DASHBOARD);

            // Create config map
            Map<String, String> config = new HashMap<>();
            config.put(CONFIG_APPINSIGHTS_APP_ID, appInsightsAppId);
            config.put(CONFIG_APPINSIGHTS_API_KEY, appInsightsApiKey);
            config.put(CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

            // Schedule recurring job using JobScheduler - job params will store the config
            Job job = JobScheduler.scheduleRecurringJob(
                Context.accountId.get(),
                new AIAgentConnectorSyncJobParams(
                    CONNECTOR_TYPE_COPILOT_STUDIO,
                    config,
                    Context.now()
                ),
                JobExecutorType.DASHBOARD,
                CONNECTOR_SYNC_JOB_RECURRING_INTERVAL_SECONDS
            );

            if (job == null) {
                loggerMaker.error("Failed to schedule recurring job for Copilot Studio connector", LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            this.jobId = job.getId().toHexString();
            loggerMaker.info("Successfully scheduled recurring job for Copilot Studio connector with job ID: " + this.jobId, LogDb.DASHBOARD);

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating Copilot Studio Import. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
