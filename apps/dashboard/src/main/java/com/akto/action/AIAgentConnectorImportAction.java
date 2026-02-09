package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;
import static com.akto.jobs.executors.AIAgentConnectorUtils.*;

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

    // Copilot Studio-specific parameters (Dataverse API)
    private String dataverseEnvironmentUrl;
    private String dataverseTenantId;
    private String dataverseClientId;
    private String dataverseClientSecret;

    // Snowflake-specific parameters
    private String snowflakeAccountUrl;
    private String snowflakeAuthType;
    private String snowflakeUsername;
    private String snowflakePassword;
    private String snowflakeToken;
    private String snowflakePrivateKey;
    private String snowflakePrivateKeyPassphrase;
    private String snowflakeWarehouse;
    private String snowflakeDatabase;
    private String snowflakeSchema;

    // Databricks-specific parameters
    private String databricksHost;
    private String databricksClientId;
    private String databricksClientSecret;
    private String databricksCatalog;
    private String databricksSchema;
    private String databricksPrefix;

    // Vertex AI Custom Deployed Model-specific parameters
    private String vertexAIProjectId;
    private String vertexAIBigQueryDataset;
    private String vertexAIBigQueryTable;

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

            // Determine the appropriate job type based on connector
            // Vertex AI Custom Deployed Model uses BigQuery to fetch prediction logs
            String jobType = CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL.equals(connectorType)
                ? "VERTEX_AI_CUSTOM_DEPLOYED_MODEL_CONNECTOR"
                : "AI_AGENT_CONNECTOR";

            // Create entry in per-account jobs collection
            // Convert Map<String, String> config to Map<String, Object> for generic storage
            Map<String, Object> jobConfig = new HashMap<>(config);

            int now = Context.now();
            AccountJob accountJob = new AccountJob(
                Context.accountId.get(),        // accountId
                jobType,                        // jobType
                connectorType,                  // subType (N8N, LANGCHAIN, COPILOT_STUDIO, VERTEX_AI_CUSTOM_DEPLOYED_MODEL, etc.)
                jobConfig,                      // flexible config map
                interval,                       // recurringIntervalSeconds
                now,                            // createdAt
                now                             // lastUpdatedAt
            );

            // Set execution tracking fields for job scheduler
            accountJob.setJobStatus(JobStatus.SCHEDULED);
            accountJob.setScheduleType(ScheduleType.RECURRING);
            accountJob.setScheduledAt(now);  // Schedule immediately
            accountJob.setHeartbeatAt(0);
            accountJob.setStartedAt(0);
            accountJob.setFinishedAt(0);

            AccountJobDao.instance.insertOne(accountJob);
            this.jobId = accountJob.getId().toHexString();
            loggerMaker.info("Successfully created account-level job for " + connectorType + " connector with job ID: " + this.jobId + ", interval: " + interval + "s, status: SCHEDULED", LogDb.DASHBOARD);

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error creating account-level job for " + connectorType + " connector: " + e.getMessage(), LogDb.DASHBOARD);
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
                if (dataverseEnvironmentUrl == null || dataverseEnvironmentUrl.isEmpty() ||
                    dataverseTenantId == null || dataverseTenantId.isEmpty() ||
                    dataverseClientId == null || dataverseClientId.isEmpty() ||
                    dataverseClientSecret == null || dataverseClientSecret.isEmpty()) {
                    loggerMaker.error("Missing required Copilot Studio Dataverse configuration", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_DATAVERSE_ENVIRONMENT_URL, dataverseEnvironmentUrl);
                config.put(CONFIG_DATAVERSE_TENANT_ID, dataverseTenantId);
                config.put(CONFIG_DATAVERSE_CLIENT_ID, dataverseClientId);
                config.put(CONFIG_DATAVERSE_CLIENT_SECRET, dataverseClientSecret);
                break;

            case CONNECTOR_TYPE_SNOWFLAKE:
                if (snowflakeAccountUrl == null || snowflakeAccountUrl.isEmpty()) {
                    loggerMaker.error("Missing required Snowflake account URL", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_SNOWFLAKE_ACCOUNT_URL, snowflakeAccountUrl);

                // Determine auth type (default to PASSWORD for backward compatibility)
                String authType = (snowflakeAuthType != null && !snowflakeAuthType.isEmpty())
                    ? snowflakeAuthType
                    : SNOWFLAKE_AUTH_TYPE_PASSWORD;
                config.put(CONFIG_SNOWFLAKE_AUTH_TYPE, authType);

                // Validate and add auth-specific fields
                if (SNOWFLAKE_AUTH_TYPE_PASSWORD.equals(authType)) {
                    if (snowflakeUsername == null || snowflakeUsername.isEmpty() ||
                        snowflakePassword == null || snowflakePassword.isEmpty()) {
                        loggerMaker.error("Missing required Snowflake username/password for password authentication", LogDb.DASHBOARD);
                        return null;
                    }
                    config.put(CONFIG_SNOWFLAKE_USERNAME, snowflakeUsername);
                    config.put(CONFIG_SNOWFLAKE_PASSWORD, snowflakePassword);
                } else if (SNOWFLAKE_AUTH_TYPE_TOKEN.equals(authType)) {
                    if (snowflakeToken == null || snowflakeToken.isEmpty()) {
                        loggerMaker.error("Missing required Snowflake OAuth token", LogDb.DASHBOARD);
                        return null;
                    }
                    config.put(CONFIG_SNOWFLAKE_TOKEN, snowflakeToken);
                } else if (SNOWFLAKE_AUTH_TYPE_KEY_PAIR.equals(authType)) {
                    if (snowflakeUsername == null || snowflakeUsername.isEmpty() ||
                        snowflakePrivateKey == null || snowflakePrivateKey.isEmpty()) {
                        loggerMaker.error("Missing required Snowflake username/private key for key pair authentication", LogDb.DASHBOARD);
                        return null;
                    }
                    config.put(CONFIG_SNOWFLAKE_USERNAME, snowflakeUsername);
                    config.put(CONFIG_SNOWFLAKE_PRIVATE_KEY, snowflakePrivateKey);
                    if (snowflakePrivateKeyPassphrase != null && !snowflakePrivateKeyPassphrase.isEmpty()) {
                        config.put(CONFIG_SNOWFLAKE_PRIVATE_KEY_PASSPHRASE, snowflakePrivateKeyPassphrase);
                    }
                } else {
                    loggerMaker.error("Unsupported Snowflake authentication type: " + authType, LogDb.DASHBOARD);
                    return null;
                }

                // Optional fields
                if (snowflakeWarehouse != null && !snowflakeWarehouse.isEmpty()) {
                    config.put(CONFIG_SNOWFLAKE_WAREHOUSE, snowflakeWarehouse);
                }
                if (snowflakeDatabase != null && !snowflakeDatabase.isEmpty()) {
                    config.put(CONFIG_SNOWFLAKE_DATABASE, snowflakeDatabase);
                }
                if (snowflakeSchema != null && !snowflakeSchema.isEmpty()) {
                    config.put(CONFIG_SNOWFLAKE_SCHEMA, snowflakeSchema);
                }
                break;

            case CONNECTOR_TYPE_DATABRICKS:
                if (databricksHost == null || databricksHost.isEmpty() ||
                    databricksClientId == null || databricksClientId.isEmpty() ||
                    databricksClientSecret == null || databricksClientSecret.isEmpty()) {
                    loggerMaker.error("Missing required Databricks configuration", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_DATABRICKS_HOST, databricksHost);
                config.put(CONFIG_DATABRICKS_CLIENT_ID, databricksClientId);
                config.put(CONFIG_DATABRICKS_CLIENT_SECRET, databricksClientSecret);
                config.put(CONFIG_DATABRICKS_CATALOG, databricksCatalog != null ? databricksCatalog : "main");
                config.put(CONFIG_DATABRICKS_SCHEMA, databricksSchema != null ? databricksSchema : "default");
                config.put(CONFIG_DATABRICKS_PREFIX, databricksPrefix != null ? databricksPrefix : "");
                break;

            case CONNECTOR_TYPE_VERTEX_AI_CUSTOM_DEPLOYED_MODEL:
                if (vertexAIProjectId == null || vertexAIProjectId.isEmpty() ||
                    vertexAIBigQueryDataset == null || vertexAIBigQueryDataset.isEmpty() ||
                    vertexAIBigQueryTable == null || vertexAIBigQueryTable.isEmpty()) {
                    loggerMaker.error("Missing required Vertex AI Custom Deployed Model configuration", LogDb.DASHBOARD);
                    return null;
                }
                config.put(CONFIG_VERTEX_AI_PROJECT_ID, vertexAIProjectId);
                config.put(CONFIG_VERTEX_AI_BIGQUERY_DATASET, vertexAIBigQueryDataset);
                config.put(CONFIG_VERTEX_AI_BIGQUERY_TABLE, vertexAIBigQueryTable);
                break;

            default:
                loggerMaker.error("Unsupported connector type: " + connectorType, LogDb.DASHBOARD);
                return null;
        }

        return config;
    }
}
