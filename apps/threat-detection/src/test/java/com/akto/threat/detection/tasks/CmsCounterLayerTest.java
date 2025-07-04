package com.akto.threat.detection.tasks;
import com.akto.dao.context.Context;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.ip_api_counter.CmsCounterLayer;
import com.clearspring.analytics.stream.frequency.CountMinSketch;

import io.lettuce.core.RedisClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CmsCounterLayerTest {

    private CmsCounterLayer cmsCounterLayer;

    private String windowKeyNow() {
        long ts = Context.now();
        return String.valueOf(ts/60);
    }

    private String windowKeyOffsetMinutes(int offset) {
        long ts = Context.now();
        long shifted = ts - offset * 60L;
        return String.valueOf(shifted / 60);
    }

    @BeforeEach
    void setUp() {
        CmsCounterLayer.initialize(null);
        cmsCounterLayer = CmsCounterLayer.getInstance();
        Field cacheField;
        try {
            cacheField = CmsCounterLayer.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            cacheField.set(null, null);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    
    @Test
    void testIncrementAndEstimateSingleKey() {
        String key = "11111|1.1.1.1|GET|/api/foo";
        String window = windowKeyNow();

        cmsCounterLayer.increment(key, window);
        cmsCounterLayer.increment(key, window);
        cmsCounterLayer.increment(key, window);

        long estimated = cmsCounterLayer.estimateCount(key, window);
        assertTrue(estimated >= 3, "Estimate should be at least 3");
    }

    @Test
    void testEstimateDifferentKeysSameWindow() {
        String window = windowKeyNow();
        String key1 = "11111|1.1.1.1|/api/a";
        String key2 = "11111|2.2.2.2|/api/b";

        for (int i = 0; i < 10; i++) cmsCounterLayer.increment(key1, window);
        for (int i = 0; i < 5; i++) cmsCounterLayer.increment(key2, window);

        assertTrue(cmsCounterLayer.estimateCount(key1, window) >= 10);
        assertTrue(cmsCounterLayer.estimateCount(key2, window) >= 5);
    }

    @Test
    void testSameKeyDifferentWindows() {
        String key = "11111|3.3.3.3|/api/x";
        String window1 = windowKeyOffsetMinutes(2);
        String window2 = windowKeyOffsetMinutes(1);

        cmsCounterLayer.increment(key, window1);
        cmsCounterLayer.increment(key, window2);
        cmsCounterLayer.increment(key, window2);

        assertEquals(1, cmsCounterLayer.estimateCount(key, window1));
        assertEquals(2, cmsCounterLayer.estimateCount(key, window2));
    }

    @Test
    void testMissingWindowReturnsZero() {
        String nonexistentWindow = String.valueOf(99999999L);
        String key = "11111|no.key|/missing";
        assertEquals(0, cmsCounterLayer.estimateCount(key, nonexistentWindow));
    }

    @Test
    void testSerializationDeserialization() throws IOException {
        String window = windowKeyOffsetMinutes(5);
        String key = "11111|9.9.9.9|/api/serialize";

        for (int i = 0; i < 7; i++) {
            cmsCounterLayer.increment(key, window);
        }

        CountMinSketch original = cmsCounterLayer.getSketch(window);
        byte[] bytes = cmsCounterLayer.serializeSketch(original);
        CountMinSketch restored = cmsCounterLayer.deserializeSketch(bytes);

        assertEquals(original.estimateCount(key), restored.estimateCount(key));
    }

    @Test
    void testMultipleKeysMultipleWindows() {
        for (int min = 0; min < 3; min++) {
            String window = windowKeyOffsetMinutes(min);
            for (int i = 0; i < min + 1; i++) {
                String key = "11111|4.4.4." + i + "|/api/z";
                System.out.println("key " + key + " window " + window);
                cmsCounterLayer.increment(key, window);
                System.out.println("key " + key + " window " + window);
                cmsCounterLayer.increment(key, window);
            }
        }

        assertEquals(2, cmsCounterLayer.estimateCount("11111|4.4.4.0|/api/z", windowKeyOffsetMinutes(0)));
        assertEquals(2, cmsCounterLayer.estimateCount("11111|4.4.4.1|/api/z", windowKeyOffsetMinutes(1)));
        assertEquals(2, cmsCounterLayer.estimateCount("11111|4.4.4.2|/api/z", windowKeyOffsetMinutes(2)));
    }

    @Test
    public void testFetchFromRedisReturnsDeserializedCMS() throws Exception {
        // Given
        String windowKey = "123456";
        CountMinSketch mockCMS = new CountMinSketch(0.01, 0.99, 12345);
        mockCMS.add("test-key", 1);
        byte[] serialized = CountMinSketch.serialize(mockCMS);

        // Mock Redis and cache
        CounterCache mockCache = mock(CounterCache.class);
        when(mockCache.fetchDataBytes(windowKey)).thenReturn(serialized);

        RedisClient mockRedisClient = mock(RedisClient.class);
        ApiCountCacheLayer mockApiCountCacheLayer = mock(ApiCountCacheLayer.class);

        // Override static cache field in CmsCounterLayer
        Field cacheField = CmsCounterLayer.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        cacheField.set(null, mockCache);

        // When
        CountMinSketch result = invokeFetchFromRedis(windowKey);

        // Then
        assertNotNull(result);
        assertTrue(result.estimateCount("test-key") > 0);
    }

    @Test
    public void testFetchFromRedisReturnsNullWhenNoData() throws Exception {
        // Given
        String windowKey = "654321";
        CounterCache mockCache = mock(CounterCache.class);
        when(mockCache.fetchDataBytes(windowKey)).thenReturn(null);

        // Set static cache
        Field cacheField = CmsCounterLayer.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        cacheField.set(null, mockCache);

        // When
        CountMinSketch result = invokeFetchFromRedis(windowKey);

        // Then
        assertNull(result);
    }

    @Test
    public void testFetchFromRedisReturnsNullWhenCacheIsNull() throws Exception {
        // Ensure static cache is null
        Field cacheField = CmsCounterLayer.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        cacheField.set(null, null);

        // When
        CountMinSketch result = invokeFetchFromRedis("any-key");

        // Then
        assertNull(result);
    }

    private CountMinSketch invokeFetchFromRedis(String windowKey) throws Exception {
        java.lang.reflect.Method method = CmsCounterLayer.class.getDeclaredMethod("fetchFromRedis", String.class);
        method.setAccessible(true);
        return (CountMinSketch) method.invoke(null, windowKey);
    }

    @Test
    void testCleanupOldWindows() throws Exception {
        String key = "11111|8.8.8.8|/api/test";

        // Add windows: 70, 65, 60, 59, 55 minutes ago
        String oldWindow1 = windowKeyOffsetMinutes(70);
        String oldWindow2 = windowKeyOffsetMinutes(65);
        String boundaryWindow = windowKeyOffsetMinutes(60);
        String recentWindow1 = windowKeyOffsetMinutes(59);
        String recentWindow2 = windowKeyOffsetMinutes(55);

        cmsCounterLayer.increment(key, oldWindow1);
        cmsCounterLayer.increment(key, oldWindow2);
        cmsCounterLayer.increment(key, boundaryWindow);
        cmsCounterLayer.increment(key, recentWindow1);
        cmsCounterLayer.increment(key, recentWindow2);

        // Check pre-cleanup values
        assertEquals(1, cmsCounterLayer.estimateCount(key, oldWindow1));
        assertEquals(1, cmsCounterLayer.estimateCount(key, oldWindow2));
        assertEquals(1, cmsCounterLayer.estimateCount(key, boundaryWindow));
        assertEquals(1, cmsCounterLayer.estimateCount(key, recentWindow1));
        assertEquals(1, cmsCounterLayer.estimateCount(key, recentWindow2));

        // // Call cleanup via reflection or helper method
        // CmsCounterLayer.class.getDeclaredMethod("cleanupOldWindows").setAccessible(true);
        // CmsCounterLayer.class.getDeclaredMethod("cleanupOldWindows").invoke(null);

        CmsCounterLayer.cleanupOldWindows();

        // Post-cleanup: oldWindow1 and oldWindow2 should be gone
        assertEquals(0, cmsCounterLayer.estimateCount(key, oldWindow1));
        assertEquals(0, cmsCounterLayer.estimateCount(key, oldWindow2));

        // Boundary and recent should still exist
        assertEquals(1, cmsCounterLayer.estimateCount(key, boundaryWindow));
        assertEquals(1, cmsCounterLayer.estimateCount(key, recentWindow1));
        assertEquals(1, cmsCounterLayer.estimateCount(key, recentWindow2));
    }

}
