package com.akto.filters.aggregators.window_based;

import com.akto.cache.TypeValueCache;
import com.akto.dto.HttpResponseParams;
import com.akto.filters.aggregators.RealTimeThresholdNotifier;

import java.util.List;

public class WindowBasedThresholdNotifier extends RealTimeThresholdNotifier {

    private final Config config;

    public static class Config {
        private final int threshold;
        private final int windowInSeconds;

        public Config(int threshold, int windowInSeconds) {
            this.threshold = threshold;
            this.windowInSeconds = windowInSeconds;
        }

        public int getThreshold() {
            return threshold;
        }

        public int getWindowInSeconds() {
            return windowInSeconds;
        }
    }

    private final TypeValueCache<Data> cache;

    public WindowBasedThresholdNotifier(TypeValueCache<Data> cache, Config config) {
        this.cache = cache;
        this.config = config;
    }

    @Override
    public boolean shouldNotify(String aggKey, HttpResponseParams responseParam) {
        long now = System.currentTimeMillis();

        Data data = this.cache.getOrDefault(aggKey, new Data());
        List<Data.Request> requests = data.getRequests();
        requests.add(new Data.Request(responseParam.getTime() * 1000L));

        // Check if the current request is in the same window as previous requests
        boolean sameWindow = now - requests.get(0).getReceivedAt() <= this.config.getWindowInSeconds() * 1000L;

        // Qualify request for notification pending last notified verification
        boolean thresholdCrossedForWindow = false;

        if (!sameWindow) {
            // Remove all the requests that are outside the window
            while (!requests.isEmpty()
                    && now - requests.get(0).getReceivedAt() > this.config.getWindowInSeconds() * 1000L) {
                requests.remove(0);
            }
        } else {
            thresholdCrossedForWindow = requests.size() >= this.config.getThreshold();

            // This is to ensure that we don't keep on adding requests to the list
            // Eg: 10k requests in 1 second qualify. So we keep only last N requests
            // where N is the threshold
            while (requests.size() > this.config.getThreshold()) {
                requests.remove(0);
            }
        }

        boolean shouldNotify = thresholdCrossedForWindow;
        // Note: This also has a dependency on the cache expiry. If cache expiry is less
        // than notification cooldown, then this will not work as expected.
        // Eg: If cache expiry is 1 minute and notification cooldown is 1 hour, then
        // this will always notify.
        if (thresholdCrossedForWindow) {
            if (now - data.getLastNotifiedAt() >= NOTIFICATION_COOLDOWN_MINUTES * 60 * 1000L) {
                data.setLastNotifiedAt(now);
            } else {
                shouldNotify = false;
            }
        }

        data.setRequests(requests);
        this.cache.put(aggKey, data);
        return shouldNotify;
    }
}
