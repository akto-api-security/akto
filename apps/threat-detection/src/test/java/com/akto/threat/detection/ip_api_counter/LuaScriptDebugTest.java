package com.akto.threat.detection.ip_api_counter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Debug test to verify Lua script loading and basic execution.
 */
class LuaScriptDebugTest {

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redis;
    private RedisCommands<String, String> syncRedis;

    @BeforeEach
    void setUp() {
        redisClient = RedisClient.create("redis://localhost:6379");
        redis = redisClient.connect();
        syncRedis = redis.sync();
        syncRedis.flushdb();
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    @Test
    void testLuaScriptLoads() throws Exception {
        // Load a simple Lua script
        String script = "return 'hello from lua'";
        String sha = syncRedis.scriptLoad(script);
        assertThat(sha).isNotNull().isNotEmpty();
        System.out.println("Lua script loaded with SHA: " + sha);
    }

    @Test
    void testAsyncThreatProcessLuaScriptLoads() throws Exception {
        // Load the actual async_threat_process script
        String script = loadLuaScript("lua/async_threat_process.lua");
        assertThat(script).isNotNull().isNotEmpty();
        System.out.println("async_threat_process.lua loaded, length: " + script.length());

        String sha = syncRedis.scriptLoad(script);
        assertThat(sha).isNotNull().isNotEmpty();
        System.out.println("async_threat_process.lua loaded with SHA: " + sha);
    }

    @Test
    void testSimpleLuaExecution() throws Exception {
        // Test simple Lua execution
        String script = "redis.call('set', 'test_key', 'test_value') return 'ok'";
        String sha = syncRedis.scriptLoad(script);

        // Execute via EVALSHA
        String result = syncRedis.evalsha(sha, io.lettuce.core.ScriptOutputType.VALUE, new String[]{});
        assertThat(result).isEqualTo("ok");

        // Verify the key was set
        String value = syncRedis.get("test_key");
        assertThat(value).isEqualTo("test_value");
    }

    private static String loadLuaScript(String resourcePath) {
        try (java.io.InputStream is = LuaScriptDebugTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + resourcePath);
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script: " + resourcePath, e);
        }
    }
}
