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

public class LangchainImportAction extends UserAction {

    private static final String CONNECTOR_TYPE_LANGCHAIN = "LANGCHAIN";
    private static final String CONFIG_LANGSMITH_BASE_URL = "LANGSMITH_BASE_URL";
    private static final String CONFIG_LANGSMITH_API_KEY = "LANGSMITH_API_KEY";
    private static final String CONFIG_DATA_INGESTION_SERVICE_URL = "DATA_INGESTION_SERVICE_URL";

    private String langsmithUrl;
    private String apiKey;
    private String dataIngestionUrl;
    private String jobId;

    private static final LoggerMaker loggerMaker = new LoggerMaker(LangchainImportAction.class, LoggerMaker.LogDb.DASHBOARD);
    private static final int CONNECTOR_SYNC_JOB_RECURRING_INTERVAL_SECONDS = 10;

    public String initiateLangchainImport() {
        try {
            loggerMaker.info("=== Langchain Import Request ===", LogDb.DASHBOARD);
            loggerMaker.info("LangSmith URL: " + langsmithUrl, LogDb.DASHBOARD);
            loggerMaker.info("Data Ingestion Service URL: " + dataIngestionUrl, LogDb.DASHBOARD);

            // Create config map
            Map<String, String> config = new HashMap<>();
            config.put(CONFIG_LANGSMITH_BASE_URL, langsmithUrl);
            config.put(CONFIG_LANGSMITH_API_KEY, apiKey);
            config.put(CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

            // Schedule recurring job using JobScheduler - job params will store the config
            Job job = JobScheduler.scheduleRecurringJob(
                Context.accountId.get(),
                new AIAgentConnectorSyncJobParams(
                    CONNECTOR_TYPE_LANGCHAIN,
                    config,
                    Context.now()
                ),
                JobExecutorType.DASHBOARD,
                CONNECTOR_SYNC_JOB_RECURRING_INTERVAL_SECONDS
            );

            if (job == null) {
                loggerMaker.error("Failed to schedule recurring job for Langchain connector", LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            this.jobId = job.getId().toHexString();
            loggerMaker.info("Successfully scheduled recurring job for Langchain connector with job ID: " + this.jobId, LogDb.DASHBOARD);

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating Langchain Import. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
