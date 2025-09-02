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
    private String cmsKey;

    @Before
    public void setup() {
        calculator = new DistributionCalculator();
        CmsCounterLayer.reset();
        apiKey = "11111|GET|/users";
        cmsKey = "11111|1.1.1.1|GET|/users";
    }

    @Test
    public void testSingleCallGoesToB1() {
        long baseEpochMin = 20000;
        calculator.updateFrequencyBuckets(apiKey, baseEpochMin, cmsKey);

        assertWindowBucket(apiKey, 5, baseEpochMin, "b1", 1);
        assertWindowBucket(apiKey, 15, baseEpochMin, "b1", 1);
        assertWindowBucket(apiKey, 30, baseEpochMin, "b1", 1);
    }

    @Test
    public void testTwelveCallsGoToB2() {
        long baseEpochMin = 30000;
        for (int i = 0; i < 12; i++) {
            calculator.updateFrequencyBuckets(apiKey, baseEpochMin, cmsKey);
        }

        assertWindowBucket(apiKey, 5, baseEpochMin, "b2", 1);
        assertWindowBucket(apiKey, 15, baseEpochMin, "b2", 1);
        assertWindowBucket(apiKey, 30, baseEpochMin, "b2", 1);
    }

    @Test
    public void testBucketTransitionFromB1ToB2() {
        long baseEpochMin = 40000;

        calculator.updateFrequencyBuckets(apiKey, baseEpochMin, cmsKey); // count = 1 (b1)
        for (int i = 0; i < 11; i++) {
            calculator.updateFrequencyBuckets(apiKey, baseEpochMin, cmsKey); // total = 12 (b2)
        }

        for (int windowSize : new int[]{5, 15, 30}) {
            assertWindowBucket(apiKey, windowSize, baseEpochMin, "b1", 0);
            assertWindowBucket(apiKey, windowSize, baseEpochMin, "b2", 1);
        }
    }

    @Test
    public void testMultipleKeysInSameWindow() {
        long baseEpochMin = 50000;
        String secondKey = "11111|POST|/orders";
        String secondCmsKey = "11111|2.2.2.2|POST|/orders";

        calculator.updateFrequencyBuckets(apiKey, baseEpochMin, cmsKey);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin, secondCmsKey);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin, secondCmsKey);

        assertWindowBucket(apiKey, 5, baseEpochMin, "b1", 1);
        assertWindowBucket(secondKey, 5, baseEpochMin, "b1", 1);
    }

    @Test
    public void testMultipleKeysInSameWindow2() {
        long baseEpochMin = 50000;
        String secondKey = "11111|POST|/orders";
        String secondCmsKey = "11111|2.2.2.2|POST|/orders";

        calculator.updateFrequencyBuckets(apiKey, baseEpochMin, cmsKey);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin, secondCmsKey);
        calculator.updateFrequencyBuckets(secondKey, baseEpochMin, secondCmsKey);

        assertWindowBucket(apiKey, 5, baseEpochMin, "b1", 1);
        assertWindowBucket(secondKey, 5, baseEpochMin, "b1", 1);
    }

    @Test
    public void testRollingWindowBehavior() {
        long t1 = 60000;
        long t2 = 60001;

        calculator.updateFrequencyBuckets(apiKey, t1, cmsKey);
        calculator.updateFrequencyBuckets(apiKey, t2, cmsKey);

        assertWindowBucket(apiKey, 5, t2, "b1", 1);
    }

    @Test
    public void testRollingWindowBehavior2() {
        long t1 = 60000;
        long t2 = 60001;

        calculator.updateFrequencyBuckets(apiKey, t1, cmsKey);
        calculator.updateFrequencyBuckets(apiKey, t2, cmsKey);
        for (int i = 0; i < 10; i++) {
            calculator.updateFrequencyBuckets(apiKey, t2, cmsKey);
        }

        assertWindowBucket(apiKey, 5, t2, "b1", 0);
        assertWindowBucket(apiKey, 5, t2, "b2", 1);
    }

    // Helper method to convert time string to epoch minutes
    private long timeToEpochMin(String dateTimeStr) {
        // Parse ISO format like "2025-09-02T12:02" and convert to epoch minutes
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(dateTimeStr);
        long epochSeconds = dateTime.toEpochSecond(java.time.ZoneOffset.UTC);
        return epochSeconds / 60;
    }
    
    @Test
    public void testGetWindowEnd() {
        String baseDate = "2025-09-02T";

        // currentEpochMin = 12:01, windowSize = 5, return 12:05
        long time_12_01 = timeToEpochMin(baseDate + "12:01:00");
        long time_12_05 = timeToEpochMin(baseDate + "12:05:00");
        assertEquals(time_12_05, DistributionCalculator.getWindowEnd(time_12_01, 5), 
            "12:01 should round up to 12:05");
        
        // currentEpochMin = 12:02, windowSize = 5, return 12:05
        long time_12_02 = timeToEpochMin(baseDate + "12:02:00");
        assertEquals(time_12_05, DistributionCalculator.getWindowEnd(time_12_02, 5), 
            "12:02 should round up to 12:05");
        
        // currentEpochMin = 12:03, windowSize = 5, return 12:05
        long time_12_03 = timeToEpochMin(baseDate + "12:03:00");
        assertEquals(time_12_05, DistributionCalculator.getWindowEnd(time_12_03, 5),
            "12:03 should round up to 12:05");
        
        // currentEpochMin = 12:04, windowSize = 5, return 12:05
        long time_12_04 = timeToEpochMin(baseDate + "12:04:00");
        assertEquals(time_12_05, DistributionCalculator.getWindowEnd(time_12_04, 5),
            "12:04 should round up to 12:05");
        
        // currentEpochMin = 12:05, windowSize = 5, return 12:05
        assertEquals(time_12_05, DistributionCalculator.getWindowEnd(time_12_05, 5),
            "12:05 should stay at 12:05");
        
        // currentEpochMin = 12:06, windowSize = 5, return 12:10
        long time_12_06 = timeToEpochMin(baseDate + "12:06:00");
        long time_12_10 = timeToEpochMin(baseDate + "12:10:00");
        assertEquals(time_12_10, DistributionCalculator.getWindowEnd(time_12_06, 5),
            "12:06 should round up to 12:10");
        
        // currentEpochMin = 12:07, windowSize = 5, return 12:10
        long time_12_07 = timeToEpochMin(baseDate + "12:07:00");
        assertEquals(time_12_10, DistributionCalculator.getWindowEnd(time_12_07, 5),
            "12:07 should round up to 12:10");
    }
    
    @Test
    public void testGetWindowStart() {
        String baseDate = "2025-09-02T";
        
        // windowEnd = 12:05, windowSize = 5, return 12:01
        long time_12_05 = timeToEpochMin(baseDate + "12:05:00");
        long time_12_01 = timeToEpochMin(baseDate + "12:01:00");
        assertEquals(time_12_01, DistributionCalculator.getWindowStart(time_12_05, 5),
            "Window ending at 12:05 should start at 12:01");

        // windowEnd = 12:10, windowSize = 5, return 12:06
        long time_12_10 = timeToEpochMin(baseDate + "12:10:00");
        long time_12_06 = timeToEpochMin(baseDate + "12:06:00");
        assertEquals(time_12_06, DistributionCalculator.getWindowStart(time_12_10, 5),
            "Window ending at 12:10 should start at 12:06");
        
        // Additional test cases for different window sizes
        // 15-minute window ending at 12:15 should start at 12:01
        long time_12_15 = timeToEpochMin(baseDate + "12:15:00");
        assertEquals(time_12_01, DistributionCalculator.getWindowStart(time_12_15, 15),
            "15-min window ending at 12:15 should start at 12:01");
        
        // 30-minute window ending at 12:30 should start at 12:01
        long time_12_30 = timeToEpochMin(baseDate + "12:30:00");
        assertEquals(time_12_01, DistributionCalculator.getWindowStart(time_12_30, 30),
            "30-min window ending at 12:30 should start at 12:01");
    }
    
    @Test
    public void testWindowBoundariesConsistency() {
        // Test that windows are continuous without gaps
        String baseDate = "2025-09-02T";
        long time_12_03 = timeToEpochMin(baseDate + "12:03:00");
        long time_12_06 = timeToEpochMin(baseDate + "12:06:00");
        
        int windowSize = 5;
        
        long window1End = DistributionCalculator.getWindowEnd(time_12_03, windowSize);
        long window1Start = DistributionCalculator.getWindowStart(window1End, windowSize);
        
        long window2End = DistributionCalculator.getWindowEnd(time_12_06, windowSize);
        long window2Start = DistributionCalculator.getWindowStart(window2End, windowSize);
        
        // Window 1: [12:01-12:05], Window 2: [12:06-12:10]
        assertEquals(timeToEpochMin(baseDate + "12:05:00"), window1End, "First window should end at 12:05");
        assertEquals(timeToEpochMin(baseDate + "12:01:00"), window1Start, "First window should start at 12:01");
        assertEquals(timeToEpochMin(baseDate + "12:10:00"), window2End, "Second window should end at 12:10");
        assertEquals(timeToEpochMin(baseDate + "12:06:00"), window2Start, "Second window should start at 12:06");
        
        // Windows should be adjacent (no gap, no overlap)
        assertEquals(window1End + 1, window2Start, 
            "Second window should start immediately after first window ends");
    }

    private void assertWindowBucket(String apiKey, int windowSize, long currentEpochMin, String bucketLabel, int expectedCount) {
        long windowEnd = ((currentEpochMin - 1) / windowSize + 1) * windowSize;
        long windowStart = windowEnd - windowSize + 1;
        Map<String, Integer> bucketDistribution = calculator.getBucketDistributionForApi(apiKey, windowSize, windowStart);
        assertEquals((Integer) expectedCount, bucketDistribution.get(bucketLabel), 
            String.format("WindowSize=%d Start=%d Bucket=%s", windowSize, windowStart, bucketLabel));
    }
}
