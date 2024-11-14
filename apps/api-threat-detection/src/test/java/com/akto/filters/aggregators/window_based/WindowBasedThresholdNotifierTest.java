package com.akto.filters.aggregators.window_based;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

import com.akto.cache.CounterCache;

class MemCache implements CounterCache {

    private final ConcurrentHashMap<String, Long> cache;

    public MemCache() {
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public void incrementBy(String key, long val) {
        cache.put(key, cache.getOrDefault(key, 0L) + val);
    }

    @Override
    public void increment(String key) {
        incrementBy(key, 1);
    }

    @Override
    public long get(String key) {
        return cache.getOrDefault(key, 0L);
    }

    public Map<String, Long> internalCache() {
        return cache;
    }
}

public class WindowBasedThresholdNotifierTest {
    private static HttpResponseParams generateResponseParamsForStatusCode(int statusCode) {
        return new HttpResponseParams(
                "HTTP/1.1",
                statusCode,
                "Bad Request",
                new HashMap<>(),
                "{'error': 'Bad Request'}",
                new HttpRequestParams(
                        "POST",
                        "/api/v1/endpoint",
                        "HTTP/1.1",
                        new HashMap<>(),
                        "{'error': 'Bad Request'}",
                        1),
                (int) (System.currentTimeMillis() / 1000L),
                "100000",
                false,
                HttpResponseParams.Source.OTHER,
                "",
                "192.168.0.1");
    }

    @Test
    public void testShouldNotify() throws InterruptedException {

        MemCache cache = new MemCache();
        WindowBasedThresholdNotifier notifier = new WindowBasedThresholdNotifier(
                cache, new WindowBasedThresholdNotifier.Config(10, 1));

        boolean shouldNotify = false;
        String ip = "192.168.0.1";

        for (int i = 0; i < 1000; i++) {
            boolean _shouldNotify = notifier.shouldNotify(
                    ip,
                    "4XX_FILTER",
                    WindowBasedThresholdNotifierTest
                            .generateResponseParamsForStatusCode(400));
            shouldNotify = shouldNotify || _shouldNotify;
        }

        long count = 0;
        for (Map.Entry<String, Long> entry : cache.internalCache().entrySet()) {
            count += entry.getValue();
        }

        assertEquals(1000, count);
    }

}
