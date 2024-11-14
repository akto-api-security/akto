package com.akto.filters.aggregators.window_based;

import com.akto.cache.CounterCache;
import com.akto.dto.HttpResponseParams;

public class WindowBasedThresholdNotifier {

    private final Config config;

    public static class Config {
        private final int threshold;
        private final int windowSizeInMinutes;

        public Config(int threshold, int windowInSeconds) {
            this.threshold = threshold;
            this.windowSizeInMinutes = windowInSeconds;
        }

        public int getThreshold() {
            return threshold;
        }

        public int getWindowSizeInMinutes() {
            return windowSizeInMinutes;
        }
    }

    private final CounterCache cache;

    public WindowBasedThresholdNotifier(CounterCache cache, Config config) {
        this.cache = cache;
        this.config = config;
    }

    private static String getBucketKey(String actor, String groupKey, int minuteOfYear) {
        return groupKey + "|" + actor + "|" + minuteOfYear;
    }

    public boolean shouldNotify(String groupKey, String actor, HttpResponseParams responseParam) {
        int requestTimeSeconds = responseParam.getTime();

        int minuteOfYear = requestTimeSeconds / 60;

        String bucketKey = getBucketKey(groupKey, actor, minuteOfYear);
        this.cache.increment(bucketKey);

        long windowCount = 0L;
        for (int i = minuteOfYear; i >= minuteOfYear - this.config.getWindowSizeInMinutes(); i--) {
            windowCount += this.cache.get(getBucketKey(groupKey, actor, minuteOfYear));
        }

        return windowCount >= this.config.getThreshold();
    }
}
