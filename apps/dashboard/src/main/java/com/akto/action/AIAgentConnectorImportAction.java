package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dto.jobs.AIAgentConnectorSyncJobParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.jobs.JobScheduler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.CONNECTOR_TYPE_N8N;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONNECTOR_TYPE_LANGCHAIN;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONNECTOR_TYPE_COPILOT_STUDIO;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_DATA_INGESTION_SERVICE_URL;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_N8N_BASE_URL;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_N8N_API_KEY;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_LANGSMITH_BASE_URL;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_LANGSMITH_API_KEY;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_APPINSIGHTS_APP_ID;
import static com.akto.jobs.executors.AIAgentConnectorConstants.CONFIG_APPINSIGHTS_API_KEY;
import static com.akto.jobs.executors.AIAgentConnectorConstants.DEFAULT_RECURRING_INTERVAL_SECONDS;
import static com.akto.jobs.executors.AIAgentConnectorUtils.isValidConnectorType;

/**
 * Unified action for importing AI Agent Connector data (N8N, Langchain, Copilot Studio).
 * This action schedules recurring jobs to sync data from various AI agent platforms.
 */
@Getter
@Setter
public class AIAgentConnectorImportAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AIAgentConnectorImportAction.class, LogDb.DASHBOARD);

    // Action parameters
    private String connectorType;
    private String dataIngestionUrl;
    private String jobId;
    private Integer recurringIntervalSeconds;

    // N8N-specific parameters
    private String n8nUrl;
    private String n8nApiKey;

    // Langchain-specific parameters
    private String langsmithUrl;
    private String langsmithApiKey;

    // Copilot Studio-specific parameters
    private String appInsightsAppId;
    private String appInsightsApiKey;

    /**
     * Unified method to initiate import for any AI Agent Connector.
     * The connector type is determined by the connectorType parameter.
     */
    public String initiateImport() {
        try {
            loggerMaker.info("Initiating import for connector type: " + connectorType, LogDb.DASHBOARD);

            // Validate connector type
            if (!isValidConnectorType(connectorType)) {
                loggerMaker.error("Invalid connector type: " + connectorType, LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            // Build configuration based on connector type
            Map<String, String> config = buildConfig();
            if (config == null) {
                return Action.ERROR.toUpperCase();
            }

            // Determine recurring interval (use provided value or default)
            int interval = (recurringIntervalSeconds != null && recurringIntervalSeconds > 0)
                ? recurringIntervalSeconds
                : DEFAULT_RECURRING_INTERVAL_SECONDS;

            // Schedule recurring job using JobScheduler
            Job job = JobScheduler.scheduleRecurringJob(
                Context.accountId.get(),
                new AIAgentConnectorSyncJobParams(
                    connectorType,
                    config,
                    Context.now()
                ),
                JobExecutorType.DASHBOARD,
                interval
            );

            if (job == null) {
                loggerMaker.error("Failed to schedule recurring job for " + connectorType + " connector", LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            this.jobId = job.getId().toHexString();
            loggerMaker.info("Successfully scheduled recurring job for " + connectorType + " connector with job ID: " + this.jobId + ", interval: " + interval + "s", LogDb.DASHBOARD);

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error initiating " + connectorType + " import: " + e.getMessage(), LogDb.DASHBOARD);
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Builds configuration map based on connector type.
     */
    private Map<String, String> buildConfig() {
        Map<String, String> config = new HashMap<>();
        config.put(CONFIG_DATA_INGESTION_SERVICE_URL, dataIngestionUrl);

        switch (connectorType) {
            case CONNECTOR_TYPE_N8N:
                if (n8nUrl == null || n8nUrl.isEmpty() || n8nApiKey == null || n8nApiKey.isEmpty()) {
                    loggerMaker.error("Missing required N8N configuration", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_N8N_BASE_URL, n8nUrl);
                config.put(CONFIG_N8N_API_KEY, n8nApiKey);
                break;

            case CONNECTOR_TYPE_LANGCHAIN:
                if (langsmithUrl == null || langsmithUrl.isEmpty() || langsmithApiKey == null || langsmithApiKey.isEmpty()) {
                    loggerMaker.error("Missing required Langchain configuration", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_LANGSMITH_BASE_URL, langsmithUrl);
                config.put(CONFIG_LANGSMITH_API_KEY, langsmithApiKey);
                break;

            case CONNECTOR_TYPE_COPILOT_STUDIO:
                if (appInsightsAppId == null || appInsightsAppId.isEmpty() || appInsightsApiKey == null || appInsightsApiKey.isEmpty()) {
                    loggerMaker.error("Missing required Copilot Studio configuration", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_APPINSIGHTS_APP_ID, appInsightsAppId);
                config.put(CONFIG_APPINSIGHTS_API_KEY, appInsightsApiKey);
                break;

            default:
                loggerMaker.error("Unsupported connector type: " + connectorType, LogDb.DASHBOARD);
                return null;
        }

        return config;
    }
}
