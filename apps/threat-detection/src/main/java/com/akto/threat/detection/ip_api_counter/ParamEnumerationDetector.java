package com.akto.threat.detection.ip_api_counter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;

import io.lettuce.core.RedisClient;

/**
 * Detects IPs enumerating URL/path param values within sliding time windows.
 *
 * Example: An IP hitting /users/1, /users/2, ..., /users/50 in 5 minutes
 * would be flagged as enumeration if threshold is set to 50.
 *
 * Uses:
 * - BloomFilter for deduplication (in-memory only)
 * - CMS via Redis (RedisBloom) for counting unique values
 */
public class ParamEnumerationDetector {

    private static final int DEFAULT_EXPECTED_INSERTIONS = 1_000_000;
    private static final double DEFAULT_FALSE_POSITIVE_RATE = 0.01;

    private final BloomFilterLayer bloomFilterLayer;
    private final CmsCounterLayer uniqueCountCmsLayer;
    private final int windowSizeMinutes;
    private final int uniqueValueThreshold;

    private static ParamEnumerationDetector INSTANCE;

    private ParamEnumerationDetector(RedisClient redisClient, int threshold, int windowSizeMinutes) {
        this.bloomFilterLayer = new BloomFilterLayer(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FALSE_POSITIVE_RATE);
        this.uniqueCountCmsLayer = CmsCounterLayer.createForParamEnum(redisClient);
        this.windowSizeMinutes = windowSizeMinutes;
        this.uniqueValueThreshold = threshold;
    }

    // Visible for testing
    public ParamEnumerationDetector(CmsCounterLayer cmsCounterLayer, int threshold, int windowSizeMinutes) {
        this.bloomFilterLayer = new BloomFilterLayer(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FALSE_POSITIVE_RATE);
        this.uniqueCountCmsLayer = cmsCounterLayer;
        this.windowSizeMinutes = windowSizeMinutes;
        this.uniqueValueThreshold = threshold;
    }

    public static void initialize(RedisClient redisClient, int threshold, int windowSizeMinutes) {
        if (redisClient == null) {
            INSTANCE = null;
            return;
        }
        INSTANCE = new ParamEnumerationDetector(redisClient, threshold, windowSizeMinutes);
        INSTANCE.startScheduledTasks();
    }

    public static ParamEnumerationDetector getInstance() {
        return INSTANCE;
    }

    private void startScheduledTasks() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::cleanup, 0, 1, TimeUnit.MINUTES);
    }

    private void cleanup() {
        bloomFilterLayer.cleanup();
    }

    /**
     * Record a request and check if IP is enumerating param values.
     *
     * @return true if unique values exceed threshold in the sliding window
     */
    public boolean recordAndCheck(String ip, int apiCollectionId, String method,
                                   String apiTemplate, String paramName, String paramValue) {
        long currentMin = Context.now() / 60;
        long windowStart = currentMin - windowSizeMinutes + 1;

        String compositeKey = ip + "|" + apiCollectionId + "|" + method + "|" + apiTemplate + "|" + paramName + "|" + paramValue;
        String countKey = ip + "|" + apiCollectionId + "|" + method + "|" + apiTemplate + "|" + paramName;

        // 1. Check if seen in any minute of the window (bloom filter, in-memory)
        if (bloomFilterLayer.mightContainInWindow(compositeKey, windowStart, currentMin)) {
            // Already counted — just return current threshold status
            return uniqueCountCmsLayer.estimateCountInRange(countKey, windowStart, currentMin)
                   > uniqueValueThreshold;
        }

        // 2. New unique value — increment CMS + query range in one call
        bloomFilterLayer.put(compositeKey, currentMin);
        long uniqueCount = uniqueCountCmsLayer.incrementAndQueryRange(
            countKey, currentMin, windowStart, currentMin);

        // 3. Check threshold
        return uniqueCount > uniqueValueThreshold;
    }

    /**
     * Query unique count without recording.
     */
    public long getUniqueCount(String ip, int apiCollectionId, String method,
                                String apiTemplate, String paramName) {
        long currentMin = Context.now() / 60;
        long windowStart = currentMin - windowSizeMinutes + 1;

        String countKey = ip + "|" + apiCollectionId + "|" + method + "|" + apiTemplate + "|" + paramName;
        return uniqueCountCmsLayer.estimateCountInRange(countKey, windowStart, currentMin);
    }

    public void reset() {
        bloomFilterLayer.reset();
    }
}
