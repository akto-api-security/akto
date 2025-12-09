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

public class AIAgentConnectorSyncJobExecutor extends JobExecutor<AIAgentConnectorSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(AIAgentConnectorSyncJobExecutor.class);
    public static final AIAgentConnectorSyncJobExecutor INSTANCE = new AIAgentConnectorSyncJobExecutor();

    // Base path for Go binaries (relative to the project root)
    private static final String BINARY_BASE_PATH = "apps/dashboard/src/main/java/com/akto/action/";
    private static final int BINARY_TIMEOUT_SECONDS = 300; // 5 minutes timeout

    // Connector type constants
    private static final String CONNECTOR_TYPE_N8N = "N8N";
    private static final String CONNECTOR_TYPE_LANGCHAIN = "LANGCHAIN";
    private static final String CONNECTOR_TYPE_COPILOT_STUDIO = "COPILOT_STUDIO";

    // Binary names for each connector
    private static final String BINARY_NAME_N8N = "n8n-shield";
    private static final String BINARY_NAME_LANGCHAIN = "langchain-shield";
    private static final String BINARY_NAME_COPILOT_STUDIO = "copilot-shield";

    // Azure Blob Storage configuration
    // Set via environment variable: AZURE_BINARY_STORAGE_CONNECTION_STRING or AZURE_BINARY_BLOB_URL
    private static final String AZURE_CONNECTION_STRING_ENV = "AZURE_BINARY_STORAGE_CONNECTION_STRING";
    private static final String AZURE_BLOB_URL_ENV = "AZURE_BINARY_BLOB_URL";
    private static final String AZURE_CONTAINER_NAME = "binaries";

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
        if (!CONNECTOR_TYPE_N8N.equals(connectorType) &&
            !CONNECTOR_TYPE_LANGCHAIN.equals(connectorType) &&
            !CONNECTOR_TYPE_COPILOT_STUDIO.equals(connectorType)) {
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
        // Determine binary name and path based on connector type
        String binaryName = getBinaryName(connectorType);
        String binaryPath = BINARY_BASE_PATH + binaryName;
        File binaryFile = new File(binaryPath);

        // Ensure binary exists (download from Azure if needed)
        ensureBinaryExists(binaryFile, binaryName);

        if (!binaryFile.canExecute()) {
            throw new Exception("Go binary is not executable at path: " + binaryFile.getAbsolutePath());
        }

        logger.info("Executing Go binary at: {} for connector type: {}", binaryFile.getAbsolutePath(), connectorType);

        // Resolve real (no-follow-symlink) path to prevent symlink-based attacks
        String execPath;
        try {
            execPath = binaryFile.toPath().toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS).toString();
        } catch (java.nio.file.NoSuchFileException e) {
            throw new Exception("Binary file does not exist: " + binaryFile.getAbsolutePath(), e);
        } catch (Exception e) {
            throw new Exception("Failed to resolve real path for binary (possible symlink): " + binaryFile.getAbsolutePath(), e);
        }

        // Enforce exact expected binary path to avoid any injection vector from manipulated paths
        String expectedBinaryCanonical = new File(BINARY_BASE_PATH, getBinaryName(connectorType)).getCanonicalPath();
        if (!new File(execPath).getCanonicalPath().equals(expectedBinaryCanonical)) {
            throw new Exception("Binary real path mismatch. Expected: " + expectedBinaryCanonical + ", Actual: " + execPath);
        }

        // Ensure canonical path is absolute (defense-in-depth)
        if (!new File(execPath).isAbsolute()) {
            throw new Exception("Binary path is not absolute: " + execPath);
        }

        // Check for shell meta-characters (defense-in-depth, though ProcessBuilder doesn't use shell)
        // Note: Allow backslashes for Windows paths, but block other shell meta-characters
        if (execPath.matches(".*[;&|<>`$].*")) {
            throw new Exception("Binary path contains illegal shell meta-characters: " + execPath);
        }

        // Final validation: Ensure expectedBinaryCanonical is inside trusted base directory and is executable
        File binFile = new File(expectedBinaryCanonical);
        String baseCanonical = new File(BINARY_BASE_PATH).getCanonicalPath();
        String binCanonical = binFile.getCanonicalPath();
        if (!binCanonical.startsWith(baseCanonical + File.separator)) {
            throw new Exception("Binary path is outside allowed base path: " + expectedBinaryCanonical);
        }
        if (!binFile.exists() || !binFile.canExecute()) {
            throw new Exception("Binary does not exist or is not executable: " + expectedBinaryCanonical);
        }

        // Create ProcessBuilder with explicit List using the validated whitelisted path
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(java.util.Arrays.asList(expectedBinaryCanonical, "-once")); // Execute the final validated canonical path
        processBuilder.environment().clear(); // Clear inherited environment to avoid using untrusted env vars
        processBuilder.directory(new File(BINARY_BASE_PATH)); // Restrict working directory to known safe directory
        processBuilder.redirectErrorStream(true); // Merge stdout and stderr

        // Set only the required environment variables for the connector
        Map<String, String> env = processBuilder.environment();
        setConnectorEnvironmentVariables(env, connectorType, config, dataIngestionUrl);

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
     * Gets the binary name for the given connector type.
     *
     * @param connectorType The connector type (N8N, LANGCHAIN, COPILOT_STUDIO)
     * @return The binary name
     */
    private String getBinaryName(String connectorType) {
        switch (connectorType) {
            case CONNECTOR_TYPE_N8N:
                return BINARY_NAME_N8N;
            case CONNECTOR_TYPE_LANGCHAIN:
                return BINARY_NAME_LANGCHAIN;
            case CONNECTOR_TYPE_COPILOT_STUDIO:
                return BINARY_NAME_COPILOT_STUDIO;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType);
        }
    }

    /**
     * Sets environment variables for the binary based on connector type.
     *
     * @param env The environment map to populate
     * @param connectorType The connector type
     * @param config The configuration map
     * @param dataIngestionUrl The data ingestion service URL
     */
    private void setConnectorEnvironmentVariables(Map<String, String> env, String connectorType,
                                                  Map<String, String> config, String dataIngestionUrl) throws Exception {
        // Common environment variables
        env.put("DATA_INGESTION_SERVICE_URL", dataIngestionUrl);
        env.put("ACCOUNT_ID", String.valueOf(Context.accountId.get()));

        // Connector-specific environment variables
        switch (connectorType) {
            case CONNECTOR_TYPE_N8N:
                String n8nUrl = config.get("N8N_BASE_URL");
                String n8nApiKey = config.get("N8N_API_KEY");
                if (n8nUrl == null || n8nApiKey == null) {
                    throw new Exception("Missing required N8N configuration: N8N_BASE_URL or N8N_API_KEY");
                }
                env.put("N8N_BASE_URL", n8nUrl);
                env.put("N8N_API_KEY", n8nApiKey);
                break;

            case CONNECTOR_TYPE_LANGCHAIN:
                String langsmithUrl = config.get("LANGSMITH_BASE_URL");
                String langsmithApiKey = config.get("LANGSMITH_API_KEY");
                if (langsmithUrl == null || langsmithApiKey == null) {
                    throw new Exception("Missing required Langchain configuration: LANGSMITH_BASE_URL or LANGSMITH_API_KEY");
                }
                env.put("LANGSMITH_BASE_URL", langsmithUrl);
                env.put("LANGSMITH_API_KEY", langsmithApiKey);
                break;

            case CONNECTOR_TYPE_COPILOT_STUDIO:
                String appInsightsAppId = config.get("APPINSIGHTS_APP_ID");
                String appInsightsApiKey = config.get("APPINSIGHTS_API_KEY");
                if (appInsightsAppId == null || appInsightsApiKey == null) {
                    throw new Exception("Missing required Copilot Studio configuration: APPINSIGHTS_APP_ID or APPINSIGHTS_API_KEY");
                }
                env.put("APPINSIGHTS_APP_ID", appInsightsAppId);
                env.put("APPINSIGHTS_API_KEY", appInsightsApiKey);
                break;

            default:
                throw new IllegalArgumentException("Unsupported connector type: " + connectorType);
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
