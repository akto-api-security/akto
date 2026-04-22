package com.akto.threat.detection.ip_api_counter;

import java.util.HashMap;
import java.util.Map;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Handles CMS increment + rate limit query (sync) and pushes
 * distribution update messages to a Redis Stream (async).
 *
 * Distribution bucket calculation is done asynchronously by
 * DistributionStreamConsumer via Lua script in Redis.
 */
public class DistributionCalculator {

    private static final LoggerMaker logger = new LoggerMaker(DistributionCalculator.class, LogDb.THREAT_DETECTION);
    private static final String DIST_STREAM_KEY = "dist_stream";

    private final CmsCounterLayer cmsCounterLayer;
    private final StatefulRedisConnection<String, String> redis;

    public DistributionCalculator(CmsCounterLayer cmsCounterLayer, RedisClient redisClient) {
        this.cmsCounterLayer = cmsCounterLayer;
        this.redis = redisClient.connect();
    }

    /**
     * Called per record in MaliciousTrafficDetectorTask hot path.
     *
     * 1. CMS increment + sliding window query (for rate limit) — sync, 1 Redis round trip
     * 2. XADD to dist_stream (for distribution calculation) — async, fire-and-forget
     *
     * @param apiKey           API distribution key (e.g., "123|/api/users|GET")
     * @param currentEpochMin  current epoch minute from the request timestamp
     * @param ipApiCmsKey      CMS item key (e.g., "ipApiCmsData|123|1.2.3.4|/api/users|GET")
     * @param rateLimitWindow  sliding window size in minutes for rate limit check
     * @return count of requests from this IP to this API in the sliding window
     */
    public long processRequest(String apiKey, long currentEpochMin, String ipApiCmsKey, int rateLimitWindow) {
        // Step 1: CMS increment + sliding window query — ONE sync Redis call
        long slidingWindowStart = currentEpochMin - rateLimitWindow + 1;
        long count = cmsCounterLayer.incrementAndQueryRange(
            ipApiCmsKey, currentEpochMin, slidingWindowStart, currentEpochMin
        );

        // Step 2: Push to stream for async distribution update — fire-and-forget
        try {
            Map<String, String> streamBody = new HashMap<>();
            streamBody.put("apiKey", apiKey);
            streamBody.put("ipApiCmsKey", ipApiCmsKey);
            streamBody.put("minute", String.valueOf(currentEpochMin));
            redis.async().xadd(DIST_STREAM_KEY, streamBody);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error pushing to distribution stream");
        }

        return count;
    }

    /**
     * Get the end of the tumbling window for a given epoch minute and window size.
     * Example: currentEpochMin=12:03, windowSize=5 → returns 12:05
     */
    public static long getWindowEnd(long currentEpochMin, int windowSize) {
        return ((currentEpochMin - 1) / windowSize + 1) * windowSize;
    }

    /**
     * Get the start of the tumbling window for a given window end and window size.
     * Example: windowEnd=12:05, windowSize=5 → returns 12:01
     */
    public static long getWindowStart(long windowEnd, int windowSize) {
        return windowEnd - windowSize + 1;
    }
}
