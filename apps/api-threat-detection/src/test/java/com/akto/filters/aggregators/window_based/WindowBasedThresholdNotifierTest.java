package com.akto.filters.aggregators.window_based;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Optional;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

import com.akto.cache.TypeValueCache;

class MemCache<V> implements TypeValueCache<V> {

    private final ConcurrentHashMap<String, V> cache;

    public MemCache() {
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<V> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public V getOrDefault(String key, V defaultValue) {
        return this.cache.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(String key) {
        return this.cache.containsKey(key);
    }

    @Override
    public void put(String key, V value) {
        this.cache.put(key, value);
    }

    @Override
    public long size() {
        return this.cache.size();
    }

    @Override
    public void destroy() {
        this.cache.clear();
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

        MemCache<Data> cache = new MemCache<>();
        WindowBasedThresholdNotifier notifier = new WindowBasedThresholdNotifier(
                cache, new WindowBasedThresholdNotifier.Config(10, 1));

        boolean shouldNotify = false;
        String ip = "192.168.0.1";

        for (int i = 0; i < 1000; i++) {
            shouldNotify = shouldNotify
                    || notifier.shouldNotify(
                            ip,
                            WindowBasedThresholdNotifierTest
                                    .generateResponseParamsForStatusCode(400));
        }

        Data data = cache.get(ip).orElse(new Data());
        assertEquals(10, data.getRequests().size());

        long lastNotifiedAt = data.getLastNotifiedAt();
        assertNotEquals(lastNotifiedAt, 0);

        assertTrue(shouldNotify);

        Thread.sleep(2000L);

        shouldNotify = notifier.shouldNotify(
                ip,
                WindowBasedThresholdNotifierTest.generateResponseParamsForStatusCode(401));
        assertFalse(shouldNotify);

        data = cache.get(ip).orElse(new Data());

    }

}
