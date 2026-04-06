package com.akto.threat.detection.hyperscan;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for loading threat patterns from Azure Blob Storage with local fallback.
 * Periodically checks for pattern updates every 15 minutes.
 *
 * - Tries to fetch from Azure Blob Storage (with 1 retry on failure)
 * - Falls back to local file if Azure fetch fails
 * - Logs each update attempt (success/failure) for monitoring
 * - Thread-safe pattern loading and instance swapping
 */
public class PatternUpdateService {

    private static final LoggerMaker logger = new LoggerMaker(PatternUpdateService.class, LogDb.THREAT_DETECTION);
    private static final long UPDATE_INTERVAL_MINUTES = 15;
    private static final int MAX_RETRIES = 1;
    private static final long RETRY_DELAY_MS = 1000;

    private final String localPatternFilePath;
    private final String azureBlobStorageConnection;
    private final String azureBlobContainerName;
    private final String azureBlobFileName;
    private final ScheduledExecutorService scheduler;
    private long lastUpdateAttemptTime = 0;

    public PatternUpdateService(
            String localPatternFilePath,
            String azureBlobStorageConnection,
            String azureBlobContainerName,
            String azureBlobFileName) {
        this.localPatternFilePath = localPatternFilePath;
        this.azureBlobStorageConnection = azureBlobStorageConnection;
        this.azureBlobContainerName = azureBlobContainerName;
        this.azureBlobFileName = azureBlobFileName;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "PatternUpdateThread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start periodic pattern update checks (every 15 minutes).
     * Logs each attempt with timestamp and result.
     */
    public void startPeriodicUpdates() {
        logger.warnAndAddToDb("PatternUpdateService: Starting periodic updates every " + UPDATE_INTERVAL_MINUTES + " minutes");
        scheduler.scheduleAtFixedRate(
                this::checkAndUpdatePatterns,
                0,
                UPDATE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * Check if enough time has passed since last update and trigger pattern reload if needed.
     * Only one update is allowed per 15 minute window.
     */
    private synchronized void checkAndUpdatePatterns() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateAttemptTime < UPDATE_INTERVAL_MINUTES * 60 * 1000) {
            return; // Not enough time has passed, skip
        }
        lastUpdateAttemptTime = now;
        updatePatterns();
    }

    /**
     * Update patterns by fetching from Azure Blob (with retry) or falling back to local file.
     * Logs each attempt for monitoring.
     */
    private void updatePatterns() {
        long startTime = System.currentTimeMillis();
        String logPrefix = "PatternUpdate[" + java.time.Instant.now() + "]";

        List<String> patternLines = null;

        // Try Azure Blob Storage with 1 retry
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                patternLines = fetchPatternsFromAzureBlob();
                if (patternLines != null && !patternLines.isEmpty()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.warnAndAddToDb(logPrefix + ": Successfully fetched patterns from Azure Blob (" + elapsed + "ms)");
                    reloadPatternsInHyperscan(patternLines);
                    return;
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (attempt < MAX_RETRIES) {
                    logger.warnAndAddToDb(logPrefix + ": Azure fetch attempt " + (attempt + 1) + " failed, retrying... (" + elapsed + "ms) - " + e.getMessage());
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                } else {
                    logger.warnAndAddToDb(logPrefix + ": Azure fetch failed after " + (MAX_RETRIES + 1) + " attempts (" + elapsed + "ms), falling back to local file - " + e.getMessage());
                }
            }
        }

        // Fallback to local file
        try {
            patternLines = loadPatternsFromLocalFile();
            if (patternLines != null && !patternLines.isEmpty()) {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.warnAndAddToDb(logPrefix + ": Loaded patterns from local fallback file (" + elapsed + "ms)");
                reloadPatternsInHyperscan(patternLines);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.errorAndAddToDb(logPrefix + ": Failed to load patterns from local file (" + elapsed + "ms) - " + e.getMessage());
        }
    }

    /**
     * Fetch patterns from Azure Blob Storage.
     * Returns list of pattern lines or throws exception if fetch fails.
     */
    private List<String> fetchPatternsFromAzureBlob() throws Exception {
        if (azureBlobStorageConnection == null || azureBlobStorageConnection.isEmpty()) {
            throw new Exception("Azure Blob Storage connection not configured");
        }
        if (azureBlobContainerName == null || azureBlobContainerName.isEmpty()) {
            throw new Exception("Azure Blob container name not configured");
        }
        if (azureBlobFileName == null || azureBlobFileName.isEmpty()) {
            throw new Exception("Azure Blob file name not configured");
        }

        // Create blob service client
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(azureBlobStorageConnection)
                .buildClient();

        // Get container client
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(azureBlobContainerName);

        // Get blob client for the pattern file
        BlobClient blobClient = containerClient.getBlobClient(azureBlobFileName);

        // Check if blob exists
        if (!blobClient.exists()) {
            throw new Exception("Pattern file not found in Azure Storage: " + azureBlobFileName);
        }

        // Read pattern lines from blob
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(blobClient.openInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    /**
     * Load patterns from local file (classpath resource or filesystem).
     */
    private List<String> loadPatternsFromLocalFile() throws Exception {
        // First try to load from classpath
        InputStream is = PatternUpdateService.class.getClassLoader().getResourceAsStream(localPatternFilePath);
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        }

        // Fall back to filesystem
        File patternFile = new File(localPatternFilePath);
        if (!patternFile.exists()) {
            throw new Exception("Local pattern file not found in classpath or filesystem: " + localPatternFilePath);
        }
        return Files.readAllLines(Paths.get(localPatternFilePath));
    }

    /**
     * Reload patterns in HyperscanThreatMatcher by creating a new instance and swapping it.
     * This ensures old threads continue using their existing scanners while new threads use updated patterns.
     */
    private void reloadPatternsInHyperscan(List<String> patternLines) {
        try {
            HyperscanThreatMatcher.getInstance().reloadPatternsFromLines(patternLines);
            logger.infoAndAddToDb("PatternUpdateService: Successfully reloaded patterns in HyperscanThreatMatcher");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "PatternUpdateService: Failed to reload patterns in HyperscanThreatMatcher - " + e.getMessage());
        }
    }

    /**
     * Shutdown the pattern update service gracefully.
     */
    public void shutdown() {
        logger.warnAndAddToDb("PatternUpdateService: Shutting down");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warnAndAddToDb("PatternUpdateService: Force shutdown after timeout");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
