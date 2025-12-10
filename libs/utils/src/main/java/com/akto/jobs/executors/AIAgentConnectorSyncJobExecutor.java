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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.akto.jobs.executors.AIAgentConnectorConstants.BINARY_BASE_PATH;
import static com.akto.jobs.executors.AIAgentConnectorConstants.BINARY_TIMEOUT_SECONDS;
import static com.akto.jobs.executors.AIAgentConnectorConstants.AZURE_CONNECTION_STRING_ENV;
import static com.akto.jobs.executors.AIAgentConnectorConstants.AZURE_BLOB_URL_ENV;
import static com.akto.jobs.executors.AIAgentConnectorConstants.AZURE_CONTAINER_NAME;
import static com.akto.jobs.executors.AIAgentConnectorUtils.isValidConnectorType;
import static com.akto.jobs.executors.BinarySecurityValidator.validateBinaryPath;
import com.akto.jobs.executors.strategy.AIAgentConnectorStrategy;
import com.akto.jobs.executors.strategy.AIAgentConnectorStrategyFactory;

public class AIAgentConnectorSyncJobExecutor extends JobExecutor<AIAgentConnectorSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(AIAgentConnectorSyncJobExecutor.class);
    public static final AIAgentConnectorSyncJobExecutor INSTANCE = new AIAgentConnectorSyncJobExecutor();

    public AIAgentConnectorSyncJobExecutor() {
        super(AIAgentConnectorSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        AIAgentConnectorSyncJobParams params = paramClass.cast(job.getJobParams());

        String connectorType = params.getConnectorType();
        Map<String, String> config = params.getConfig();

        logger.info("Running AI Agent Connector Sync Job for connector type: {}", connectorType);

        // Validate connector type
        if (!isValidConnectorType(connectorType)) {
            throw new Exception("Unsupported connector type: " + connectorType);
        }

        // Get data ingestion URL (common for all connectors)
        String dataIngestionUrl = config.get("DATA_INGESTION_SERVICE_URL");
        if (dataIngestionUrl == null) {
            throw new Exception("Missing required configuration: DATA_INGESTION_SERVICE_URL");
        }

        // Update heartbeat before running the binary
        updateJobHeartbeat(job);

        // Execute the Go binary based on connector type
        executeGoBinary(connectorType, config, dataIngestionUrl, job);

        // Update last synced timestamp
        params.setLastSyncedAt(Context.now());
        updateJobParams(job, params);

        logger.info("Successfully completed AI Agent Connector Sync Job for connector type: {}", connectorType);
    }

    private void executeGoBinary(String connectorType, Map<String, String> config, String dataIngestionUrl, Job job) throws Exception {
        // Get strategy for this connector type using Factory pattern
        AIAgentConnectorStrategy strategy = AIAgentConnectorStrategyFactory.getStrategy(connectorType);

        // Determine binary name and path using strategy
        String binaryName = strategy.getBinaryName();
        String binaryPath = BINARY_BASE_PATH + binaryName;
        File binaryFile = new File(binaryPath);

        // Ensure binary exists (download from Azure if needed)
        ensureBinaryExists(binaryFile, binaryName);

        if (!binaryFile.canExecute()) {
            throw new Exception("Go binary is not executable at path: " + binaryFile.getAbsolutePath());
        }

        logger.info("Executing Go binary at: {} for connector type: {}", binaryFile.getAbsolutePath(), connectorType);

        // Validate binary path with comprehensive security checks
        String execCanonical = validateBinaryPath(binaryFile, binaryName, BINARY_BASE_PATH);
        String baseCanonical = new File(BINARY_BASE_PATH).getCanonicalPath();

        // Create ProcessBuilder with unmodifiable explicit command list (discrete tokens, no shell)
        // Uses: (1) execCanonical - fully validated absolute real path (no symlinks) with no user influence
        //       (2) "-once" - hardcoded argument (no user input)
        // Unmodifiable list ensures tokens cannot be altered and prevents shell interpretation
        java.util.List<String> command = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(execCanonical, "-once")
        );
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().clear(); // Clear inherited environment to avoid using untrusted env vars
        processBuilder.directory(new File(baseCanonical)); // Use the resolved canonical base directory to avoid symlink/relative path bypass
        processBuilder.redirectErrorStream(true); // Merge stdout and stderr

        // Set only the required environment variables for the connector
        Map<String, String> env = processBuilder.environment();

        // Set common environment variables
        env.put("DATA_INGESTION_SERVICE_URL", dataIngestionUrl);
        env.put("ACCOUNT_ID", String.valueOf(Context.accountId.get()));

        // Set connector-specific environment variables using strategy
        strategy.setEnvironmentVariables(env, config);

        Process process = null;
        StringBuilder output = new StringBuilder();

        try {
            // Start the process
            process = processBuilder.start();
            logger.info("Started Go binary process for connector type: {}", connectorType);

            // Read output in a separate thread
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                // Only log important messages (errors, warnings, and summary)
                if (line.contains("ERROR") || line.contains("WARN") ||
                    line.contains("completed") || line.contains("Results:") ||
                    line.contains("Starting") || line.contains("stopped")) {
                    logger.info("[Go Binary - {}] {}", connectorType, line);
                }
            }

            // Wait for the process to complete (with timeout)
            boolean finished = process.waitFor(BINARY_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new Exception("Go binary execution timed out after " + BINARY_TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();
            logger.info("Go binary completed with exit code: {} for connector type: {}", exitCode, connectorType);

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
     * @param binaryName The name of the binary (used for Azure blob name)
     * @throws Exception if binary cannot be found or downloaded
     */
    private void ensureBinaryExists(File binaryFile, String binaryName) throws Exception {
        if (binaryFile.exists()) {
            logger.info("Go binary already exists at: {}", binaryFile.getAbsolutePath());
            return;
        }

        logger.info("Go binary '{}' not found locally. Attempting to download from Azure Blob Storage...", binaryName);

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
        downloadBinaryFromAzure(binaryFile, binaryName, connectionString, blobUrl);

        // Set executable permissions
        makeExecutable(binaryFile);

        logger.info("Go binary '{}' downloaded and configured successfully", binaryName);
    }

    /**
     * Downloads the Go binary from Azure Blob Storage.
     *
     * @param targetFile The target file location
     * @param binaryName The name of the binary blob in Azure
     * @param connectionString Azure storage connection string (can be null if using blobUrl)
     * @param blobUrl Direct blob URL with SAS token (can be null if using connectionString)
     * @throws Exception if download fails
     */
    private void downloadBinaryFromAzure(File targetFile, String binaryName, String connectionString, String blobUrl) throws Exception {
        try {
            BlobClient blobClient;

            if (connectionString != null && !connectionString.isEmpty()) {
                logger.info("Using Azure connection string to download binary '{}'", binaryName);
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

                BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(AZURE_CONTAINER_NAME);
                blobClient = containerClient.getBlobClient(binaryName);
            } else {
                logger.info("Using Azure blob URL to download binary '{}'", binaryName);
                // When using SAS URL, construct the full blob path
                String fullBlobUrl = blobUrl;
                if (!fullBlobUrl.contains(AZURE_CONTAINER_NAME)) {
                    // Append container and blob name if not in URL
                    fullBlobUrl = blobUrl.split("\\?")[0] + "/" + AZURE_CONTAINER_NAME + "/" + binaryName;
                    if (blobUrl.contains("?")) {
                        fullBlobUrl += "?" + blobUrl.split("\\?")[1];
                    }
                }
                blobClient = new BlobServiceClientBuilder()
                    .endpoint(fullBlobUrl)
                    .buildClient()
                    .getBlobContainerClient(AZURE_CONTAINER_NAME)
                    .getBlobClient(binaryName);
            }

            logger.info("Downloading binary from Azure: {}/{}", AZURE_CONTAINER_NAME, binaryName);

            // Download to target file
            blobClient.downloadToFile(targetFile.getAbsolutePath(), true);

            logger.info("Binary '{}' downloaded successfully. Size: {} bytes", binaryName, targetFile.length());

        } catch (Exception e) {
            throw new Exception("Failed to download Go binary '" + binaryName + "' from Azure: " + e.getMessage(), e);
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
