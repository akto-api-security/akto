package com.akto.filters.aggregators.window_based;

import com.akto.cache.CounterCache;
import com.akto.dto.HttpResponseParams;
import com.akto.filters.aggregators.RealTimeThresholdNotifier;

public class WindowBasedThresholdNotifier extends RealTimeThresholdNotifier {

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

    @Override
    public boolean shouldNotify(String actor, HttpResponseParams responseParam) {
        int requestTimeSeconds = responseParam.getTime();

        int minuteOfYear = (int) Math.ceil(requestTimeSeconds / (60L));

        String bucketKey = actor + "|" + minuteOfYear;
        this.cache.increment(bucketKey);

        long windowCount = 0L;
        for (int i = minuteOfYear; i >= minuteOfYear - this.config.getWindowSizeInMinutes(); i--) {
            windowCount += this.cache.get(actor + "|" + i);
        }

        return windowCount >= this.config.getThreshold();
    }
}
