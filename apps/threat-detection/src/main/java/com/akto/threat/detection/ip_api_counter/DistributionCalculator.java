package com.akto.threat.detection.ip_api_counter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.akto.utils.ThreatApiDistributionUtils;

public class DistributionCalculator {
    /**
     * frequencyBuckets example structure:
     * {
     *   "5|1625254800": { // 5-minute window starting at epoch 1625254800
     *     "123:/api/v1/resource:GET": { // API key
     *       "b1": 5, // 5 users called this API in bucket b1 (1-10 requests)
     *       "b2": 2  // 2 users called this API in bucket b2 (11-50 requests)
     *     }
     *   }
     * }
     */

    private static Map<String, Map<String, Map<String, Integer>>> frequencyBuckets;
    private static CmsCounterLayer cmsCounterLayer;

    public DistributionCalculator() {
        frequencyBuckets = new ConcurrentHashMap<>();
        cmsCounterLayer = CmsCounterLayer.getInstance();
    }

  
    /**
     * Get the end of the window for a given current epoch minute and window size.
     * Example:
     * currentEpochMin = 12:01,12:02,12:03,12:04,12:05, windowSize = 5, return 12:05
     * currentEpochMin = 12:06,12:07,12:08,12:09,12:10, windowSize = 5, return 12:10
     */
    public static long getWindowEnd(long currentEpochMin, int windowSize) {
        return ((currentEpochMin - 1) / windowSize + 1) * windowSize;
    }

    /**
     * Get the start of the window for a given window end and window size.
     * Example:
     * windowEnd = 12:05, windowSize = 5, return 12:01
     * windowEnd = 12:10, windowSize = 5, return 12:06
     */
    public static long getWindowStart(long windowEnd, int windowSize) {
        return windowEnd - windowSize + 1;
    }

    public void updateFrequencyBuckets(String apiKey, long currentEpochMin, String ipApiCmsKey) {
        // Increment CMS for current minute
        cmsCounterLayer.increment(ipApiCmsKey, String.valueOf(currentEpochMin));

        for (int windowSize : Arrays.asList(5, 15, 30)) {

            // Tumbling windows of 5, 15, and 30 minutes
            // Windows: [12:01-12:05], [12:06-12:10], [12:11-12:15], etc.
            long windowEnd = getWindowEnd(currentEpochMin, windowSize);
            long windowStart = getWindowStart(windowEnd, windowSize);
            String compositeKey = windowSize + "|" + windowStart;

            // Example: Requests times: 12:01, 12:02, 12:03 -> Window: [12:01-12:05] -> Count: 3
            long count = getCountInRange(ipApiCmsKey, windowStart, windowEnd);
            String newBucket = getBucketLabel(count);
            String oldBucket = getBucketLabel(count - 1);

            frequencyBuckets.putIfAbsent(compositeKey, new ConcurrentHashMap<>());
            Map<String, Map<String, Integer>> apiMap = frequencyBuckets.get(compositeKey);

            apiMap.putIfAbsent(apiKey, new ConcurrentHashMap<>());
            Map<String, Integer> bucketMap = apiMap.get(apiKey);

            // Initialize all bucket labels to 0 if not present
            for (ThreatApiDistributionUtils.Range r : ThreatApiDistributionUtils.getBucketRanges()) {
                bucketMap.putIfAbsent(r.label, 0);
            }

            if (count == 1) {
                bucketMap.put(newBucket, bucketMap.get(newBucket) + 1);
            } else if (!Objects.equals(oldBucket, newBucket)) {
                bucketMap.put(oldBucket, bucketMap.getOrDefault(oldBucket, 0) - 1);
                bucketMap.put(newBucket, bucketMap.getOrDefault(newBucket, 0) + 1);
            }
        }
    }

    /**
     *   TUMBLING (Fixed blocks):
     *   [12:01-12:05] [12:06-12:10] [12:11-12:15]
     *   ↑             ↑             ↑
     *   All requests   All requests  All requests
     *   at 12:03       at 12:08      at 12:13
     *   check here     check here    check here
     */
    private long getCountInRange(String ipApiCmsKey, long startEpochMin, long endEpochMin) {
        long sum = 0;
        for (long ts = startEpochMin; ts <= endEpochMin; ts++) {
            sum += cmsCounterLayer.estimateCount(ipApiCmsKey, String.valueOf(ts));
        }
        return sum;
    }

    /**
     *  SLIDING (Moving window):
         [11:59-12:03] at 12:03
          [12:00-12:04] at 12:04
           [12:01-12:05] at 12:05
            [12:02-12:06] at 12:06
             ↑
          Window moves with current time
     */
    public long getSlidingWindowCount(String ipApiCmsKey, long currentEpochMin, int windowSize) {
        long endEpochMin = currentEpochMin;
        long startEpochMin = currentEpochMin - windowSize + 1;
        long sum = 0;
        for (long ts = startEpochMin; ts <= endEpochMin; ts++) {
            sum += cmsCounterLayer.estimateCount(ipApiCmsKey, String.valueOf(ts));
        }
        return sum;
    }

    private String getBucketLabel(long count) {
        for (ThreatApiDistributionUtils.Range r : ThreatApiDistributionUtils.getBucketRanges()) {
            if (count >= r.min && count < r.max) return r.label;
        }
        return "b1"; // fallback
    }

    public Map<String, Integer> getBucketDistributionForApi(String apiKey, int windowSize, long windowStart) {
        String windowKey = windowSize + "|" + windowStart;
        Map<String, Map<String, Integer>> apiMap = frequencyBuckets.get(windowKey);
        if (apiMap != null) {
            return apiMap.getOrDefault(apiKey, new HashMap<>());
        }
        return new HashMap<>();
    }

    public Map<String, Map<String, Integer>> getBucketDistribution(int windowSize, long windowStart) {
        String windowKey = windowSize + "|" + windowStart;
        return frequencyBuckets.get(windowKey);
    }
}