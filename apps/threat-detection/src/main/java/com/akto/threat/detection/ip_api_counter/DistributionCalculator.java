package com.akto.threat.detection.ip_api_counter;

import java.util.HashMap;
import java.util.Map;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.detection.constants.RedisKeyInfo;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Fully async threat processor. No sync Redis calls on the hot path.
 *
 * Hot path: async XADD to Redis stream with all parameters.
 * Background: DistributionStreamConsumer batch-processes via Lua script
 * (CMS increment, distribution update, mitigation check, threshold check).
 */
public class DistributionCalculator {

    private static final LoggerMaker logger = new LoggerMaker(DistributionCalculator.class, LogDb.THREAT_DETECTION);

    private final StatefulRedisConnection<String, String> redis;

    public DistributionCalculator(RedisClient redisClient) {
        this.redis = redisClient.connect();
    }

    /**
     * Called per record in MaliciousTrafficDetectorTask hot path.
     * ZERO sync Redis calls — only an async XADD.
     */
    public void processRequest(String apiKey, long currentEpochMin, String ipApiCmsKey,
                               int rateLimitWindow, long threshold, int mitigationPeriod,
                               String actor, String host, String accountId, int timestamp,
                               String countryCode, String destCountryCode) {
        try {
            Map<String, String> streamBody = new HashMap<>();
            streamBody.put("ipApiCmsKey", ipApiCmsKey);
            streamBody.put("apiKey", apiKey);
            streamBody.put("minute", String.valueOf(currentEpochMin));
            streamBody.put("rateLimitWindow", String.valueOf(rateLimitWindow));
            streamBody.put("threshold", String.valueOf(threshold));
            streamBody.put("mitigationPeriod", String.valueOf(mitigationPeriod));
            streamBody.put("actor", actor);
            streamBody.put("host", host != null ? host : "");
            streamBody.put("accountId", accountId != null ? accountId : "");
            streamBody.put("timestamp", String.valueOf(timestamp));
            streamBody.put("countryCode", countryCode != null ? countryCode : "");
            streamBody.put("destCountryCode", destCountryCode != null ? destCountryCode : "");
            redis.async().xadd(RedisKeyInfo.THREAT_INPUT_STREAM, streamBody);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error pushing to threat input stream");
        }
    }

    public static long getWindowEnd(long currentEpochMin, int windowSize) {
        return ((currentEpochMin - 1) / windowSize + 1) * windowSize;
    }

    public static long getWindowStart(long windowEnd, int windowSize) {
        return windowEnd - windowSize + 1;
    }
}
