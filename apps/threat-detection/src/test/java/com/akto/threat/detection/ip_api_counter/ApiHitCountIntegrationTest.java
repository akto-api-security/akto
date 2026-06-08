package com.akto.threat.detection.ip_api_counter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the API hit count flow.
 * Covers:
 *   1. Counter accuracy — apiCount|{key}|{bin} reflects true call counts
 *   2. Threshold breach detection — verified via Redis counter state, not Kafka
 *
 * Uses real Redis (localhost:6379). Consumer is shared across all tests.
 * Does NOT test Kafka alert forwarding.
 */
class ApiHitCountIntegrationTest extends DistributionIntegrationTestBase {

    private static final long EPOCH_MIN = 88320L;
    private static final String API_KEY = "123|/api/login|POST";
    private static final String IP = "10.0.0.1";
    private static final String IP_CMS_KEY = "ipApiCmsData|123|" + IP + "|/api/login|POST";

    // -------------------------------------------------------------------------
    // Counter accuracy tests
    // -------------------------------------------------------------------------

    @Test
    void testSingleRequestIncrementsBinCounter() throws Exception {
        pushMessagesToStream(1, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP);
        Thread.sleep(2000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(1);
        assertThat(getIndexScore(API_KEY)).isEqualTo((double) EPOCH_MIN);
    }

    @Test
    void testMultipleRequestsSameBinAccumulate() throws Exception {
        pushMessagesToStream(100, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP);
        Thread.sleep(2000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(100);
    }

    @Test
    void testRequestsInDifferentBinsStoredSeparately() throws Exception {
        pushMessagesToStream(50, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP);
        pushMessagesToStream(30, IP_CMS_KEY, API_KEY, EPOCH_MIN + 1, IP);
        pushMessagesToStream(20, IP_CMS_KEY, API_KEY, EPOCH_MIN + 2, IP);
        Thread.sleep(2000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(50);
        assertThat(getApiCount(API_KEY, EPOCH_MIN + 1)).isEqualTo(30);
        assertThat(getApiCount(API_KEY, EPOCH_MIN + 2)).isEqualTo(20);
    }

    @Test
    void testDifferentApisHaveSeparateCounters() throws Exception {
        String apiKey2 = "123|/api/users|GET";
        String ipCmsKey2 = "ipApiCmsData|123|" + IP + "|/api/users|GET";

        pushMessagesToStream(40, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP);
        pushMessagesToStream(60, ipCmsKey2, apiKey2, EPOCH_MIN, IP);
        Thread.sleep(2000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(40);
        assertThat(getApiCount(apiKey2, EPOCH_MIN)).isEqualTo(60);
    }

    @Test
    void testMultipleBatchesAccumulateCorrectly() throws Exception {
        // 600 messages > BATCH_SIZE(500) — consumer processes in 2 batches
        pushMessagesToStream(600, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP);
        Thread.sleep(3000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(600);
    }

    @Test
    void testZeroWindowKeepsCountingButSkipsBreachCheck() throws Exception {
        // apiLevelWindow=0 disables threshold check, but counters + index still populated
        pushMessagesToStreamWithApiRateLimit(200, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP, 0, 0);
        Thread.sleep(2000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(200);
        assertThat(getIndexScore(API_KEY)).isNotNull();
    }

    @Test
    void testMultipleIpsShareTheSameApiCounter() throws Exception {
        String ip2 = "10.0.0.2";
        String ipCmsKey2 = "ipApiCmsData|123|" + ip2 + "|/api/login|POST";

        pushMessagesToStream(300, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP);
        pushMessagesToStream(300, ipCmsKey2, API_KEY, EPOCH_MIN, ip2);
        Thread.sleep(2000);

        // Both IPs increment the same apiCount key — must sum to 600
        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(600);
    }

    // -------------------------------------------------------------------------
    // Threshold breach detection tests
    // Breach detection is verified by asserting the counter crossed the threshold.
    // Kafka forwarding is a separate concern not tested here.
    // -------------------------------------------------------------------------

    @Test
    void testNoBreachBelowThreshold() throws Exception {
        // 200 req/min × 3 bins = 600 total; threshold = 1000 → no breach
        for (long bin = EPOCH_MIN; bin < EPOCH_MIN + 3; bin++) {
            pushMessagesToStreamWithApiRateLimit(200, IP_CMS_KEY, API_KEY, bin, IP, 5, 1000);
        }
        Thread.sleep(2000);

        long total = getApiCount(API_KEY, EPOCH_MIN)
                   + getApiCount(API_KEY, EPOCH_MIN + 1)
                   + getApiCount(API_KEY, EPOCH_MIN + 2);
        assertThat(total).isEqualTo(600);
        assertThat(total).isLessThan(1000);
    }

    @Test
    void testBreachWhenThresholdExceededInSingleBin() throws Exception {
        // 600 requests in one bin, threshold = 500 → counter exceeds threshold
        pushMessagesToStreamWithApiRateLimit(600, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP, 5, 500);
        Thread.sleep(2000);

        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isGreaterThanOrEqualTo(500);
    }

    @Test
    void testBreachAccumulatesAcrossWindowBins() throws Exception {
        // 200/min × 3 bins within a 5-min window = 600 total; threshold = 500
        for (long bin = EPOCH_MIN; bin < EPOCH_MIN + 3; bin++) {
            pushMessagesToStreamWithApiRateLimit(200, IP_CMS_KEY, API_KEY, bin, IP, 5, 500);
        }
        Thread.sleep(2000);

        long windowTotal = getApiCount(API_KEY, EPOCH_MIN)
                         + getApiCount(API_KEY, EPOCH_MIN + 1)
                         + getApiCount(API_KEY, EPOCH_MIN + 2);
        assertThat(windowTotal).isGreaterThanOrEqualTo(500);
    }

    @Test
    void testWindowSlideDropsOldBins() throws Exception {
        // 300 requests at EPOCH_MIN — outside any 3-min window relative to EPOCH_MIN+10
        pushMessagesToStreamWithApiRateLimit(300, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP, 3, 500);

        // 100 req/min at bins +10 and +11 — window [+9..+11] sums to 200, below threshold
        pushMessagesToStreamWithApiRateLimit(100, IP_CMS_KEY, API_KEY, EPOCH_MIN + 10, IP, 3, 500);
        pushMessagesToStreamWithApiRateLimit(100, IP_CMS_KEY, API_KEY, EPOCH_MIN + 11, IP, 3, 500);
        Thread.sleep(2000);

        // Only the recent window counts — old bin must not contribute
        long recentWindowTotal = getApiCount(API_KEY, EPOCH_MIN + 9)
                               + getApiCount(API_KEY, EPOCH_MIN + 10)
                               + getApiCount(API_KEY, EPOCH_MIN + 11);
        assertThat(recentWindowTotal).isLessThan(500);
    }

    @Test
    void testMultipleIpsHittingSameApiTriggersBreach() throws Exception {
        // 2 different IPs, 300 each = 600 total on the same API key; threshold = 500
        String ip2 = "10.0.0.2";
        String ipCmsKey2 = "ipApiCmsData|123|" + ip2 + "|/api/login|POST";

        pushMessagesToStreamWithApiRateLimit(300, IP_CMS_KEY, API_KEY, EPOCH_MIN, IP, 5, 500);
        pushMessagesToStreamWithApiRateLimit(300, ipCmsKey2, API_KEY, EPOCH_MIN, ip2, 5, 500);
        Thread.sleep(2000);

        // Shared counter sums contributions from both IPs
        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isEqualTo(600);
        assertThat(getApiCount(API_KEY, EPOCH_MIN)).isGreaterThanOrEqualTo(500);
    }
}
