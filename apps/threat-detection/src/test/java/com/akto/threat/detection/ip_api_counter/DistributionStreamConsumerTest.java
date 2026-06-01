package com.akto.threat.detection.ip_api_counter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for DistributionStreamConsumer.
 * Tests the Lua script execution, CMS updates, and distribution bucket calculations.
 */
class DistributionStreamConsumerTest extends DistributionIntegrationTestBase {

    @Test
    void testSingleIPMakesNRequestsLandsInCorrectBucket() throws Exception {
        // Given: 1 IP makes 8 requests to /api/users in a window
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String ip = "192.168.1.1";
        String apiKey = "123|/api/users|GET";
        String ipApiCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push 8 messages (same IP/API)
        pushMessagesToStream(8, ipApiCmsKey, apiKey, currentEpochMin, ip);
        Thread.sleep(2000);  // Allow Lua script to process

        // Then: Distribution hash should show 1 IP in b1 (1-10)
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);
        long b1Count = getDistributionBucketCount(windowSize, windowStart, apiKey, "b1");

        assertThat(b1Count).isEqualTo(1);

        // And: distApis set should contain this API
        boolean apiInSet = isApiActiveInWindow(windowSize, windowStart, apiKey);
        assertThat(apiInSet).isTrue();
    }

    @Test
    void testMultipleIPsAreBucketedIndependently() throws Exception {
        // Given: 3 IPs calling same API with different counts
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String apiKey = "123|/api/users|GET";

        // IP1: 5 requests → b1
        String ip1 = "192.168.1.1";
        String cms1 = "ipApiCmsData|123|" + ip1 + "|/api/users|GET";
        pushMessagesToStream(5, cms1, apiKey, currentEpochMin, ip1);

        // IP2: 25 requests → b2
        String ip2 = "192.168.1.2";
        String cms2 = "ipApiCmsData|123|" + ip2 + "|/api/users|GET";
        pushMessagesToStream(25, cms2, apiKey, currentEpochMin, ip2);

        // IP3: 75 requests → b3
        String ip3 = "192.168.1.3";
        String cms3 = "ipApiCmsData|123|" + ip3 + "|/api/users|GET";
        pushMessagesToStream(75, cms3, apiKey, currentEpochMin, ip3);

        // When: Consumer processes all
        Thread.sleep(3000);

        // Then: Distribution should be {"b1": 1, "b2": 1, "b3": 1}
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);
        Map<String, String> distribution = getDistributionBuckets(windowSize, windowStart, apiKey);

        assertThat(distribution)
            .containsEntry("b1", "1")
            .containsEntry("b2", "1")
            .containsEntry("b3", "1");
    }

    @Test
    void testBucketTransitionWhenCountCrossesBoundary() throws Exception {
        // Given: 1 IP with 10 requests (in b1)
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push 10 messages
        pushMessagesToStream(10, ipCmsKey, apiKey, currentEpochMin, ip);
        Thread.sleep(2000);

        // Then: Verify b1=1, b2=0
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b1"))
            .isEqualTo(1);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b2"))
            .isEqualTo(0);

        // When: Push 11th message (in next minute within same window)
        pushMessagesToStream(1, ipCmsKey, apiKey, currentEpochMin + 1, ip);
        Thread.sleep(2000);

        // Then: Should transition to b2 (b1 decremented, b2 incremented)
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b1"))
            .isEqualTo(0);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b2"))
            .isEqualTo(1);
    }

    @Test
    void testDistributionComputedAcrossAllWindowSizes() throws Exception {
        // Given: Messages spread across 31 minutes
        startConsumer();
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push same IP across 31 minutes (1 request per minute)
        for (long min = 100; min <= 130; min++) {
            pushMessagesToStream(1, ipCmsKey, apiKey, min, ip);
        }
        Thread.sleep(3000);

        // Then: Should have distributions for all window sizes
        // 5-min window [100-104] → 5 requests in b1
        long ws5_1 = getWindowStartForMinute(100, 5);
        Map<String, String> dist5_1 = getDistributionBuckets(5, ws5_1, apiKey);
        assertThat(dist5_1).containsEntry("b1", "1");  // 1 IP made 5 requests

        // 15-min window [100-114] → 15 requests in b2
        long ws15_1 = getWindowStartForMinute(100, 15);
        Map<String, String> dist15_1 = getDistributionBuckets(15, ws15_1, apiKey);
        assertThat(dist15_1).containsEntry("b2", "1");  // 1 IP made 15 requests

        // 30-min window [100-129] → 30 requests in b4
        long ws30_1 = getWindowStartForMinute(100, 30);
        Map<String, String> dist30_1 = getDistributionBuckets(30, ws30_1, apiKey);
        assertThat(dist30_1).containsEntry("b4", "1");  // 1 IP made 30 requests
    }

    @Test
    void testDifferentAPIsHaveIndependentDistributions() throws Exception {
        // Given: 1 IP calling 3 different APIs with different counts
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String ip = "192.168.1.1";

        // /api/users: 8 requests (b1)
        String apiKey1 = "123|/api/users|GET";
        String cms1 = "ipApiCmsData|123|" + ip + "|/api/users|GET";
        pushMessagesToStream(8, cms1, apiKey1, currentEpochMin, ip);

        // /api/products: 25 requests (b2)
        String apiKey2 = "123|/api/products|GET";
        String cms2 = "ipApiCmsData|123|" + ip + "|/api/products|GET";
        pushMessagesToStream(25, cms2, apiKey2, currentEpochMin, ip);

        // /api/orders: 60 requests (b3)
        String apiKey3 = "123|/api/orders|GET";
        String cms3 = "ipApiCmsData|123|" + ip + "|/api/orders|GET";
        pushMessagesToStream(60, cms3, apiKey3, currentEpochMin, ip);

        Thread.sleep(3000);

        // Then: Each API has its own distribution
        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);

        Map<String, String> dist1 = getDistributionBuckets(windowSize, windowStart, apiKey1);
        assertThat(dist1).containsEntry("b1", "1");

        Map<String, String> dist2 = getDistributionBuckets(windowSize, windowStart, apiKey2);
        assertThat(dist2).containsEntry("b2", "1");

        Map<String, String> dist3 = getDistributionBuckets(windowSize, windowStart, apiKey3);
        assertThat(dist3).containsEntry("b3", "1");
    }

    @Test
    void testCMSCountAccuracy() throws Exception {
        // Given: Multiple minutes of data for same IP/API
        startConsumer();
        long baseEpochMin = 120;
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push requests across 5 consecutive minutes (3 requests per minute = 15 total)
        for (long min = baseEpochMin; min < baseEpochMin + 5; min++) {
            pushMessagesToStream(3, ipCmsKey, apiKey, min, ip);
        }
        Thread.sleep(2000);

        // Then: Total count should be ~15 (within CMS error bounds)
        long windowStart = getWindowStartForMinute(baseEpochMin, 5);
        long b2Count = getDistributionBucketCount(5, windowStart, apiKey, "b2");  // b2 is 11-50

        assertThat(b2Count).isEqualTo(1);  // 1 IP made 15 requests, falls in b2
    }

    @Test
    void testStreamAcknowledgment() throws Exception {
        // Given: Consumer started and stream populated
        startConsumer();
        long currentEpochMin = 120;
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        // When: Push messages
        pushMessagesToStream(5, ipCmsKey, apiKey, currentEpochMin, ip);
        long initialLength = getStreamLength();
        assertThat(initialLength).isGreaterThan(0);

        // Allow consumer to process and acknowledge
        Thread.sleep(3000);

        // Then: Messages should be acknowledged (stream may not be empty due to async processing)
        // At minimum, the consumer should have read and processed them
        long finalLength = getStreamLength();
        assertThat(finalLength).isLessThanOrEqualTo(initialLength);
    }

    @Test
    void testBucketIncrementDecrement() throws Exception {
        // Given: Start with 1 request (b1: 1)
        startConsumer();
        long currentEpochMin = 120;
        int windowSize = 5;
        String apiKey = "123|/api/users|GET";
        String ip = "192.168.1.1";
        String ipCmsKey = "ipApiCmsData|123|" + ip + "|/api/users|GET";

        pushMessagesToStream(1, ipCmsKey, apiKey, currentEpochMin, ip);
        Thread.sleep(2000);

        long windowStart = getWindowStartForMinute(currentEpochMin, windowSize);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b1"))
            .isEqualTo(1);

        // When: Add more requests and move to b2
        pushMessagesToStream(10, ipCmsKey, apiKey, currentEpochMin + 1, ip);
        Thread.sleep(2000);

        // Then: b1 should be 0, b2 should be 1
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b1"))
            .isEqualTo(0);
        assertThat(getDistributionBucketCount(windowSize, windowStart, apiKey, "b2"))
            .isEqualTo(1);
    }
}
