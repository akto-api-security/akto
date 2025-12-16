package com.akto.jobs.executors;

import com.akto.log.LoggerMaker;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Utility class for downloading binaries from Azure Blob Storage.
 * Handles binary download, caching, and validation.
 */
public final class BinaryDownloader {

    private static final LoggerMaker logger = new LoggerMaker(BinaryDownloader.class);

    private BinaryDownloader() {
        // Prevent instantiation
    }

    /**
     * Downloads a binary from Azure Blob Storage.
     * The binary is downloaded to a temporary directory and made executable.
     *
     * @param binaryName Name of the binary to download (e.g., "n8n-shield")
     * @param connectionString Azure Storage connection string
     * @param containerName Azure container name (e.g., "binaries")
     * @return File object pointing to the downloaded binary
     * @throws Exception if download fails
     */
    public static File downloadBinary(String binaryName, String connectionString, String containerName) throws Exception {
        logger.info("Downloading binary: {}", binaryName);

        try {
            // Create blob service client
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

            // Get container client
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

            // Get blob client for the binary
            BlobClient blobClient = containerClient.getBlobClient(binaryName);

            // Check if blob exists
            if (!blobClient.exists()) {
                throw new Exception("Binary not found in Azure Storage: " + binaryName);
            }

            // Create directory for binaries in user home
            String homeDir = System.getProperty("user.home");
            File binariesDir = new File(homeDir, ".akto/binaries");
            if (!binariesDir.exists() && !binariesDir.mkdirs()) {
                throw new Exception("Failed to create binaries directory: " + binariesDir.getAbsolutePath());
            }

            // Download binary
            File binaryFile = new File(binariesDir, binaryName);

            // Download if not already cached or if remote version is newer
            if (!binaryFile.exists() || shouldRedownload(binaryFile, blobClient)) {
                logger.info("Downloading binary from Azure Storage to: {}", binaryFile.getAbsolutePath());

                try (InputStream inputStream = blobClient.openInputStream();
                     FileOutputStream outputStream = new FileOutputStream(binaryFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                // Make binary executable
                if (!binaryFile.setExecutable(true, false)) {
                    logger.warn("Failed to set executable permission on binary: {}", binaryFile.getAbsolutePath());
                }

                logger.info("Binary downloaded successfully: {}", binaryFile.getAbsolutePath());
            } else {
                logger.info("Using cached binary: {}", binaryFile.getAbsolutePath());
            }

            // Validate binary file
            validateBinary(binaryFile);

            return binaryFile;

        } catch (Exception e) {
            logger.error("Failed to download binary: {}", binaryName, e);
            throw new Exception("Failed to download binary from Azure Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads binary using direct URL instead of connection string.
     *
     * @param binaryName Name of the binary
     * @param blobUrl Direct URL to the blob (including SAS token if needed)
     * @return File object pointing to the downloaded binary
     * @throws Exception if download fails
     */
    public static File downloadBinaryFromUrl(String binaryName, String blobUrl) throws Exception {
        logger.info("Downloading binary from URL: {}", binaryName);

        try {
            // Create blob client from URL
            BlobClient blobClient = new BlobServiceClientBuilder()
                .endpoint(blobUrl)
                .buildClient()
                .getBlobContainerClient("")
                .getBlobClient(binaryName);

            // Create directory for binaries in user home
            String homeDir = System.getProperty("user.home");
            File binariesDir = new File(homeDir, ".akto/binaries");
            if (!binariesDir.exists() && !binariesDir.mkdirs()) {
                throw new Exception("Failed to create binaries directory: " + binariesDir.getAbsolutePath());
            }

            // Download binary
            File binaryFile = new File(binariesDir, binaryName);

            logger.info("Downloading binary from URL to: {}", binaryFile.getAbsolutePath());

            try (InputStream inputStream = blobClient.openInputStream();
                 FileOutputStream outputStream = new FileOutputStream(binaryFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            // Make binary executable
            if (!binaryFile.setExecutable(true, false)) {
                logger.warn("Failed to set executable permission on binary: {}", binaryFile.getAbsolutePath());
            }

            logger.info("Binary downloaded successfully from URL: {}", binaryFile.getAbsolutePath());

            // Validate binary file
            validateBinary(binaryFile);

            return binaryFile;

        } catch (Exception e) {
            logger.error("Failed to download binary from URL: {}", binaryName, e);
            throw new Exception("Failed to download binary from URL: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if binary should be re-downloaded.
     * Currently checks if local file is older than 24 hours.
     */
    private static boolean shouldRedownload(File localFile, BlobClient blobClient) {
        // Re-download if file is older than 24 hours
        long ageMillis = System.currentTimeMillis() - localFile.lastModified();
        long twentyFourHoursMillis = 24 * 60 * 60 * 1000L;

        if (ageMillis > twentyFourHoursMillis) {
            logger.info("Local binary is older than 24 hours, will re-download");
            return true;
        }

        return false;
    }

    /**
     * Validates that the binary file is valid and executable.
     */
    private static void validateBinary(File binaryFile) throws Exception {
        if (!binaryFile.exists()) {
            throw new Exception("Binary file does not exist: " + binaryFile.getAbsolutePath());
        }

        if (!binaryFile.isFile()) {
            throw new Exception("Binary path is not a file: " + binaryFile.getAbsolutePath());
        }

        if (!binaryFile.canExecute()) {
            throw new Exception("Binary file is not executable: " + binaryFile.getAbsolutePath());
        }

        if (binaryFile.length() == 0) {
            throw new Exception("Binary file is empty: " + binaryFile.getAbsolutePath());
        }

        logger.debug("Binary validation successful: {}", binaryFile.getAbsolutePath());
    }
}
