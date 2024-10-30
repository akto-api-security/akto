package com.akto.filters.aggregators.notifier.window_based;

import com.akto.cache.TypeValueCache;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.filters.aggregators.key_generator.SourceIPKeyGenerator;
import com.akto.filters.aggregators.notifier.RealTimeThresholdNotifier;

import java.util.List;
import java.util.Optional;

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
    public Optional<String> generateKey(HttpResponseParams responseParams) {
        return new SourceIPKeyGenerator().generate(responseParams);
    }

    @Override
    public boolean qualifyForNotification(HttpResponseParams responseParam) {
        // For now, we only care about 4xx errors
        // Later can be made configurable
        return responseParam.statusCode >= 400 && responseParam.statusCode < 500;
    }

    @Override
    public boolean shouldNotify(HttpResponseParams responseParam) {
        if (!this.qualifyForNotification(responseParam)) {
            return false;
        }

        Optional<String> aggregateKey = this.generateKey(responseParam);
        if (!aggregateKey.isPresent()) {
            return false;
        }

        String aggKey = aggregateKey.get();

        long now = System.currentTimeMillis();

        Data data = this.cache.getOrDefault(aggKey, new Data());
        List<Data.Request> requests = data.getRequests();
        HttpRequestParams requestParams = responseParam.getRequestParams();
        requests.add(new Data.Request(responseParam.getTime() * 1000L, requestParams.getURL()));

        // Check if the current request is in the same window as previous requests
        boolean sameWindow =
                now - requests.get(0).getReceivedAt() <= this.config.getWindowInSeconds() * 1000L;

        // Qualify request for notification pending last notified verification
        boolean thresholdCrossedForWindow = false;

        if (!sameWindow) {
            // Remove all the requests that are outside the window
            while (!requests.isEmpty()
                    && now - requests.get(0).getReceivedAt()
                            > this.config.getWindowInSeconds() * 1000L) {
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
        if (thresholdCrossedForWindow) {
            if (now - data.getLastNotifiedAt() >= this.notificationCooldown() * 1000L) {
                data.setLastNotifiedAt(now);
            } else {
                shouldNotify = false;
            }
        }

        data.setRequests(requests);
        this.cache.put(aggKey, data);
        return shouldNotify;
    }

    public void close() {
        this.cache.destroy();
    }
}
