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
    private static final int CMS_DATA_RETENTION_MINUTES = 480; // retain last 8 hours

    /*
     * 12:01 -> CMS
     * 12:02 -> CMS
     * 12:03 -> CMS
     * Each CMS stores: IP|API -> Count of calls for this API from this IP
     */
    private final ConcurrentHashMap<String, CountMinSketch> sketches = new ConcurrentHashMap<>();
    private final CounterCache cache;
    private final String redisKeyPrefix;
    private final LoggerMaker logger;

    // Default singleton for backward compatibility
    private static CmsCounterLayer DEFAULT_INSTANCE;

    /**
     * Constructor for creating new instances with custom cache and Redis key prefix.
     */
    public CmsCounterLayer(CounterCache cache, String redisKeyPrefix) {
        this.cache = cache;
        this.redisKeyPrefix = redisKeyPrefix;
        this.logger = new LoggerMaker(CmsCounterLayer.class, LogDb.THREAT_DETECTION);
    }

    /**
     * Backward compatible singleton access.
     */
    public static CmsCounterLayer getInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Initialize the default instance (called from Main).
     * This maintains backward compatibility with existing code.
     */
    public static void initialize(RedisClient redisClient) {
        CounterCache cache = redisClient != null ? new ApiCountCacheLayer(redisClient) : null;
        DEFAULT_INSTANCE = new CmsCounterLayer(cache, RedisKeyInfo.IP_API_CMS_DATA_PREFIX);
        DEFAULT_INSTANCE.startScheduledTasks();
    }

    /**
     * Factory method for creating new instances with custom configuration.
     */
    public static CmsCounterLayer create(CounterCache cache, String redisKeyPrefix) {
        return new CmsCounterLayer(cache, redisKeyPrefix);
    }

    /**
     * Start scheduled tasks for cleanup and Redis sync.
     */
    public void startScheduledTasks() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(this::cleanupOldWindows, 0, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::syncToRedis, 0, 1, TimeUnit.MINUTES);
    }

    public void increment(String key, String windowKey) {
        CountMinSketch cms = sketches.computeIfAbsent(windowKey, k ->
            new CountMinSketch(EPSILON, CONFIDENCE, SEED)
        );
        cms.add(key, 1);
    }

    public long estimateCount(String key, String windowKey) {
        CountMinSketch cms = sketches.get(windowKey);
        if (cms == null) {
            cms = fetchFromRedis(redisKeyPrefix + "|" + windowKey);
            if (cms != null) {
                sketches.put(windowKey, cms);
            } else {
                return 0;
            }
        }
        return cms.estimateCount(key);
    }

    /**
     *   Estimate count across a sliding window range.
     *   Sliding because we are storing CMS for each minute.
     *   [12:01-12:05] [12:06-12:10] [12:11-12:15]
     *   ↑             ↑             ↑
     *   All requests   All requests  All requests
     *   at 12:03       at 12:08      at 12:13
     *   check here     check here    check here
     */
    public long estimateCountInWindow(String key, long windowStart, long windowEnd) {
        long sum = 0;
        for (long min = windowStart; min <= windowEnd; min++) {
            sum += estimateCount(key, String.valueOf(min));
        }
        return sum;
    }

    /**
     * Cleanup CMS from in-memory HashMap that are more than 8 hours old.
     * Redis will automatically handle the expiration of keys.
     */
    public void cleanupOldWindows() {
        logger.debug("cleanupOldWindows triggered at " + Context.now());
        long currentEpochMin = Context.now() / 60;
        long startWindow = currentEpochMin - 540; // ~9 hours ago
        long endWindow = currentEpochMin - 481; // 8 hours + 1 minute ago

        for (long i = startWindow; i < endWindow; i++) {
            String windowKey = String.valueOf(i);
            if (sketches.containsKey(windowKey)) {
                sketches.remove(windowKey);
            }
        }
    }

    private void syncToRedis() {
        if (cache == null) {
            return;
        }
        logger.debug("syncToRedis triggered at " + Context.now());
        long currentEpochMin = Context.now()/60;
        long startWindow = currentEpochMin - 5;

        for (long i = startWindow; i < currentEpochMin; i++) {
            String windowKey = String.valueOf(i);
            CountMinSketch cms = sketches.get(windowKey);
            if (cms == null || cms.size() == 0) continue;

            try {
                byte[] serialized = CountMinSketch.serialize(cms);
                String redisKey = redisKeyPrefix + "|" + windowKey;
                cache.setBytesWithExpiry(redisKey, serialized, CMS_DATA_RETENTION_MINUTES * 60);
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error in sync to redis CountMinSketch for window " + windowKey);
            }

        }
    }

    private CountMinSketch fetchFromRedis(String windowKey) {
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

    public void reset() {
        sketches.clear();
    }

}
