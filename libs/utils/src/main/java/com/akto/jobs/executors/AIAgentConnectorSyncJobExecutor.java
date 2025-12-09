package com.akto.jobs.executors;

import com.akto.dao.context.Context;
import com.akto.dto.jobs.AIAgentConnectorSyncJobParams;
import com.akto.dto.jobs.Job;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AIAgentConnectorSyncJobExecutor extends JobExecutor<AIAgentConnectorSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(AIAgentConnectorSyncJobExecutor.class);
    public static final AIAgentConnectorSyncJobExecutor INSTANCE = new AIAgentConnectorSyncJobExecutor();

    // Path to the Go binary (relative to the project root)
    private static final String GO_BINARY_PATH = "apps/dashboard/src/main/java/com/akto/action/n8n-shield";
    private static final int BINARY_TIMEOUT_SECONDS = 300; // 5 minutes timeout

    public AIAgentConnectorSyncJobExecutor() {
        super(AIAgentConnectorSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        AIAgentConnectorSyncJobParams params = paramClass.cast(job.getJobParams());

        String connectorType = params.getConnectorType();
        Map<String, String> config = params.getConfig();

        logger.info("Running AI Agent Connector Sync Job for connector type: {}", connectorType);

        // Only run for N8N connector type for now
        if (!"N8N".equals(connectorType)) {
            logger.info("Skipping connector type: {}. Only N8N is supported currently.", connectorType);
            params.setLastSyncedAt(Context.now());
            updateJobParams(job, params);
            return;
        }

        // Get configuration values
        String n8nUrl = config.get("N8N_BASE_URL");
        String apiKey = config.get("N8N_API_KEY");
        String dataIngestionUrl = config.get("DATA_INGESTION_SERVICE_URL");

        if (n8nUrl == null || apiKey == null || dataIngestionUrl == null) {
            throw new Exception("Missing required configuration: N8N_BASE_URL, N8N_API_KEY, or DATA_INGESTION_SERVICE_URL");
        }

        // Update heartbeat before running the binary
        updateJobHeartbeat(job);

        // Execute the Go binary
        executeGoBinary(n8nUrl, apiKey, dataIngestionUrl, job);

        // Update last synced timestamp
        params.setLastSyncedAt(Context.now());
        updateJobParams(job, params);

        logger.info("Successfully completed AI Agent Connector Sync Job for connector type: {}", connectorType);
    }

    private void executeGoBinary(String n8nUrl, String apiKey, String dataIngestionUrl, Job job) throws Exception {
        // Get the absolute path to the Go binary
        File binaryFile = new File(GO_BINARY_PATH);

        if (!binaryFile.exists()) {
            throw new Exception("Go binary not found at path: " + binaryFile.getAbsolutePath());
        }

        if (!binaryFile.canExecute()) {
            throw new Exception("Go binary is not executable at path: " + binaryFile.getAbsolutePath());
        }

        logger.info("Executing Go binary at: {}", binaryFile.getAbsolutePath());

        // Build the command with -once flag
        List<String> command = new ArrayList<>();
        command.add(binaryFile.getAbsolutePath());
        command.add("-once");

        // Create ProcessBuilder
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // Merge stdout and stderr

        // Set environment variables
        Map<String, String> env = processBuilder.environment();
        env.put("N8N_BASE_URL", n8nUrl);
        env.put("N8N_API_KEY", apiKey);
        env.put("DATA_INGESTION_SERVICE_URL", dataIngestionUrl);
        env.put("ACCOUNT_ID", String.valueOf(Context.accountId.get()));

        Process process = null;
        StringBuilder output = new StringBuilder();

        try {
            // Start the process
            process = processBuilder.start();
            logger.info("Started Go binary process");

            // Read output in a separate thread
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                // Only log important messages (errors, warnings, and summary)
                if (line.contains("ERROR") || line.contains("WARN") ||
                    line.contains("completed") || line.contains("Results:") ||
                    line.contains("Starting") || line.contains("stopped")) {
                    logger.info("[Go Binary] {}", line);
                }
            }

            // Wait for the process to complete (with timeout)
            boolean finished = process.waitFor(BINARY_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new Exception("Go binary execution timed out after " + BINARY_TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();
            logger.info("Go binary completed with exit code: {}", exitCode);

            if (exitCode != 0) {
                throw new Exception("Go binary failed with exit code: " + exitCode + ". Output: " + output.toString());
            }

            // Update heartbeat after successful execution
            updateJobHeartbeat(job);

        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new Exception("Go binary execution was interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            throw new Exception("Error executing Go binary: " + e.getMessage() + ". Output: " + output.toString(), e);
        }
    }
}
