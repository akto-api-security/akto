package com.akto.threat.backend.cache;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.log.LoggerMaker;
import com.akto.threat.backend.service.MaliciousEventService;
import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.util.ThreatDetectionConstants;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoredEventCache {

    private static final LoggerMaker logger = new LoggerMaker(MaliciousEventService.class);

    // Cache for ignored URL+filter combinations per account
    // Using Set for O(1) lookups
    private static final Map<String, Set<String>> ignoredCombinationsCache = new ConcurrentHashMap<>();
    // Track last refresh time per account
    private static final Map<String, Long> lastCacheRefreshPerAccount = new ConcurrentHashMap<>();
    private static final long CACHE_REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static MongoClient mongoClient = null;

    public static void setMongoClient(MongoClient client) {
        mongoClient = client;
    }

    public static void refreshCacheFromDB(String accountId) {
        if (mongoClient == null) {
            logger.error("MongoClient not set for IgnoredEventCache");
            return;
        }

        long currentTime = System.currentTimeMillis();
        Long lastRefresh = lastCacheRefreshPerAccount.get(accountId);

        // Only refresh if interval has passed or this is the first load for this account
        if (lastRefresh != null && currentTime - lastRefresh < CACHE_REFRESH_INTERVAL_MS) {
            return;
        }

        lastCacheRefreshPerAccount.put(accountId, currentTime);
        logger.debug("Refreshing ignored cache from DB for account " + accountId);

        try {
            // Clear existing cache for this account
            Set<String> ignoredSet = ConcurrentHashMap.newKeySet();

            // Query DB for all ignored events
            MongoCollection<MaliciousEventDto> collection = mongoClient
                .getDatabase(accountId)
                .getCollection(MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS, MaliciousEventDto.class);

            Bson filter = Filters.eq("status", ThreatDetectionConstants.IGNORED);

            try (MongoCursor<MaliciousEventDto> cursor = collection.find(filter).cursor()) {
                while (cursor.hasNext()) {
                    MaliciousEventDto event = cursor.next();
                    String cacheKey = getCacheKey(event.getLatestApiEndpoint(), event.getFilterId());
                    ignoredSet.add(cacheKey);
                }
            }

            // Update cache
            ignoredCombinationsCache.put(accountId, ignoredSet);
            logger.info("Loaded " + ignoredSet.size() + " ignored entries into cache for account " + accountId);

        } catch (Exception e) {
            logger.error("Error refreshing ignored cache from DB", e);
        }
    }

    private static String getCacheKey(String url, String filterId) {
        return url + ":" + filterId;
    }

    public static boolean isIgnoredInCache(String accountId, String url, String filterId) {
        // Refresh cache if needed
        refreshCacheFromDB(accountId);

        Set<String> ignoredSet = ignoredCombinationsCache.get(accountId);
        if (ignoredSet == null) {
            return false;
        }

        String cacheKey = getCacheKey(url, filterId);
        return ignoredSet.contains(cacheKey);
    }

}