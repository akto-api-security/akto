package com.akto.threat.detection.ip_api_counter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.constants.RedisKeyInfo;

/**
 * Detects IPs enumerating URL/path param values within sliding time windows.
 *
 * Example: An IP hitting /users/1, /users/2, ..., /users/50 in 5 minutes
 * would be flagged as enumeration if threshold is set to 50.
 *
 * Uses:
 * - BloomFilter for deduplication (in-memory only)
 * - CMS for counting unique values (synced to Redis)
 */
public class ParamEnumerationDetector {

    private static final int DEFAULT_EXPECTED_INSERTIONS = 1_000_000;
    private static final double DEFAULT_FALSE_POSITIVE_RATE = 0.01;

    private final BloomFilterLayer bloomFilterLayer;
    private final CmsCounterLayer uniqueCountCmsLayer;
    private final int windowSizeMinutes;
    private final int uniqueValueThreshold;

    private static ParamEnumerationDetector INSTANCE;

    /**
     * Private constructor - use initialize() to create the singleton instance.
     */
    private ParamEnumerationDetector(CounterCache cache, int threshold, int windowSizeMinutes) {
        // BloomFilter is in-memory only (no Redis)
        this.bloomFilterLayer = new BloomFilterLayer(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FALSE_POSITIVE_RATE);

        // Create NEW instance of CmsCounterLayer with different prefix
        this.uniqueCountCmsLayer = CmsCounterLayer.create(cache, RedisKeyInfo.PARAM_ENUM_CMS_PREFIX);

        this.windowSizeMinutes = windowSizeMinutes;
        this.uniqueValueThreshold = threshold;
    }

    /**
     * Initialize the singleton instance.
     *
     * @param cache     Redis cache for CMS persistence
     * @param threshold Number of unique values to trigger detection
     */
    public static void initialize(CounterCache cache, int threshold) {
        initialize(cache, threshold, 5);
    }

    /**
     * Initialize the singleton instance with custom window size.
     *
     * @param cache            Redis cache for CMS persistence
     * @param threshold        Number of unique values to trigger detection
     * @param windowSizeMinutes Size of the sliding window in minutes
     */
    public static void initialize(CounterCache cache, int threshold, int windowSizeMinutes) {
        INSTANCE = new ParamEnumerationDetector(cache, threshold, windowSizeMinutes);
        INSTANCE.startScheduledTasks();
    }

    /**
     * Get the singleton instance.
     */
    public static ParamEnumerationDetector getInstance() {
        return INSTANCE;
    }

    /**
     * Start scheduled cleanup tasks.
     */
    private void startScheduledTasks() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(this::cleanup, 0, 1, TimeUnit.MINUTES);
        // CMS has its own scheduled tasks started via startScheduledTasks()
        uniqueCountCmsLayer.startScheduledTasks();
    }

    /**
     * Cleanup old data from both layers.
     */
    private void cleanup() {
        bloomFilterLayer.cleanup();
        // CMS cleanup is handled by its own scheduled task
    }

    /**
     * Record a request and check if IP is enumerating param values.
     *
     * @param ip              Source IP address (e.g., "1.2.3.4")
     * @param apiCollectionId API collection ID
     * @param method          HTTP method (e.g., "GET", "POST")
     * @param apiTemplate     API template (e.g., "/users/{userId}")
     * @param paramName       Parameter being tracked (e.g., "userId")
     * @param paramValue      Actual parameter value (e.g., "42")
     * @return true if unique values exceed threshold in the sliding window
     */
    public boolean recordAndCheck(String ip, int apiCollectionId, String method, String apiTemplate, String paramName, String paramValue) {
        long currentMin = Context.now() / 60;
        long windowStart = currentMin - windowSizeMinutes + 1;

        String compositeKey = ip + "|" + apiCollectionId + "|" + method + "|" + apiTemplate + "|" + paramName + "|" + paramValue;
        String countKey = ip + "|" + apiCollectionId + "|" + method + "|" + apiTemplate + "|" + paramName;

        // 1. Check if seen in any minute of the window
        if (bloomFilterLayer.mightContainInWindow(compositeKey, windowStart, currentMin)) {
            // Already counted - just return current threshold status
            return uniqueCountCmsLayer.estimateCountInWindow(countKey, windowStart, currentMin)
                   > uniqueValueThreshold;
        }

        // 2. New unique value - add to current minute
        bloomFilterLayer.put(compositeKey, currentMin);
        uniqueCountCmsLayer.increment(countKey, String.valueOf(currentMin));

        // 3. Check threshold
        return uniqueCountCmsLayer.estimateCountInWindow(countKey, windowStart, currentMin)
               > uniqueValueThreshold;
    }

    /**
     * Query unique count without recording.
     *
     * @param ip              Source IP address
     * @param apiCollectionId API collection ID
     * @param method          HTTP method (e.g., "GET", "POST")
     * @param apiTemplate     API template
     * @param paramName       Parameter name
     * @return Estimated number of unique values in the current window
     */
    public long getUniqueCount(String ip, int apiCollectionId, String method, String apiTemplate, String paramName) {
        long currentMin = Context.now() / 60;
        long windowStart = currentMin - windowSizeMinutes + 1;

        String countKey = ip + "|" + apiCollectionId + "|" + method + "|" + apiTemplate + "|" + paramName;
        return uniqueCountCmsLayer.estimateCountInWindow(countKey, windowStart, currentMin);
    }

    /**
     * Reset all data (for testing purposes).
     */
    public void reset() {
        bloomFilterLayer.reset();
        uniqueCountCmsLayer.reset();
    }

}
