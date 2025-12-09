package com.akto.jobs.executors;

import com.akto.dao.context.Context;
import com.akto.dto.jobs.AIAgentConnectorSyncJobParams;
import com.akto.dto.jobs.Job;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AIAgentConnectorSyncJobExecutor extends JobExecutor<AIAgentConnectorSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(AIAgentConnectorSyncJobExecutor.class);
    public static final AIAgentConnectorSyncJobExecutor INSTANCE = new AIAgentConnectorSyncJobExecutor();

    // Path to the Go binary (relative to the project root)
    private static final String GO_BINARY_PATH = "apps/dashboard/src/main/java/com/akto/action/n8n-shield";
    private static final int BINARY_TIMEOUT_SECONDS = 300; // 5 minutes timeout

    // Azure Blob Storage configuration
    // Set via environment variable: AZURE_BINARY_STORAGE_CONNECTION_STRING or AZURE_BINARY_BLOB_URL
    private static final String AZURE_CONNECTION_STRING_ENV = "AZURE_BINARY_STORAGE_CONNECTION_STRING";
    private static final String AZURE_BLOB_URL_ENV = "AZURE_BINARY_BLOB_URL";
    private static final String AZURE_CONTAINER_NAME = "binaries";
    private static final String AZURE_BLOB_NAME = "n8n-shield";

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

        // Ensure binary exists (download from Azure if needed)
        ensureBinaryExists(binaryFile);

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

    /**
     * Ensures the Go binary exists locally. If not found, attempts to download from Azure Blob Storage.
     *
     * @param binaryFile The File object pointing to the binary location
     * @throws Exception if binary cannot be found or downloaded
     */
    private void ensureBinaryExists(File binaryFile) throws Exception {
        if (binaryFile.exists()) {
            logger.info("Go binary already exists at: {}", binaryFile.getAbsolutePath());
            return;
        }

        logger.info("Go binary not found locally. Attempting to download from Azure Blob Storage...");

        // Check if Azure configuration is available
        String connectionString = System.getenv(AZURE_CONNECTION_STRING_ENV);
        String blobUrl = System.getenv(AZURE_BLOB_URL_ENV);

        if (connectionString == null && blobUrl == null) {
            throw new Exception(
                "Go binary not found at: " + binaryFile.getAbsolutePath() +
                " and no Azure configuration found. Please set either " +
                AZURE_CONNECTION_STRING_ENV + " or " + AZURE_BLOB_URL_ENV
            );
        }

        // Create parent directories if they don't exist
        File parentDir = binaryFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new Exception("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        // Download from Azure
        downloadBinaryFromAzure(binaryFile, connectionString, blobUrl);

        // Set executable permissions
        makeExecutable(binaryFile);

        logger.info("Go binary downloaded and configured successfully");
    }

    /**
     * Downloads the Go binary from Azure Blob Storage.
     *
     * @param targetFile The target file location
     * @param connectionString Azure storage connection string (can be null if using blobUrl)
     * @param blobUrl Direct blob URL with SAS token (can be null if using connectionString)
     * @throws Exception if download fails
     */
    private void downloadBinaryFromAzure(File targetFile, String connectionString, String blobUrl) throws Exception {
        try {
            BlobClient blobClient;

            if (connectionString != null && !connectionString.isEmpty()) {
                logger.info("Using Azure connection string to download binary");
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

                BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(AZURE_CONTAINER_NAME);
                blobClient = containerClient.getBlobClient(AZURE_BLOB_NAME);
            } else {
                logger.info("Using Azure blob URL to download binary");
                // When using SAS URL, construct the full blob path
                String fullBlobUrl = blobUrl;
                if (!fullBlobUrl.contains(AZURE_CONTAINER_NAME)) {
                    // Append container and blob name if not in URL
                    fullBlobUrl = blobUrl.split("\\?")[0] + "/" + AZURE_CONTAINER_NAME + "/" + AZURE_BLOB_NAME;
                    if (blobUrl.contains("?")) {
                        fullBlobUrl += "?" + blobUrl.split("\\?")[1];
                    }
                }
                blobClient = new BlobServiceClientBuilder()
                    .endpoint(fullBlobUrl)
                    .buildClient()
                    .getBlobContainerClient(AZURE_CONTAINER_NAME)
                    .getBlobClient(AZURE_BLOB_NAME);
            }

            logger.info("Downloading binary from Azure: {}/{}", AZURE_CONTAINER_NAME, AZURE_BLOB_NAME);

            // Download to target file
            blobClient.downloadToFile(targetFile.getAbsolutePath(), true);

            logger.info("Binary downloaded successfully. Size: {} bytes", targetFile.length());

        } catch (Exception e) {
            throw new Exception("Failed to download Go binary from Azure: " + e.getMessage(), e);
        }
    }

    /**
     * Sets executable permissions on the binary file.
     *
     * @param binaryFile The binary file to make executable
     * @throws Exception if permissions cannot be set
     */
    private void makeExecutable(File binaryFile) throws Exception {
        try {
            // Try POSIX permissions first (Linux/Mac)
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_READ);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);

            Files.setPosixFilePermissions(binaryFile.toPath(), perms);
            logger.info("Binary permissions set successfully (POSIX)");
        } catch (UnsupportedOperationException e) {
            // Fall back to basic setExecutable for Windows
            if (!binaryFile.setExecutable(true, false)) {
                throw new Exception("Failed to set executable permissions on binary");
            }
            logger.info("Binary permissions set successfully (setExecutable)");
        }
    }
}
