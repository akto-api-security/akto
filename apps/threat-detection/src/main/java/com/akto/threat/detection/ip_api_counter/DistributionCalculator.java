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

    public void updateFrequencyBuckets(String apiKey, long currentEpochMin, String cmsKey) {
        // Increment CMS for current minute
        cmsCounterLayer.increment(cmsKey, String.valueOf(currentEpochMin));

        for (int windowSize : Arrays.asList(5, 15, 30)) {
            // Tumbling windows of 5, 15, and 30 minutes
            // 12:00, 12:05, 12:10, etc.
            // Requests coming in at 12:02, 12:03 will be counted in the 12:00 window
            long windowEnd = ((currentEpochMin - 1) / windowSize + 1) * windowSize;
            long windowStart = windowEnd - windowSize + 1;
            String compositeKey = windowSize + "|" + windowStart;

            long count = getCountInRange(cmsKey, windowStart, windowEnd);
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
     * Get the total count of IP|API calls made in the specified time range.
     * Example Output: Count of IP|API calls in 5 minutes.
     */
    private long getCountInRange(String key, long startEpochMin, long endEpochMin) {
        long sum = 0;
        for (long ts = startEpochMin; ts <= endEpochMin; ts++) {
            sum += cmsCounterLayer.estimateCount(key, String.valueOf(ts));
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