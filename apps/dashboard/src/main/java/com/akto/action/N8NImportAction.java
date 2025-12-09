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

public class N8NImportAction extends UserAction {

    private static final String CONNECTOR_TYPE_N8N = "N8N";
    private static final String CONFIG_N8N_BASE_URL = "N8N_BASE_URL";
    private static final String CONFIG_N8N_API_KEY = "N8N_API_KEY";
    private static final String CONFIG_DATA_INGESTION_SERVICE_URL = "DATA_INGESTION_SERVICE_URL";

    private String n8nUrl;
    private String apiKey;
    private String dataIngestionUrl;
    private String jobId;

    private static final LoggerMaker loggerMaker = new LoggerMaker(N8NImportAction.class, LoggerMaker.LogDb.DASHBOARD);
    private static final int CONNECTOR_SYNC_JOB_RECURRING_INTERVAL_SECONDS = 300;

    public String initiateN8NImport() {
        try {
            loggerMaker.info("=== N8N Import Request ===", LogDb.DASHBOARD);
            loggerMaker.info("N8N URL: " + n8nUrl, LogDb.DASHBOARD);
            loggerMaker.info("Data Ingestion Service URL: " + dataIngestionUrl, LogDb.DASHBOARD);

            // Create config map
            Map<String, String> config = new HashMap<>();
            config.put(CONFIG_N8N_BASE_URL, n8nUrl);
            config.put(CONFIG_N8N_API_KEY, apiKey);
            config.put(CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

            // Schedule recurring job using JobScheduler - job params will store the config
            Job job = JobScheduler.scheduleRecurringJob(
                Context.accountId.get(),
                new AIAgentConnectorSyncJobParams(
                    CONNECTOR_TYPE_N8N,
                    config,
                    Context.now()
                ),
                JobExecutorType.DASHBOARD,
                CONNECTOR_SYNC_JOB_RECURRING_INTERVAL_SECONDS
            );

            if (job == null) {
                loggerMaker.error("Failed to schedule recurring job for N8N connector", LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            this.jobId = job.getId().toHexString();
            loggerMaker.info("Successfully scheduled recurring job for N8N connector with job ID: " + this.jobId, LogDb.DASHBOARD);

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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
