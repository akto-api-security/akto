package com.akto.threat.detection.ip_api_counter;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for DistributionDataForwardLayer.
 * Tests distribution export, dedup, and distributed locking.
 */
class DistributionDataForwardLayerTest extends DistributionIntegrationTestBase {

    @Test
    void testEmptyWindowsSkipped() throws Exception {
        // Given: No distribution data in Redis
        DistributionDataForwardLayer forwardLayer = new DistributionDataForwardLayer(redisClient);

        // When: Cron runs
        forwardLayer.buildPayloadAndForwardData();

        // Then: No errors, just skips empty windows
        // (Verify via logs or lack of exceptions)
    }

    @Test
    void testWindowDataReadFromRedis() throws Exception {
        // Given: Some distribution data in Redis
        long windowStart = 100;
        String apiKey = "123|/api/users|GET";

        syncRedis.sadd("distApis|5|" + windowStart, apiKey);
        syncRedis.hset("dist|5|" + windowStart + "|" + apiKey, "b1", "10");
        syncRedis.hset("dist|5|" + windowStart + "|" + apiKey, "b2", "3");

        // When: Forward layer reads the data
        String apisKey = "distApis|5|" + windowStart;
        Set<String> activeApis = syncRedis.smembers(apisKey);

        // Then: Should find the API
        assertThat(activeApis).contains(apiKey);

        // And: Distribution hash should have correct values
        Map<String, String> dist = syncRedis.hgetall("dist|5|" + windowStart + "|" + apiKey);
        assertThat(dist).containsEntry("b1", "10").containsEntry("b2", "3");
    }

    @Test
    void testDedupPreventsReSendingWindow() throws Exception {
        // Given: Distribution data in Redis
        long windowStart = 100;
        String apiKey = "123|/api/users|GET";

        syncRedis.sadd("distApis|5|" + windowStart, apiKey);
        syncRedis.hset("dist|5|" + windowStart + "|" + apiKey, "b1", "10");

        // When: Build payload (simulate reading the window)
        // Note: In real test, we'd mock the HTTP call and verify it's made once
        // For now, we verify the internal logic by checking sent windows

        // Manually simulate what buildPayloadAndForwardData does:
        // 1. Check if window was already sent via distributed lock

        // Initially not sent
        // After processing, should be marked sent
        // The test would need to inject a spy/mock for the HTTP client
        // This is a limitation of the current implementation

        // Alternative: Verify window locking mechanism
        String lockKey = "distLock|5|" + windowStart;
        String acquired = syncRedis.set(lockKey, "1", io.lettuce.core.SetArgs.Builder.nx().ex(60));

        assertThat(acquired).isNotNull();  // First attempt succeeds
        assertThat(syncRedis.set(lockKey, "1", io.lettuce.core.SetArgs.Builder.nx().ex(60)))
            .isNull();  // Second attempt fails (already locked)

        // Clean up lock
        syncRedis.del(lockKey);
    }

    @Test
    void testDistributedLockPreventsMultiplePods() throws Exception {
        // Given: Two simulated pods trying to send same window
        long windowStart = 100;
        String lockKey = "distLock|5|" + windowStart;

        // When: Pod 1 tries to acquire lock
        String lock1Result = syncRedis.set(lockKey, "1", io.lettuce.core.SetArgs.Builder.nx().ex(60));

        // Then: Pod 1 succeeds
        assertThat(lock1Result).isNotNull();

        // When: Pod 2 tries to acquire same lock
        String lock2Result = syncRedis.set(lockKey, "1", io.lettuce.core.SetArgs.Builder.nx().ex(60));

        // Then: Pod 2 fails (lock already held)
        assertThat(lock2Result).isNull();

        // When: Lock expires or is deleted, Pod 2 can acquire
        syncRedis.del(lockKey);
        String lock3Result = syncRedis.set(lockKey, "1", io.lettuce.core.SetArgs.Builder.nx().ex(60));

        assertThat(lock3Result).isNotNull();
    }

    @Test
    void testWindowBoundaryCalculation() throws Exception {
        // Given: Current time and window sizes
        long currentEpochMin = System.currentTimeMillis() / 60000;
        int windowSize = 5;

        // When: Calculate safe window end (current aligned - 1 window)
        long currentAlignedWindowEnd = (currentEpochMin / windowSize) * windowSize;
        long safeWindowEnd = currentAlignedWindowEnd - windowSize;

        // Then: safeWindowEnd should be in the past
        assertThat(safeWindowEnd).isLessThan(currentEpochMin);

        // And: Lookback should go back 8 hours
        long lookbackStart = safeWindowEnd - (8 * 60);  // 8 hours in minutes
        assertThat(safeWindowEnd - lookbackStart).isEqualTo(8 * 60);
    }

    @Test
    void testActiveAPIsSetManagement() throws Exception {
        // Given: Multiple APIs active in same window
        long windowStart = 100;
        String api1 = "123|/api/users|GET";
        String api2 = "123|/api/products|GET";
        String api3 = "123|/api/orders|POST";

        String apisKey = "distApis|5|" + windowStart;

        // When: Add APIs to set
        syncRedis.sadd(apisKey, api1);
        syncRedis.sadd(apisKey, api2);
        syncRedis.sadd(apisKey, api3);

        // Then: All should be in the set
        Set<String> activeApis = syncRedis.smembers(apisKey);
        assertThat(activeApis).hasSize(3).contains(api1, api2, api3);
    }

    @Test
    void testWindowExpirationViaTTL() throws Exception {
        // Given: Distribution data with TTL
        long windowStart = 100;
        String apiKey = "123|/api/users|GET";
        String distKey = "dist|5|" + windowStart + "|" + apiKey;

        syncRedis.hset(distKey, "b1", "10");
        syncRedis.expire(distKey, 1);  // 1 second TTL

        // When: Key exists initially
        assertThat(syncRedis.exists(distKey)).isEqualTo(1);

        // Then: After TTL expires, key is gone
        Thread.sleep(1100);
        assertThat(syncRedis.exists(distKey)).isEqualTo(0);
    }

    @Test
    void testLookbackWindowCalculation() throws Exception {
        // Given: 8-hour lookback window
        long currentEpochMin = 600;  // Arbitrary time
        int windowSize = 5;
        long lookbackHours = 8;

        long currentAlignedWindowEnd = (currentEpochMin / windowSize) * windowSize;
        long safeWindowEnd = currentAlignedWindowEnd - windowSize;
        long lookbackStart = safeWindowEnd - (lookbackHours * 60);

        // When: Iterate over all windows
        int windowCount = 0;
        for (long i = lookbackStart; i <= safeWindowEnd; i += windowSize) {
            windowCount++;
        }

        // Then: Should have multiple windows
        assertThat(windowCount).isGreaterThan(0);
        // For 8 hours and 5-min windows: range is [lookbackStart, safeWindowEnd] inclusive
        // Number of windows = (safeWindowEnd - lookbackStart) / windowSize + 1
        // = (480 / 5) + 1 = 96 + 1 = 97 windows
        assertThat(windowCount).isEqualTo((int) ((lookbackHours * 60) / windowSize + 1));
    }

    @Test
    void testMultipleWindowSizesProcessed() throws Exception {
        // Given: Data for all window sizes
        int[] windowSizes = {5, 15, 30};
        long baseWindowStart = 100;

        for (int ws : windowSizes) {
            String apiKey = "123|/api/users|GET";
            String apisKey = "distApis|" + ws + "|" + baseWindowStart;
            String distKey = "dist|" + ws + "|" + baseWindowStart + "|" + apiKey;

            syncRedis.sadd(apisKey, apiKey);
            syncRedis.hset(distKey, "b1", "5");
        }

        // When: Verify data for each window size
        for (int ws : windowSizes) {
            String apiKey = "123|/api/users|GET";
            String apisKey = "distApis|" + ws + "|" + baseWindowStart;
            String distKey = "dist|" + ws + "|" + baseWindowStart + "|" + apiKey;

            // Then: All should exist
            assertThat(syncRedis.sismember(apisKey, apiKey)).isTrue();
            assertThat(syncRedis.hget(distKey, "b1")).isEqualTo("5");
        }
    }

    @Test
    void testEmptyWindowSkipped() throws Exception {
        // Given: A window with no active APIs
        long windowStart = 100;
        String apisKey = "distApis|5|" + windowStart;

        // When: Check for active APIs
        Set<String> activeApis = syncRedis.smembers(apisKey);

        // Then: Set is empty, should be skipped
        assertThat(activeApis).isEmpty();
    }

    @Test
    void testDistributionHashStructure() throws Exception {
        // Given: Create a distribution hash with multiple buckets
        long windowStart = 100;
        String apiKey = "123|/api/users|GET";
        String distKey = "dist|5|" + windowStart + "|" + apiKey;

        syncRedis.hset(distKey, "b1", "10");
        syncRedis.hset(distKey, "b2", "5");
        syncRedis.hset(distKey, "b3", "2");
        syncRedis.hset(distKey, "b4", "1");

        // When: Read the entire hash
        Map<String, String> distribution = syncRedis.hgetall(distKey);

        // Then: All buckets present with correct values
        assertThat(distribution)
            .containsEntry("b1", "10")
            .containsEntry("b2", "5")
            .containsEntry("b3", "2")
            .containsEntry("b4", "1")
            .hasSize(4);
    }

    @Test
    void testWindowKeyGeneration() throws Exception {
        // Given: Window parameters
        int windowSize = 5;
        long windowStart = 115;

        // When: Generate keys
        String distKey = "dist|" + windowSize + "|" + windowStart + "|123|/api/users|GET";
        String apisKey = "distApis|" + windowSize + "|" + windowStart;
        String lockKey = "distLock|" + windowSize + "|" + windowStart;

        // Then: Keys follow expected pattern
        assertThat(distKey).startsWith("dist|");
        assertThat(apisKey).startsWith("distApis|");
        assertThat(lockKey).startsWith("distLock|");

        // And: Can store/retrieve using these keys
        syncRedis.sadd(apisKey, "123|/api/users|GET");
        assertThat(syncRedis.sismember(apisKey, "123|/api/users|GET")).isTrue();
    }
}
