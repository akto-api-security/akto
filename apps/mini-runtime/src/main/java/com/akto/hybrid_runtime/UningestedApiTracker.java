package com.akto.hybrid_runtime;

import com.akto.data_actor.DataActorFactory;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.type.URLMethods;
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
    private static final ConcurrentHashMap<String, UningestedApiOverage> pendingOverageApisInfo = new ConcurrentHashMap<>();
    
    // Counter for batch size
    private static final AtomicInteger batchCounter = new AtomicInteger(0);
    
    // Batch size threshold
    private static final int BATCH_SIZE = 100;
    
    // Sync interval in seconds
    private static final int SYNC_INTERVAL = 120; // 2 minute
    
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
    public static void recordOverage(int apiCollectionId, String urlType, URLMethods.Method method, String url) {
        
        String cacheKey = generateCacheKey(apiCollectionId, urlType, method, url);
        
        // Check if already recorded in cache
        if (overageApisCache.containsKey(cacheKey)) {
            return;
        }
        
        // Check if already exists in DB
        if (DataActorFactory.fetchInstance().overageApisExists(apiCollectionId, urlType, method, url)) {
            overageApisCache.put(cacheKey, true);
            return;
        }
        
        // Create overage info
        UningestedApiOverage uningestedApiOverage = new UningestedApiOverage(
            apiCollectionId,  urlType, method, url
        );
        
        // Add to pending batch
        pendingOverageApisInfo.put(cacheKey, uningestedApiOverage);
        overageApisCache.put(cacheKey, true);
        
        // Log the overage
        loggerMaker.infoAndAddToDb(String.format("Overage recorded -  Collection: %d, Type: %s, " +
                "Method: %s, url: %s", apiCollectionId, urlType, method, url));
        
        // Check if batch is full
        if (batchCounter.incrementAndGet() >= BATCH_SIZE) {
            syncWithDB();
        }
    }
    
    /**
     * Generate cache key for deduplication
     */
    private static String generateCacheKey(int apiCollectionId, String urlType, URLMethods.Method method, String url) {
        return String.format("%d_%s_%s_%s", apiCollectionId, urlType, method.name(), url);
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
            for (UningestedApiOverage uningestedApiOverage : pendingOverageApisInfo.values()) {
                try {
                    // Create filter map for the document
                    Map<String, Object> filters = new HashMap<>();
                    filters.put(UningestedApiOverage.API_COLLECTION_ID, uningestedApiOverage.getApiCollectionId());
                    filters.put(UningestedApiOverage.URL_TYPE, uningestedApiOverage.getUrlType());
                    filters.put(UningestedApiOverage.METHOD, uningestedApiOverage.getMethod());
                    filters.put(UningestedApiOverage.URL, uningestedApiOverage.getUrl());
                    
                    // Create updates list
                    ArrayList<String> updates = new ArrayList<>();
                    
                    // Add timestamp update
                    UpdatePayload timestampUpdate = new UpdatePayload(UningestedApiOverage.TIMESTAMP, uningestedApiOverage.getTimestamp(), "setOnInsert");
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
}