package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.executors.BinaryDownloader;
import com.akto.jobs.executors.BinaryExecutor;
import com.akto.log.LoggerMaker;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Executor for AI Agent Connector jobs.
 * Handles execution of AI Agent Connector integrations (N8N, Langchain, Copilot Studio, etc.).
 *
 * This is a singleton executor - use AIAgentConnectorExecutor.INSTANCE to access it.
 */
public class AIAgentConnectorExecutor extends AccountJobExecutor {

    public static final AIAgentConnectorExecutor INSTANCE = new AIAgentConnectorExecutor();

    private static final LoggerMaker logger = new LoggerMaker(AIAgentConnectorExecutor.class);

    /**
     * Private constructor for singleton pattern.
     */
    private AIAgentConnectorExecutor() {
    }

    /**
     * Execute AI Agent Connector job.
     * Processes connector-specific logic based on subType (N8N, LANGCHAIN, COPILOT_STUDIO).
     *
     * @param job The AccountJob to execute
     * @throws Exception If job execution fails
     */
    @Override
    protected void runJob(AccountJob job) throws Exception {
        logger.info("Executing AI Agent Connector job: jobId={}, subType={}",
            job.getId(), job.getSubType());

        // Extract job configuration
        Map<String, Object> config = job.getConfig();
        String subType = job.getSubType();

        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        if (subType == null || subType.isEmpty()) {
            throw new IllegalArgumentException("Job subType is null or empty for job: " + job.getId());
        }

        // Execute based on connector type
        switch (subType) {
            case "N8N":
                executeN8NConnector(job, config);
                break;

            case "LANGCHAIN":
            case "LANGSMITH":
                executeLangchainConnector(job, config);
                break;

            case "COPILOT_STUDIO":
                executeCopilotStudioConnector(job, config);
                break;

            case "SNOWFLAKE":
                executeSnowflakeConnector(job, config);
                break;

            default:
                logger.warn("Unknown AI Agent Connector subType: {}. Skipping job execution.", subType);
                throw new IllegalArgumentException("Unsupported AI Agent Connector subType: " + subType);
        }

        logger.info("AI Agent Connector job completed successfully: jobId={}", job.getId());
    }

    /**
     * Execute N8N connector logic.
     * Downloads the N8N shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeN8NConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing N8N connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_N8N);

        logger.info("N8N connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Langchain/Langsmith connector logic.
     * Downloads the Langchain shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeLangchainConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Langchain connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_LANGCHAIN);

        logger.info("Langchain connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Copilot Studio connector logic.
     * Downloads the Copilot shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeCopilotStudioConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Copilot Studio connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_COPILOT_STUDIO);

        logger.info("Copilot Studio connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Snowflake connector logic.
     * Downloads the Snowflake shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeSnowflakeConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Snowflake connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_SNOWFLAKE);

        logger.info("Snowflake connector execution completed: jobId={}", job.getId());
    }

    /**
     * Common method to execute any AI Agent Connector binary.
     * Downloads binary from Azure Storage and executes it with config as environment variables.
     *
     * @param job The AccountJob being executed
     * @param config Job configuration containing connector-specific settings
     * @param binaryName Name of the binary to download and execute
     * @throws Exception if download or execution fails
     */
    private void executeBinaryConnector(AccountJob job, Map<String, Object> config, String binaryName) throws Exception {
        logger.info("Executing binary connector: binaryName={}, jobId={}", binaryName, job.getId());

        // Get Azure Storage connection string from environment
        String connectionString = System.getenv(AZURE_CONNECTION_STRING_ENV);
        String blobUrl = System.getenv(AZURE_BLOB_URL_ENV);

        if (connectionString == null && blobUrl == null) {
            throw new Exception("Azure Storage credentials not configured. Set either " +
                AZURE_CONNECTION_STRING_ENV + " or " + AZURE_BLOB_URL_ENV + " environment variable");
        }

        // Download binary from Azure Storage
        File binaryFile;
        if (connectionString != null && !connectionString.isEmpty()) {
            logger.info("Downloading binary using connection string: {}", binaryName);
            binaryFile = BinaryDownloader.downloadBinary(binaryName, connectionString, AZURE_CONTAINER_NAME);
        } else {
            logger.info("Downloading binary using blob URL: {}", binaryName);
            binaryFile = BinaryDownloader.downloadBinaryFromUrl(binaryName, blobUrl);
        }

        // Update heartbeat after download (download might take time)
        updateJobHeartbeat(job);

        // Prepare environment variables for binary execution
        Map<String, String> envVars = new HashMap<>();

        // Add all config values as environment variables
        if (config != null) {
            config.forEach((key, value) -> {
                if (value != null) {
                    envVars.put(key, value.toString());
                }
            });
        }

        // Add AKTO_JWT_TOKEN from DATABASE_ABSTRACTOR_SERVICE_TOKEN (for DIS authentication)
        String aktoJwtToken = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (aktoJwtToken == null || aktoJwtToken.isEmpty()) {
            throw new Exception("DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable not set. Required for DIS authentication.");
        }
        envVars.put("AKTO_JWT_TOKEN", aktoJwtToken);

        // Add job metadata
        envVars.put("AKTO_JOB_ID", job.getId().toHexString());
        envVars.put("AKTO_ACCOUNT_ID", String.valueOf(job.getAccountId()));
        envVars.put("AKTO_JOB_TYPE", job.getJobType());
        envVars.put("AKTO_JOB_SUB_TYPE", job.getSubType());

        logger.info("Executing binary with {} environment variables", envVars.size());

        // Execute binary with -once flag and timeout
        // The -once flag tells the binary to run one iteration and exit
        String[] args = new String[] { "-once" };
        BinaryExecutor.ExecutionResult result = BinaryExecutor.executeBinary(
            binaryFile,
            args,
            envVars,
            BINARY_TIMEOUT_SECONDS
        );

        // Update heartbeat after execution
        updateJobHeartbeat(job);

        // Check execution result
        if (!result.isSuccess()) {
            String errorMsg = "Binary execution failed with exit code " + result.getExitCode() +
                ". Output: " + result.getStdout().substring(0, Math.min(500, result.getStdout().length()));
            logger.error("Binary execution failed: {}", errorMsg);
            throw new Exception(errorMsg);
        }

        logger.info("Binary execution successful: binaryName={}, exitCode={}, outputLength={}",
            binaryName, result.getExitCode(), result.getStdout().length());
    }
}
