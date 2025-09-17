package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.McpReconRequest;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.McpScanResult;
import com.akto.dto.McpServer;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.*;

/**
 * MCP Reconnaissance Sync Job Executor
 * Performs scheduled network scanning to discover MCP servers
 */
public class McpReconSyncJobExecutor {
    
    private static final LoggerMaker logger = new LoggerMaker(McpReconSyncJobExecutor.class, LogDb.RUNTIME);
    
    // Scanner instance
    private McpReconScanner scanner;
    
    // Singleton instance
    public static final McpReconSyncJobExecutor INSTANCE = new McpReconSyncJobExecutor();
    
    // Configuration constants
    private static final String MCP_RECON_COLLECTION_TAG = "MCP_RECON_SCAN";
    private static final int DEFAULT_SCAN_INTERVAL_MINS = 2; // Scan every 2 mins
    private static final int MAX_CONCURRENT_SCANS = 5;
    private static final int SCAN_TIMEOUT_MINUTES = 30;
    
    // Optimized executor with better thread management
    private final ExecutorService scanExecutor;
    
    // Enhanced cache with size limits and expiration
    private final Map<String, ScanCacheEntry> scanCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_EXPIRY_MS = 3600000; // 1 hour
    
    public McpReconSyncJobExecutor() {
        // Optimize scanner with better settings
        this.scanner = new McpReconScanner(200, 1500, 1000); // Increased concurrency and batch size
        
        // Use optimized thread pool with bounded queue
        this.scanExecutor = new ThreadPoolExecutor(
            2,                      // Core pool size
            MAX_CONCURRENT_SCANS,   // Maximum pool size  
            60L, TimeUnit.SECONDS,  // Keep alive time
            new LinkedBlockingQueue<>(MAX_CONCURRENT_SCANS * 2),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * Main entry point for the job
     */
    public void runJob() {
        logger.info("Starting MCP Recon Sync Job");
        
        try {
            // Fetch configured scan targets from database
            List<McpReconConfig> scanConfigs = fetchMcpReconIPRanges();
            
            if (scanConfigs.isEmpty()) {
                logger.info("No MCP recon configurations found. Skipping scan.");
                return;
            }
            
            logger.info(String.format("Found %d scan configurations", scanConfigs.size()));
            
            // Process scan configurations in parallel
            List<Future<ScanTaskResult>> futures = new ArrayList<>();
            
            for (McpReconConfig config : scanConfigs) {
                // Check cache to avoid redundant scans
                if (shouldSkipScan(config)) {
                    logger.debug(String.format("Skipping cached scan for %s", config.getIpRange()));
                    continue;
                }
                
                // Submit scan task
                Future<ScanTaskResult> future = scanExecutor.submit(() -> executeScan(config));
                futures.add(future);
            }
            
            // Collect results with timeout
            List<ScanTaskResult> scanResults = new ArrayList<>();
            for (Future<ScanTaskResult> future : futures) {
                try {
                    ScanTaskResult result = future.get(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                    if (result != null) {
                        scanResults.add(result);
                    }
                } catch (TimeoutException e) {
                    logger.error("Scan task timed out", e);
                    future.cancel(true);
                } catch (Exception e) {
                    logger.error("Error collecting scan result", e);
                }
            }
            
            // Process and store scan results
            if (!scanResults.isEmpty()) {
                processScanResults(scanResults);
            }
            
            logger.info(String.format("MCP Recon Sync Job completed. Processed %d scans", scanResults.size()));
            
        } catch (Exception e) {
            logger.error("Error executing MCP Recon Sync Job", e);
        }
    }
    
    /**
     * Fetch MCP recon configurations from database through Cyborg
     */
    private List<McpReconConfig> fetchMcpReconIPRanges() {
        List<McpReconConfig> configs = new ArrayList<>();
        
        try {
            // Fetch pending MCP recon requests from MongoDB
            List<McpReconRequest> pendingRequests = DataActorFactory.fetchInstance().fetchPendingMcpReconRequests();
            
            logger.info(String.format("Found %d pending MCP recon requests", pendingRequests.size()));
            
            // Convert McpReconRequest DTOs to McpReconConfig
            for (McpReconRequest request : pendingRequests) {
                McpReconConfig config = new McpReconConfig();
                config.setRequestId(request.get_id());
                config.setAccountId(request.getAccountId()); 
                config.setName("MCP_Recon_" + request.get_id());
                config.setIpRange(request.getIpRange());
                config.setEnabled(true);
                
                configs.add(config);
                
                // Update status to "In Progress"
                int currentTime = Context.now();
                DataActorFactory.fetchInstance().updateMcpReconRequestStatus(
                    request.get_id(),
                    McpReconRequest.STATUS_IN_PROGRESS,
                    0,  // serversFound is 0 when starting
                    currentTime,  // startedAt
                    0  // finishedAt is 0 when starting
                );
            }
        } catch (Exception e) {
            logger.error("Error fetching MCP recon configurations", e);
        }
        return configs;
    }

    
    /**
     * Extract IP range from collection
     */
    private String extractIpRange(ApiCollection collection) {
        // Check for explicit IP range in tags
        for (CollectionTags tag : collection.getTagsList()) {
            if ("MCP_RECON_IP_RANGE".equals(tag.getKeyName())) {
                return tag.getValue();
            }
        }
        
        // Use hostname as fallback
        if (!StringUtils.isEmpty(collection.getHostName())) {
            // Check if hostname looks like an IP range
            String hostname = collection.getHostName();
            if (hostname.contains("/") || hostname.contains("-") || hostname.contains(",")) {
                return hostname;
            }
            
            // Convert single hostname to CIDR /32
            if (isIpAddress(hostname)) {
                return hostname + "/32";
            }
        }
        
        return null;
    }
    
    /**
     * Check if string is an IP address
     */
    private boolean isIpAddress(String str) {
        return str.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }
    
    /**
     * Optimized cache check with expiration and size management
     */
    private boolean shouldSkipScan(McpReconConfig config) {
        String cacheKey = config.getRequestId();
        ScanCacheEntry cached = scanCache.get(cacheKey);
        
        if (cached == null) {
            return false;
        }
        
        // Check if cache is still valid
        long cacheAge = System.currentTimeMillis() - cached.timestamp;
        
        if (cacheAge < CACHE_EXPIRY_MS) {
            return true;
        } else {
            // Remove expired entry
            scanCache.remove(cacheKey);
        }
        
        // Clean cache if it gets too large
        if (scanCache.size() > MAX_CACHE_SIZE) {
            cleanExpiredCacheEntries();
        }
        
        return false;
    }
    
    /**
     * Clean expired cache entries to prevent memory bloat
     */
    private void cleanExpiredCacheEntries() {
        long currentTime = System.currentTimeMillis();
        scanCache.entrySet().removeIf(entry -> 
            (currentTime - entry.getValue().timestamp) > CACHE_EXPIRY_MS);
        
        // If still too large, remove oldest entries
        if (scanCache.size() > MAX_CACHE_SIZE) {
            List<Map.Entry<String, ScanCacheEntry>> entries = new ArrayList<>(scanCache.entrySet());
            entries.sort((a, b) -> Long.compare(a.getValue().timestamp, b.getValue().timestamp));
            
            // Remove oldest 20% of entries
            int toRemove = scanCache.size() / 5;
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                scanCache.remove(entries.get(i).getKey());
            }
        }
    }
    
    /**
     * Execute scan for a configuration
     */
    private ScanTaskResult executeScan(McpReconConfig config) {
        logger.info(String.format("Starting scan for %s", config.getIpRange()));
        
        try {
            // Create scanner with custom parameters if specified
            McpReconScanner scannerInstance = scanner;
            
            // Execute scan
            long startTime = System.currentTimeMillis();
            McpScanResult result = scannerInstance.scanIpRange(config.getIpRange());
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info(String.format("Scan completed for %s in %d ms. Found %d servers",
                config.getIpRange(), duration, result.getServersFound()));
            
            // Update cache
            scanCache.put(config.getRequestId(), new ScanCacheEntry(result, System.currentTimeMillis()));
            
            // Return task result
            return new ScanTaskResult(config, result, duration);
            
        } catch (Exception e) {
            logger.error(String.format("Error scanning %s", config.getIpRange()), e);
            return new ScanTaskResult(config, null, 0);
        }
    }
    
    /**
     * Process and store scan results
     */
    private void processScanResults(List<ScanTaskResult> scanResults) {
        List<HttpResponseParams> allResponseParams = new ArrayList<>();
        
        // Batch size for database insertions
        final int BATCH_SIZE = 500;
        List<BasicDBObject> serverBatch = new ArrayList<>();
        
        for (ScanTaskResult taskResult : scanResults) {
            if (taskResult.result == null || taskResult.result.getServers() == null) {
                continue;
            }

            // Store scan metadata
            storeScanMetadata(taskResult);

            // Prepare batch data for scan results
            int discoveryTimestamp = Context.now();
            for (McpServer server : taskResult.result.getServers()) {
                BasicDBObject serverData = new BasicDBObject();

                // Add MCP recon request ID
                serverData.put("mcp_recon_request_id", taskResult.config.getRequestId());

                // Add all server parameters
                serverData.put("ip", server.getIp());
                serverData.put("port", server.getPort());
                serverData.put("url", server.getUrl());
                serverData.put("verified", server.isVerified());
                serverData.put("detection_method", server.getDetectionMethod());
                serverData.put("timestamp", server.getTimestamp());
                serverData.put("type", server.getType());
                serverData.put("endpoint", server.getEndpoint());
                serverData.put("protocol_version", server.getProtocolVersion());
                serverData.put("server_info", server.getServerInfo());
                serverData.put("capabilities", server.getCapabilities());
                serverData.put("tools", server.getTools());
                serverData.put("resources", server.getResources());
                serverData.put("prompts", server.getPrompts());

                // Add discovery timestamp
                serverData.put("discovered_at", discoveryTimestamp);

                // Add to batch
                serverBatch.add(serverData);
                
                // Insert when batch is full
                if (serverBatch.size() >= BATCH_SIZE) {
                    DataActorFactory.fetchInstance().storeMcpReconResultsBatch(serverBatch);
                    serverBatch.clear();
                }
            }
        }
        
        // Insert remaining servers in batch
        if (!serverBatch.isEmpty()) {
            DataActorFactory.fetchInstance().storeMcpReconResultsBatch(serverBatch);
        }
    }

    /**
     * Store scan metadata for tracking
     */

    //write logic to update mcp_recon_requests collection with status completed/failed and finished_at timestamp
    private void storeScanMetadata(ScanTaskResult taskResult) {
        try {
            // Determine status and servers found
            String status;
            int serversFound = 0;
            
            if (taskResult.result != null) {
                status = McpReconRequest.STATUS_COMPLETED;
                serversFound = taskResult.result.getServersFound();
            } else {
                status = McpReconRequest.STATUS_FAILED;
            }
            
            // Update MCP recon request status with results completed or failed
            int finishedAt = Context.now();
            DataActorFactory.fetchInstance().updateMcpReconRequestStatus(
                taskResult.config.getRequestId(),
                status,
                serversFound,
                0,  // startedAt (not updating when finishing)
                finishedAt
            );
            
        } catch (Exception e) {
            logger.error("Error storing scan metadata", e);
        }
    }
    
    /**
     * Shutdown executor service
     */
    public void shutdown() {
        try {
            scanExecutor.shutdown();
            if (!scanExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scanExecutor.shutdownNow();
            }
            if (scanner != null) {
                scanner.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error shutting down MCP Recon executor", e);
        }
    }
    
    /**
     * Configuration class for MCP recon scans
     */
    private static class McpReconConfig {
        private String requestId;  // MongoDB request ID
        private int accountId;
        private String name;
        private String ipRange;
        private int batchSize = 500;
        private int timeout = 2000;
        private int maxConcurrent = 100;
        private boolean enabled = true;
        
        public static McpReconConfig fromMap(Map<String, Object> map) {
            McpReconConfig config = new McpReconConfig();
            config.setAccountId((Integer) map.getOrDefault("account_id", 0));
            config.setName((String) map.get("name"));
            config.setIpRange((String) map.get("ip_range"));
            config.setEnabled((Boolean) map.getOrDefault("enabled", true));
            
            // Parse custom settings
            if (map.containsKey("batch_size")) {
                config.setBatchSize((Integer) map.get("batch_size"));
            }
            if (map.containsKey("timeout")) {
                config.setTimeout((Integer) map.get("timeout"));
            }
            if (map.containsKey("max_concurrent")) {
                config.setMaxConcurrent((Integer) map.get("max_concurrent"));
            }
            
            return config;
        }
        
        // Getters and setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public int getAccountId() { return accountId; }
        public void setAccountId(int accountId) { this.accountId = accountId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getIpRange() { return ipRange; }
        public void setIpRange(String ipRange) { this.ipRange = ipRange; }
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    /**
     * Result of a scan task
     */
    private static class ScanTaskResult {
        final McpReconConfig config;
        final McpScanResult result;
        final long duration;
        
        ScanTaskResult(McpReconConfig config, McpScanResult result, long duration) {
            this.config = config;
            this.result = result;
            this.duration = duration;
        }
    }
    
    /**
     * Cache entry for scan results
     */
    private static class ScanCacheEntry {
        final McpScanResult result;
        final long timestamp;
        
        ScanCacheEntry(McpScanResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }
}