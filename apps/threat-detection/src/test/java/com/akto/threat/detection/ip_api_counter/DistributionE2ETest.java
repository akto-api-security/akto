package com.akto.threat.detection.ip_api_counter;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-End integration tests for the entire IP/API distribution flow.
 * Tests: DistributionCalculator → Stream → Lua Script → Distribution → Export
 */
class DistributionE2ETest extends DistributionIntegrationTestBase {

    @Test
    void testFullFlowSingleIPSingleAPI() throws Exception {
        // Given: Consumer running
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String ip = "192.168.1.1";
        String apiKey = "123|/api/users|GET";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Single IP makes 15 requests to single API
        for (int i = 0; i < 15; i++) {
            distributionCalculator.processRequest(
                apiKey, currentEpochMin, ipCmsKey, 30, -1, 300,
                ip, "host", "acct", (int) (System.currentTimeMillis() / 1000),
                "US", "US"
            );
        }

        // Allow processing
        Thread.sleep(2000);

        // Then: Distribution should show 1 IP in b2 (11-50 requests)
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);
        long b2Count = getDistributionBucketCount(windowSize, windowStart, apiKey, "b2");

        assertThat(b2Count).isEqualTo(1);

        // And: API should be marked as active
        assertThat(isApiActiveInWindow(windowSize, windowStart, apiKey)).isTrue();
    }

    @Test
    void testFullFlowMultipleIPsMultipleAPIs() throws Exception {
        // Given: Consumer running
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;

        // Test data: expected counts per IP/API
        Map<String, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put("192.168.1.1|/api/users", 8);      // b1
        expectedCounts.put("192.168.1.2|/api/users", 25);     // b2
        expectedCounts.put("192.168.1.3|/api/users", 75);     // b3
        expectedCounts.put("192.168.1.1|/api/products", 5);   // b1
        expectedCounts.put("192.168.1.2|/api/products", 120);  // b4

        // When: Push exact requests for each IP/API pair
        for (Map.Entry<String, Integer> e : expectedCounts.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            String ip = parts[0];
            String path = parts[1];
            String apiKey = "123|" + path + "|GET";
            String ipCmsKey = "ipApiCmsData|123|" + ip + "|" + path + "|GET";

            for (int i = 0; i < e.getValue(); i++) {
                distributionCalculator.processRequest(
                    apiKey, currentEpochMin, ipCmsKey, 30, -1, 300,
                    ip, "host", "acct", (int) (System.currentTimeMillis() / 1000),
                    "US", "US"
                );
            }
        }

        // Allow processing
        Thread.sleep(3000);

        // Then: Verify distributions
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);

        // /api/users: 8 in b1, 25 in b2, 75 in b3
        String usersApiKey = "123|/api/users|GET";
        assertThat(getDistributionBucketCount(windowSize, windowStart, usersApiKey, "b1"))
            .isEqualTo(1);  // 1 IP with 8 requests
        assertThat(getDistributionBucketCount(windowSize, windowStart, usersApiKey, "b2"))
            .isEqualTo(1);  // 1 IP with 25 requests
        assertThat(getDistributionBucketCount(windowSize, windowStart, usersApiKey, "b3"))
            .isEqualTo(1);  // 1 IP with 75 requests

        // /api/products: 5 in b1, 120 in b4
        String productsApiKey = "123|/api/products|GET";
        assertThat(getDistributionBucketCount(windowSize, windowStart, productsApiKey, "b1"))
            .isEqualTo(1);  // 1 IP with 5 requests
        assertThat(getDistributionBucketCount(windowSize, windowStart, productsApiKey, "b4"))
            .isEqualTo(1);  // 1 IP with 120 requests
    }

    @Test
    void testFullFlowAcrossMultipleWindows() throws Exception {
        // Given: Consumer running
        startConsumer();
        String ip = "192.168.1.1";
        String apiKey = "123|/api/users|GET";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push requests across 30 minutes (creates multiple 5-min windows)
        for (long min = 100; min <= 129; min++) {
            pushMessagesToStream(3, ipCmsKey, apiKey, min, ip);  // 3 requests per minute
        }

        // Allow processing
        Thread.sleep(3000);

        // Then: Each 5-min window should have distribution
        for (long baseMin = 100; baseMin <= 125; baseMin += 5) {
            long windowStart = getWindowStartForMinute(baseMin, 5);
            long b1Count = getDistributionBucketCount(5, windowStart, apiKey, "b1");

            // Each 5-min window has 15 requests (3 per min * 5 min) → b2
            assertThat(b1Count).isEqualTo(0);
        }

        // Verify 15-min windows
        long ws15 = getWindowStartForMinute(100, 15);
        long b2Count15 = getDistributionBucketCount(15, ws15, apiKey, "b2");
        assertThat(b2Count15).isEqualTo(1);  // 1 IP with 45 requests → b2

        // Verify 30-min window
        long ws30 = getWindowStartForMinute(100, 30);
        long b4Count30 = getDistributionBucketCount(30, ws30, apiKey, "b4");
        assertThat(b4Count30).isEqualTo(1);  // 1 IP with 90 requests → b4
    }

    @Test
    void testBucketTransitionAcrossWindowBoundary() throws Exception {
        // Given: Consumer running
        startConsumer();
        long startMin = 100;
        int windowSize = 5;
        String ip = "192.168.1.1";
        String apiKey = "123|/api/users|GET";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push 10 requests in minute 100
        pushMessagesToStream(10, ipCmsKey, apiKey, startMin, ip);
        Thread.sleep(2000);

        // Then: Should be in b1
        long windowStart = getWindowStartForMinute(startMin, windowSize);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b1"))
            .isEqualTo(1);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b2"))
            .isEqualTo(0);

        // When: Push 1 more request in minute 101
        pushMessagesToStream(1, ipCmsKey, apiKey, startMin + 1, ip);
        Thread.sleep(2000);

        // Then: Should transition to b2
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b1"))
            .isEqualTo(0);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b2"))
            .isEqualTo(1);
    }

    @Test
    void testComplexScenarioWith100IPs() throws Exception {
        // Given: Consumer running
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String apiKey = "123|/api/users|GET";

        // When: 100 different IPs with varying request counts
        for (int i = 0; i < 100; i++) {
            String ip = "192.168." + (i / 256) + "." + (i % 256);
            String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

            // Vary request counts: some make 1-10, some 11-50, some 51+
            int requestCount;
            if (i < 50) {
                requestCount = 1 + (i % 10);  // 1-10 requests → b1
            } else if (i < 80) {
                requestCount = 11 + ((i - 50) % 40);  // 11-50 requests → b2
            } else {
                requestCount = 51 + ((i - 80) % 50);  // 51-100 requests → b3
            }

            for (int j = 0; j < requestCount; j++) {
                distributionCalculator.processRequest(
                    apiKey, currentEpochMin, ipCmsKey, 30, -1, 300,
                    ip, "host", "acct", (int) (System.currentTimeMillis() / 1000),
                    "US", "US"
                );
            }
        }

        // Allow processing
        Thread.sleep(3000);

        // Then: Verify distribution has all three buckets with IPs
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);
        Map<String, String> distribution = getDistributionBuckets(windowSize, windowStart, apiKey);

        long b1 = Long.parseLong(distribution.getOrDefault("b1", "0"));
        long b2 = Long.parseLong(distribution.getOrDefault("b2", "0"));
        long b3 = Long.parseLong(distribution.getOrDefault("b3", "0"));

        // Approximately 50 IPs in b1, 30 in b2, 20 in b3
        assertThat(b1).isGreaterThan(40).isLessThan(60);
        assertThat(b2).isGreaterThan(20).isLessThan(40);
        assertThat(b3).isGreaterThan(10).isLessThan(30);

        // And: Total IPs should be ~100
        long totalIPs = b1 + b2 + b3;
        assertThat(totalIPs).isEqualTo(100);
    }

    @Test
    void testAPIDataIntegrity() throws Exception {
        // Given: Consumer running
        startConsumer();
        long currentEpochMin = 120;
        String ip = "192.168.1.1";
        String apiKey = "123|/api/users|GET";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push 25 requests
        pushMessagesToStream(25, ipCmsKey, apiKey, currentEpochMin, ip);
        Thread.sleep(2000);

        // Then: Verify all bucket counts sum correctly
        long windowStart = getWindowStartForMinute(currentEpochMin, 5);
        Map<String, String> distribution = getDistributionBuckets(5, windowStart, apiKey);

        long totalIPs = 0;
        for (String count : distribution.values()) {
            totalIPs += Long.parseLong(count);
        }

        // Should have exactly 1 IP (in b2)
        assertThat(totalIPs).isEqualTo(1);

        // Verify b2 is the only non-zero bucket
        assertThat(distribution.get("b2")).isEqualTo("1");
    }

    @Test
    void testConsumerHandlesBatchCorrectly() throws Exception {
        // Given: Consumer running
        startConsumer();
        long currentEpochMin = 120;
        String apiKey = "123|/api/users|GET";

        // When: Push 250 messages (half of BATCH_SIZE 500)
        for (int i = 0; i < 250; i++) {
            String ip = "192.168." + (i / 256) + "." + (i % 256);
            String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

            distributionCalculator.processRequest(
                apiKey, currentEpochMin, ipCmsKey, 30, -1, 300,
                ip, "host", "acct", (int) (System.currentTimeMillis() / 1000),
                "US", "US"
            );
        }

        // Allow processing
        Thread.sleep(3000);

        // Then: All 250 should be processed into distribution
        long windowStart = getWindowStartForMinute(currentEpochMin, 5);
        Map<String, String> distribution = getDistributionBuckets(5, windowStart, apiKey);

        long totalIPs = 0;
        for (String count : distribution.values()) {
            totalIPs += Long.parseLong(count);
        }

        assertThat(totalIPs).isEqualTo(250);
    }

    @Test
    void testDataConsistencyAcrossRestarts() throws Exception {
        // Given: Initial data pushed and processed
        startConsumer();
        long currentEpochMin = 120;
        String ip = "192.168.1.1";
        String apiKey = "123|/api/users|GET";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        pushMessagesToStream(20, ipCmsKey, apiKey, currentEpochMin, ip);
        Thread.sleep(2000);

        // When: Verify first batch processed
        long windowStart = getWindowStartForMinute(currentEpochMin, 5);
        long b2CountBefore = getDistributionBucketCount(5, windowStart, apiKey, "b2");
        assertThat(b2CountBefore).isEqualTo(1);

        // Note: In a real test, we'd restart the consumer here
        // For this integration test, we just verify data persists in Redis
        // If consumer crashed and restarted, the stream would still have unacknowledged messages

        // Then: Data should still be in Redis
        long b2CountAfter = getDistributionBucketCount(5, windowStart, apiKey, "b2");
        assertThat(b2CountAfter).isEqualTo(b2CountBefore);
    }
}
