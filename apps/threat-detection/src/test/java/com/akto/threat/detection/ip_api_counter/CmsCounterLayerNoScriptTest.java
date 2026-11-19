package com.akto.threat.detection.ip_api_counter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmsCounterLayerNoScriptTest {

    private static final String REDIS_URL = "redis://localhost:6379";

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private CmsCounterLayer layer;

    @BeforeEach
    void setUp() {
        redisClient = RedisClient.create(REDIS_URL);
        connection = redisClient.connect();
        layer = CmsCounterLayer.createForParamEnum(redisClient);
    }

    @AfterEach
    void tearDown() {
        connection.close();
        redisClient.shutdown();
    }

    @Test
    void incrementAndQueryRange_recoversAfterScriptCacheFlush() {
        long minute = System.currentTimeMillis() / 60000;
        String key = "noscript-test|" + UUID.randomUUID();

        assertEquals(1L, layer.incrementAndQueryRange(key, minute, minute - 4, minute));

        connection.sync().scriptFlush();

        assertEquals(2L, layer.incrementAndQueryRange(key, minute, minute - 4, minute));
    }

    @Test
    void estimateCountInRange_recoversAfterScriptCacheFlush() {
        long minute = System.currentTimeMillis() / 60000;
        String key = "noscript-test|" + UUID.randomUUID();

        assertEquals(1L, layer.incrementAndQueryRange(key, minute, minute - 4, minute));

        connection.sync().scriptFlush();

        assertEquals(1L, layer.estimateCountInRange(key, minute - 4, minute));
    }
}
