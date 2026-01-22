package com.akto.threat.detection.tasks;
import com.akto.dao.context.Context;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.ip_api_counter.CmsCounterLayer;
import com.clearspring.analytics.stream.frequency.CountMinSketch;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CmsCounterLayerTest {

    private static CmsCounterLayer cmsCounterLayer;

    private String windowKeyNow() {
        long ts = Context.now();
        return String.valueOf(ts/60);
    }

    private String windowKeyOffsetMinutes(int offset) {
        long ts = Context.now();
        long shifted = ts - offset * 60L;
        return String.valueOf(shifted / 60);
    }

    @BeforeAll
    static void setUpOnce() {
        // Create single instance without starting scheduled tasks
        cmsCounterLayer = new CmsCounterLayer(null, "test-prefix");
    }

    @BeforeEach
    void setUp() {
        // Reset state between tests
        cmsCounterLayer.reset();
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

        // Mock cache
        CounterCache mockCache = mock(CounterCache.class);
        when(mockCache.fetchDataBytes(windowKey)).thenReturn(serialized);

        // Create instance with mocked cache
        CmsCounterLayer testLayer = new CmsCounterLayer(mockCache, "test-prefix");

        // When
        CountMinSketch result = invokeFetchFromRedis(testLayer, windowKey);

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

        // Create instance with mocked cache
        CmsCounterLayer testLayer = new CmsCounterLayer(mockCache, "test-prefix");

        // When
        CountMinSketch result = invokeFetchFromRedis(testLayer, windowKey);

        // Then
        assertNull(result);
    }

    @Test
    public void testFetchFromRedisReturnsNullWhenCacheIsNull() throws Exception {
        // Create instance with null cache
        CmsCounterLayer testLayer = new CmsCounterLayer(null, "test-prefix");

        // When
        CountMinSketch result = invokeFetchFromRedis(testLayer, "any-key");

        // Then
        assertNull(result);
    }

    private CountMinSketch invokeFetchFromRedis(CmsCounterLayer instance, String windowKey) throws Exception {
        java.lang.reflect.Method method = CmsCounterLayer.class.getDeclaredMethod("fetchFromRedis", String.class);
        method.setAccessible(true);
        return (CountMinSketch) method.invoke(instance, windowKey);
    }

    @Test
    void testCleanupOldWindows() throws Exception {
        String key = "11111|8.8.8.8|/api/test";

        // Retention is 480 minutes (8 hours)
        // Add windows: 490, 485, 480 (boundary), 479, 240 minutes ago
        String oldWindow1 = windowKeyOffsetMinutes(490);       // Should be deleted (> 480)
        String oldWindow2 = windowKeyOffsetMinutes(485);       // Should be deleted (> 480)
        String boundaryWindow = windowKeyOffsetMinutes(480);   // Boundary - should be kept (== 480)
        String recentWindow1 = windowKeyOffsetMinutes(479);    // Should be kept (< 480)
        String recentWindow2 = windowKeyOffsetMinutes(240);    // Should be kept (< 480)

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

        // Call cleanup
        cmsCounterLayer.cleanupOldWindows();

        // Post-cleanup: oldWindow1 and oldWindow2 should be gone (> 480 minutes)
        assertEquals(0, cmsCounterLayer.estimateCount(key, oldWindow1));
        assertEquals(0, cmsCounterLayer.estimateCount(key, oldWindow2));

        // Boundary and recent should still exist (>= 480 minutes retained)
        assertEquals(1, cmsCounterLayer.estimateCount(key, boundaryWindow));
        assertEquals(1, cmsCounterLayer.estimateCount(key, recentWindow1));
        assertEquals(1, cmsCounterLayer.estimateCount(key, recentWindow2));
    }

}
