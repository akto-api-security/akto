package com.akto.threat.detection.tasks;

import com.akto.threat.detection.ip_api_counter.CmsCounterLayer;
import com.akto.threat.detection.ip_api_counter.DistributionCalculator;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DistributionCalculatorTest {

    private DistributionCalculator calculator;
    private String apiKey;

    @Before
    public void setup() {
        calculator = new DistributionCalculator();
        CmsCounterLayer.reset();
        apiKey = "GET:/users";
    }

    @Test
    public void testSingleCallGoesToB1() {
        long baseEpochMin = 20000;
        calculator.updateFrequencyBuckets(apiKey, baseEpochMin);

        assertWindowBucket(apiKey, 5, baseEpochMin, "b1", 1);
        assertWindowBucket(apiKey, 15, baseEpochMin, "b1", 1);
        assertWindowBucket(apiKey, 30, baseEpochMin, "b1", 1);
    }

    @Test
    public void testTwelveCallsGoToB2() {
        long baseEpochMin = 30000;
        for (int i = 0; i < 12; i++) {
            calculator.updateFrequencyBuckets(apiKey, baseEpochMin);
        }

        assertWindowBucket(apiKey, 5, baseEpochMin, "b2", 1);
        assertWindowBucket(apiKey, 15, baseEpochMin, "b2", 1);
        assertWindowBucket(apiKey, 30, baseEpochMin, "b2", 1);
    }

    @Test
    public void testBucketTransitionFromB1ToB2() {
        long baseEpochMin = 40000;

        calculator.updateFrequencyBuckets(apiKey, baseEpochMin); // count = 1 (b1)
        for (int i = 0; i < 11; i++) {
            calculator.updateFrequencyBuckets(apiKey, baseEpochMin); // total = 12 (b2)
        }

        for (int windowSize : new int[]{5, 15, 30}) {
            assertWindowBucket(apiKey, windowSize, baseEpochMin, "b1", 0);
            assertWindowBucket(apiKey, windowSize, baseEpochMin, "b2", 1);
        }
    }

    @Test
    public void testMultipleKeysInSameWindow() {
        long baseEpochMin = 50000;
        String secondKey = "POST:/orders";

        calculator.updateFrequencyBuckets(apiKey, baseEpochMin);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin);

        assertWindowBucket(apiKey, 5, baseEpochMin, "b1", 1);
        assertWindowBucket(secondKey, 5, baseEpochMin, "b1", 1);
    }

    @Test
    public void testMultipleKeysInSameWindow2() {
        long baseEpochMin = 50000;
        String secondKey = "POST:/orders";

        calculator.updateFrequencyBuckets(apiKey, baseEpochMin);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin);

        assertWindowBucket(apiKey, 5, baseEpochMin, "b1", 1);
        assertWindowBucket(secondKey, 5, baseEpochMin, "b1", 1);
    }

    @Test
    public void testRollingWindowBehavior() {
        long t1 = 60000;
        long t2 = 60001;

        calculator.updateFrequencyBuckets(apiKey, t1);
        calculator.updateFrequencyBuckets(apiKey, t2);

        assertWindowBucket(apiKey, 5, t2, "b1", 1);
    }

    @Test
    public void testRollingWindowBehavior2() {
        long t1 = 60000;
        long t2 = 60001;

        calculator.updateFrequencyBuckets(apiKey, t1);
        calculator.updateFrequencyBuckets(apiKey, t2);
        for (int i = 0; i < 10; i++) {
            calculator.updateFrequencyBuckets(apiKey, t2);
        }

        assertWindowBucket(apiKey, 5, t2, "b1", 0);
        assertWindowBucket(apiKey, 5, t2, "b2", 1);
    }

    private void assertWindowBucket(String apiKey, int windowSize, long currentEpochMin, String bucketLabel, int expectedCount) {
        long windowEnd = ((currentEpochMin - 1) / windowSize + 1) * windowSize;
        long windowStart = windowEnd - windowSize + 1;
        Map<String, Integer> bucketDistribution = calculator.getBucketDistributionForApi(apiKey, windowSize, windowStart);
        assertEquals((Integer) expectedCount, bucketDistribution.get(bucketLabel), 
            String.format("WindowSize=%d Start=%d Bucket=%s", windowSize, windowStart, bucketLabel));
    }
}
