package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.executors.BinaryDownloader;
import com.akto.jobs.executors.BinaryExecutor;
import com.akto.log.LoggerMaker;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Shared binary-download-and-execute logic for AI Agent Connector "shield" binaries.
 * Used by AIAgentConnectorExecutor (one binary run per job) and CopilotStudioMultiEnvExecutor
 * (one copilot-shield run per discovered environment, within a single job execution).
 */
public final class BinaryConnectorRunner {

    private static final LoggerMaker logger = new LoggerMaker(BinaryConnectorRunner.class);

    /** Cap on how much of the binary's stdout we copy into the AccountJob.error field. */
    private static final int MAX_BINARY_ERROR_OUTPUT_CHARS = 8000;

    private BinaryConnectorRunner() {
    }

    /**
     * Downloads the given binary from Azure Storage and executes it with config as environment variables.
     *
     * @param job The AccountJob being executed (used for heartbeat updates and job metadata env vars)
     * @param config Job configuration containing connector-specific settings
     * @param binaryName Name of the binary to download and execute
     * @throws Exception if download or execution fails
     */
    public static void run(AccountJob job, Map<String, Object> config, String binaryName) throws Exception {
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
        CyborgApiClient.updateJobHeartbeat(job.getId());

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
        CyborgApiClient.updateJobHeartbeat(job.getId());

        // Check execution result
        if (!result.isSuccess()) {
            String outputTail = tailOfOutput(result.getStdout(), MAX_BINARY_ERROR_OUTPUT_CHARS);
            String errorMsg = "Binary execution failed with exit code " + result.getExitCode() +
                ". Output: " + outputTail;
            logger.error("Binary execution failed: {}", errorMsg);
            throw new Exception(errorMsg);
        }

        logger.info("Binary execution successful: binaryName={}, exitCode={}, outputLength={}",
            binaryName, result.getExitCode(), result.getStdout().length());
    }

    private static String tailOfOutput(String output, int maxChars) {
        if (output == null) {
            return "";
        }
        if (output.length() <= maxChars) {
            return output;
        }
        return output.substring(output.length() - maxChars);
    }
}
