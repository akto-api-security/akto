package com.akto.threat.backend.cache;

import com.akto.log.LoggerMaker;
import com.akto.threat.backend.service.MaliciousEventService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TriagedEventCache {
    
    private static final LoggerMaker logger = new LoggerMaker(MaliciousEventService.class);
    
    // Cache entry class to store triaged combinations with timestamp
    private static class TriagedCacheEntry {
        private final long timestamp;
        
        public TriagedCacheEntry() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired(long currentTime, long expiryDurationMs) {
            return (currentTime - timestamp) > expiryDurationMs;
        }
    }

    // Cache for triaged URL+filter combinations per account
    private static final Map<String, Map<String, TriagedCacheEntry>> triagedCombinationsCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static long lastCacheCleanup = System.currentTimeMillis();

    public static void cleanupExpiredCacheEntries() {
        long currentTime = System.currentTimeMillis();
        
        // Only cleanup every 5 minutes to avoid frequent iterations
        if (currentTime - lastCacheCleanup < CACHE_EXPIRY_MS) {
            return;
        }
        
        lastCacheCleanup = currentTime;
        logger.debug("Starting cache cleanup for triaged combinations");
        
        triagedCombinationsCache.entrySet().removeIf(accountEntry -> {
            Map<String, TriagedCacheEntry> accountCache = accountEntry.getValue();
            accountCache.entrySet().removeIf(entry -> 
                entry.getValue().isExpired(currentTime, CACHE_EXPIRY_MS)
            );
            // Remove account cache if empty
            return accountCache.isEmpty();
        });
        
        logger.debug("Cache cleanup completed");
    }

    private static String getCacheKey(String url, String filterId) {
        return url + ":" + filterId;
    }

    public static boolean isTriagedInCache(String accountId, String url, String filterId) {
        Map<String, TriagedCacheEntry> accountCache = triagedCombinationsCache.get(accountId);
        if (accountCache == null) {
            return false;
        }
        
        String cacheKey = getCacheKey(url, filterId);
        TriagedCacheEntry entry = accountCache.get(cacheKey);
        if (entry == null) {
            return false;
        }
        
        // Check if entry is expired
        if (entry.isExpired(System.currentTimeMillis(), CACHE_EXPIRY_MS)) {
            accountCache.remove(cacheKey);
            return false;
        }
        
        return true;
    }

    public static void addToTriagedCache(String accountId, String url, String filterId) {
        triagedCombinationsCache.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>())
            .put(getCacheKey(url, filterId), new TriagedCacheEntry());
        logger.debug("Added to triage cache: " + url + ":" + filterId + " for account " + accountId);
    }

    public static void removeFromTriagedCache(String accountId, String url, String filterId) {
        Map<String, TriagedCacheEntry> accountCache = triagedCombinationsCache.get(accountId);
        if (accountCache != null) {
            accountCache.remove(getCacheKey(url, filterId));
            logger.debug("Removed from triage cache: " + url + ":" + filterId + " for account " + accountId);
            
            // Remove account cache if empty
            if (accountCache.isEmpty()) {
                triagedCombinationsCache.remove(accountId);
            }
        }
    }
}