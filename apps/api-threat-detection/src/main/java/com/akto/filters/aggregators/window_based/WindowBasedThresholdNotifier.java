package com.akto.filters.aggregators.window_based;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.cache.CounterCache;
import com.akto.dto.HttpResponseParams;

public class WindowBasedThresholdNotifier {

    private final Config config;

    // We can use an in-memory cache for this, since we dont mind being notified
    // more than once by multiple instances of the service.
    // But on 1 instance, we should not notify more than once in the cooldown
    // period.
    private final ConcurrentMap<String, Long> notifiedMap;

    public static class Config {
        private final int threshold;
        private final int windowSizeInMinutes;
        private int notificationCooldownInSeconds = 60 * 30; // 30 mins

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
        this.notifiedMap = new ConcurrentHashMap<>();
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

        boolean thresholdBreached = windowCount >= this.config.getThreshold();

        long now = System.currentTimeMillis() / 1000L;
        long lastNotified = this.notifiedMap.getOrDefault(bucketKey, 0L);

        boolean cooldownBreached = (now - lastNotified) >= this.config.notificationCooldownInSeconds;

        if (thresholdBreached && cooldownBreached) {
            this.notifiedMap.put(bucketKey, now);
            return true;
        }

        return false;
    }
}
