package com.akto.threat.detection.ip_api_counter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.constants.RedisKeyInfo;
import com.clearspring.analytics.stream.frequency.CountMinSketch;

import io.lettuce.core.RedisClient;

public class CmsCounterLayer {

    private static final double EPSILON = 0.01; // 1% error
    private static final double CONFIDENCE = 0.99; // 99% confidence
    private static final int SEED = 12345; // fixed seed for reproducibility
    private static final int CMS_DATA_RETENTION_MINUTES = 60; // retain last 60 minutes

    /*
     * 12:01 -> CMS
     * 12:02 -> CMS
     * 12:03 -> CMS
     * Each CMS stores: IP|API -> Count of calls for this API from this IP
     */
    private static final ConcurrentHashMap<String, CountMinSketch> sketches = new ConcurrentHashMap<>();
    private static CounterCache cache;
    private static final LoggerMaker logger = new LoggerMaker(CmsCounterLayer.class, LogDb.THREAT_DETECTION);
    private static final CmsCounterLayer INSTANCE = new CmsCounterLayer();

    private CmsCounterLayer() {
    }

    public static CmsCounterLayer getInstance() {
        return INSTANCE;
    }

    public static void initialize(RedisClient redisClient) {
        if (redisClient != null) {
            cache = new ApiCountCacheLayer(redisClient);
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
            scheduler.scheduleAtFixedRate(CmsCounterLayer::cleanupOldWindows, 0, 1, TimeUnit.MINUTES);
            scheduler.scheduleAtFixedRate(CmsCounterLayer::syncToRedis, 0, 1, TimeUnit.MINUTES);
        } else {
            cache = null;
        }
    }

    public void increment(String key, String windowKey) {
        CountMinSketch cms = sketches.computeIfAbsent(windowKey, k ->
            new CountMinSketch(EPSILON, CONFIDENCE, SEED)
        );
        cms.add(key, 1);
    }

    // Key is count of API calls for this IP,
    // e.g., "ipApiCmsData|10.2.3.4|123|/api/v1/resource|GET"

    // windowKey is timestamp in minutes.
    public long estimateCount(String key, String windowKey) {
        CountMinSketch cms = sketches.get(windowKey);
        if (cms == null) {
            cms = fetchFromRedis(RedisKeyInfo.IP_API_CMS_DATA_PREFIX + "|" + windowKey);
            if (cms != null) {
                sketches.put(windowKey, cms);
            } else {
                return 0;
            }
        }
        return cms.estimateCount(key);
    }

    /**
     * Cleanup CMS from in-memory HashMap that are more than 60 minutes old.
     * Redis will automatically handle the expiration of keys.
     */
    public static void cleanupOldWindows() {
        logger.debug("cleanupOldWindows triggered at " + Context.now());
        long currentEpochMin = Context.now() / 60;
        long startWindow = currentEpochMin - 90;
        long endWindow = currentEpochMin - 61;

        for (long i = startWindow; i < endWindow; i++) {
            String windowKey = String.valueOf(i);
            if (sketches.containsKey(windowKey)) {
                sketches.remove(windowKey);
            }
        }
    }

    private static void syncToRedis() {
        logger.debug("syncToRedis triggered at " + Context.now());
        long currentEpochMin = Context.now()/60;
        long startWindow = currentEpochMin - 5;
   
        for (long i = startWindow; i < currentEpochMin; i++) {
            String windowKey = String.valueOf(i);
            CountMinSketch cms = sketches.get(windowKey);
            if (cms == null || cms.size() == 0) continue;

            try {
                byte[] serialized = CountMinSketch.serialize(cms);
                String redisKey = RedisKeyInfo.IP_API_CMS_DATA_PREFIX + "|" + windowKey;
                cache.setBytesWithExpiry(redisKey, serialized, CMS_DATA_RETENTION_MINUTES * 60);
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error in sync to redis CountMinSketch for window " + windowKey);
            }

        }
    }

    private static CountMinSketch fetchFromRedis(String windowKey) {
        if (cache == null) {
            return null;
        }
        byte[] val = cache.fetchDataBytes(windowKey);
        if (val != null) {
            return CountMinSketch.deserialize(val);
        }
        return null;
    }

    public CountMinSketch getSketch(String windowKey) {
        return sketches.get(windowKey);
    }

    public byte[] serializeSketch(CountMinSketch cms) throws IOException {
        return CountMinSketch.serialize(cms);
    }

    public CountMinSketch deserializeSketch(byte[] data) throws IOException {
        return CountMinSketch.deserialize(data);
    }

    public static void reset() {
        sketches.clear();
    }

}
