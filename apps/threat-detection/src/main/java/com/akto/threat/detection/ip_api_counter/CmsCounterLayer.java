package com.akto.threat.detection.ip_api_counter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * CMS Counter Layer backed entirely by Redis (RedisBloom CMS).
 * No in-memory state. All CMS operations are performed via Lua scripts
 * for atomicity and minimal round trips.
 */
public class CmsCounterLayer {

    private static final double EPSILON = 0.01;
    private static final double DELTA = 0.01;
    private static final int TTL_SECONDS = 8 * 60 * 60; // 8 hours

    private static final String CMS_KEY_PREFIX = "cms|";
    private static final String PARAM_CMS_KEY_PREFIX = "paramCms|";

    private final StatefulRedisConnection<String, String> connection;
    private final String incrementAndQuerySha;
    private final String rangeQuerySha;
    private final String keyPrefix;
    private final LoggerMaker logger;

    private static CmsCounterLayer DEFAULT_INSTANCE;

    public CmsCounterLayer(RedisClient redisClient, String keyPrefix) {
        this.connection = redisClient.connect();
        this.keyPrefix = keyPrefix;
        this.logger = new LoggerMaker(CmsCounterLayer.class, LogDb.THREAT_DETECTION);

        RedisCommands<String, String> sync = connection.sync();
        this.incrementAndQuerySha = sync.scriptLoad(loadLuaScript("lua/cms_increment_and_query.lua"));
        this.rangeQuerySha = sync.scriptLoad(loadLuaScript("lua/cms_range_query.lua"));

        logger.infoAndAddToDb("CmsCounterLayer initialized with prefix: " + keyPrefix);
    }

    public static void initialize(RedisClient redisClient) {
        if (redisClient == null) {
            DEFAULT_INSTANCE = null;
            return;
        }
        DEFAULT_INSTANCE = new CmsCounterLayer(redisClient, CMS_KEY_PREFIX);
    }

    public static CmsCounterLayer getInstance() {
        return DEFAULT_INSTANCE;
    }

    public static CmsCounterLayer createForParamEnum(RedisClient redisClient) {
        return new CmsCounterLayer(redisClient, PARAM_CMS_KEY_PREFIX);
    }

    /**
     * Increment CMS for current minute AND query across a sliding window range.
     * Single Redis round trip via Lua.
     *
     * @param itemKey     the string to increment/query in CMS (e.g., "ipApiCmsData|123|1.2.3.4|/api|GET")
     * @param currentMin  current epoch minute
     * @param windowStart start of sliding window (epoch minute)
     * @param windowEnd   end of sliding window (epoch minute)
     * @return total estimated count across the window (includes the just-incremented value)
     */
    public long incrementAndQueryRange(String itemKey, long currentMin, long windowStart, long windowEnd) {
        int windowSize = (int) (windowEnd - windowStart + 1);
        String[] keys = new String[windowSize + 1];
        keys[0] = keyPrefix + currentMin;
        for (int i = 0; i < windowSize; i++) {
            keys[i + 1] = keyPrefix + (windowStart + i);
        }

        String[] argv = new String[]{
            itemKey,
            String.valueOf(TTL_SECONDS),
            String.valueOf(EPSILON),
            String.valueOf(DELTA)
        };

        try {
            Long result = connection.sync().evalsha(
                incrementAndQuerySha, ScriptOutputType.INTEGER, keys, argv
            );
            return result != null ? result : 0L;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error in incrementAndQueryRange for " + itemKey);
            return 0L;
        }
    }

    /**
     * Query-only across a range. No increment.
     * Used by ParamEnumerationDetector for threshold checks.
     *
     * @param itemKey     the string to query
     * @param windowStart start of range (epoch minute)
     * @param windowEnd   end of range (epoch minute)
     * @return total estimated count across the range
     */
    public long estimateCountInRange(String itemKey, long windowStart, long windowEnd) {
        int windowSize = (int) (windowEnd - windowStart + 1);
        String[] keys = new String[windowSize];
        for (int i = 0; i < windowSize; i++) {
            keys[i] = keyPrefix + (windowStart + i);
        }

        String[] argv = new String[]{itemKey};

        try {
            Long result = connection.sync().evalsha(
                rangeQuerySha, ScriptOutputType.INTEGER, keys, argv
            );
            return result != null ? result : 0L;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error in estimateCountInRange for " + itemKey);
            return 0L;
        }
    }

    private static String loadLuaScript(String resourcePath) {
        try (InputStream is = CmsCounterLayer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script: " + resourcePath, e);
        }
    }
}
