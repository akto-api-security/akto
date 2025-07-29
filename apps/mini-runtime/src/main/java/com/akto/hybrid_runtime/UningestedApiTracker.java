package com.akto.hybrid_runtime;

import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.UningesetedApiOverage;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class UningestedApiTracker {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(UningestedApiTracker.class, LogDb.RUNTIME);
    
    // Local cache to avoid frequent DB checks
    private static final ConcurrentHashMap<String, Boolean> overageApisCache = new ConcurrentHashMap<>();
    
    // Batch operations for DB writes
    private static final ConcurrentHashMap<String, UningesetedApiOverage> pendingOverageApisInfo = new ConcurrentHashMap<>();
    
    // Counter for batch size
    private static final AtomicInteger batchCounter = new AtomicInteger(0);
    
    // Batch size threshold
    private static final int BATCH_SIZE = 100;
    
    // Sync interval in seconds
    private static final int SYNC_INTERVAL = 120; // 1 minute
    
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
        if (overageApisCache.containsKey(cacheKey)) {
            return;
        }
        
        // Check if already exists in DB
        if (DataActorFactory.fetchInstance().overageApisExists(apiCollectionId, urlType, methodAndUrl)) {
            overageApisCache.put(cacheKey, true);
            return;
        }
        
        // Create overage info
        UningesetedApiOverage uningesetedApiOverage = new UningesetedApiOverage(
            apiCollectionId,  urlType, methodAndUrl
        );
        
        // Add to pending batch
        pendingOverageApisInfo.put(cacheKey, uningesetedApiOverage);
        overageApisCache.put(cacheKey, true);
        
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
        if (pendingOverageApisInfo.isEmpty()) {
            return;
        }
        
        try {
            // Prepare bulk write operations using BulkUpdates
            List<Object> bulkWrites = new ArrayList<>();
            for (UningesetedApiOverage uningesetedApiOverage : pendingOverageApisInfo.values()) {
                try {
                    // Create filter map for the document
                    Map<String, Object> filters = new HashMap<>();
                    filters.put(UningesetedApiOverage.API_COLLECTION_ID, uningesetedApiOverage.getApiCollectionId());
                    filters.put(UningesetedApiOverage.URL_TYPE, uningesetedApiOverage.getUrlType());
                    filters.put(UningesetedApiOverage.METHOD_AND_URL, uningesetedApiOverage.getMethodAndUrl());
                    
                    // Create updates list
                    ArrayList<String> updates = new ArrayList<>();
                    
                    // Add timestamp update
                    UpdatePayload timestampUpdate = new UpdatePayload(UningesetedApiOverage.TIMESTAMP, uningesetedApiOverage.getTimestamp(), "setOnInsert");
                    updates.add(timestampUpdate.toString());
                    
                    // Create BulkUpdates object
                    BulkUpdates bulkUpdate = new BulkUpdates(filters, updates);
                    bulkWrites.add(bulkUpdate);
                    
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Failed to prepare overage info for bulk write: " + e.getMessage());
                }
            }
            
            // Perform bulk write
            if (!bulkWrites.isEmpty()) {
                DataActorFactory.fetchInstance().bulkWriteOverageInfo(bulkWrites);
            }
            
            int insertedCount = pendingOverageApisInfo.size();
            loggerMaker.infoAndAddToDb("Synced " + insertedCount + " overage records to database");
            
            // Clear pending records and reset counter
            pendingOverageApisInfo.clear();
            batchCounter.set(0);
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to sync overage info with database: " + e.getMessage());
        }
    }
    
    /**
     * Get overage statistics for an account
     */
    /**
     * Clear cache for testing purposes
     */
    public static void clearCache() {
        overageApisCache.clear();
        pendingOverageApisInfo.clear();
        batchCounter.set(0);
    }
    
    /**
     * Get cache size for monitoring
     */
    public static int getCacheSize() {
        return overageApisCache.size();
    }
    
    /**
     * Get pending batch size for monitoring
     */
    public static int getPendingBatchSize() {
        return pendingOverageApisInfo.size();
    }
} 