package com.akto.hybrid_runtime;

import com.akto.dao.billing.UningestedApiOverageDao;
import com.akto.dto.billing.UningesetedApiOverage;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UningestedApiTracker {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(UningestedApiTracker.class, LogDb.RUNTIME);
    
    // Local cache to avoid frequent DB checks
    private static final ConcurrentHashMap<String, Boolean> overageCache = new ConcurrentHashMap<>();
    
    // Batch operations for DB writes
    private static final ConcurrentHashMap<String, UningesetedApiOverage> pendingOverageInfo = new ConcurrentHashMap<>();
    
    // Counter for batch size
    private static final AtomicInteger batchCounter = new AtomicInteger(0);
    
    // Batch size threshold
    private static final int BATCH_SIZE = 100;
    
    // Sync interval in seconds
    private static final int SYNC_INTERVAL = 60; // 1 minute
    
    // Instance ID for tracking which runtime instance created the record
    private static final String INSTANCE_ID = System.getenv("INSTANCE_ID") != null ? 
        System.getenv("INSTANCE_ID") : "unknown";
    
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    static {
        // Start periodic sync with DB
        scheduler.scheduleAtFixedRate(UningestedApiTracker::syncWithDB, SYNC_INTERVAL, SYNC_INTERVAL, TimeUnit.SECONDS);
        
        // Shutdown hook to sync remaining data
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            syncWithDB(); // Final sync
        }));
    }
    
    /**
     * Record overage information when customer reaches usage limit
     */
    public static void recordOverage(int apiCollectionId, String urlType, String methodAndUrl) {
        
        String cacheKey = generateCacheKey(apiCollectionId, urlType, methodAndUrl);
        
        // Check if already recorded in cache
        if (overageCache.containsKey(cacheKey)) {
            return;
        }
        
        // Check if already exists in DB
        if (UningestedApiOverageDao.instance.exists(apiCollectionId, urlType, methodAndUrl)) {
            overageCache.put(cacheKey, true);
            return;
        }
        
        // Create overage info
        UningesetedApiOverage uningesetedApiOverage = new UningesetedApiOverage(
            apiCollectionId,  urlType, methodAndUrl
        );
        
        // Add to pending batch
        pendingOverageInfo.put(cacheKey, uningesetedApiOverage);
        overageCache.put(cacheKey, true);
        
        // Log the overage
        loggerMaker.infoAndAddToDb(String.format("Overage recorded -  Collection: %d, Type: %s, " +
                "MethodUrl: %s", apiCollectionId, urlType, methodAndUrl));
        
        // Check if batch is full
        if (batchCounter.incrementAndGet() >= BATCH_SIZE) {
            syncWithDB();
        }
    }
    
    /**
     * Generate cache key for deduplication
     */
    private static String generateCacheKey(int apiCollectionId, String urlType, String methodAndUrl) {
        return String.format("%d_%s_%s", apiCollectionId, urlType, methodAndUrl);
    }
    
    /**
     * Sync pending overage info with database
     */
    private static void syncWithDB() {
        if (pendingOverageInfo.isEmpty()) {
            return;
        }
        
        try {
            // Create indices if they don't exist
            UningestedApiOverageDao.instance.createIndicesIfAbsent();
            
            // Insert all pending records
            for (UningesetedApiOverage uningesetedApiOverage : pendingOverageInfo.values()) {
                try {
                    UningestedApiOverageDao.instance.insertOne(uningesetedApiOverage);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Failed to insert overage info: " + e.getMessage());
                }
            }
            
            int insertedCount = pendingOverageInfo.size();
            loggerMaker.infoAndAddToDb("Synced " + insertedCount + " overage records to database");
            
            // Clear pending records and reset counter
            pendingOverageInfo.clear();
            batchCounter.set(0);
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to sync overage info with database: " + e.getMessage());
        }
    }
    
    /**
     * Get overage statistics for an account
     */
    public static long getOverageCount(int accountId) {
        try {
            return UningestedApiOverageDao.instance.count(UningestedApiOverageDao.generateFilter(accountId));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to get overage count: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Clear cache for testing purposes
     */
    public static void clearCache() {
        overageCache.clear();
        pendingOverageInfo.clear();
        batchCounter.set(0);
    }
    
    /**
     * Get cache size for monitoring
     */
    public static int getCacheSize() {
        return overageCache.size();
    }
    
    /**
     * Get pending batch size for monitoring
     */
    public static int getPendingBatchSize() {
        return pendingOverageInfo.size();
    }
} 