package com.akto.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestTtlCache {

    @Test
    public void returnsTheValueUntilItExpires() throws InterruptedException {
        TtlCache<String, String> cache = new TtlCache<>(300, 10);
        cache.put("key", "value");
        assertEquals("value", cache.get("key"));
        Thread.sleep(700);
        assertNull(cache.get("key"));
    }

    @Test
    public void startsOverWhenFull() {
        TtlCache<String, Integer> cache = new TtlCache<>(60_000, 2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        assertNull(cache.get("a"));
        assertEquals(Integer.valueOf(3), cache.get("c"));
    }

    @Test
    public void aFullMapDropsExpiredEntriesBeforeStartingOver() throws InterruptedException {
        TtlCache<String, Integer> cache = new TtlCache<>(200, 2);
        cache.put("a", 1);
        Thread.sleep(400);
        cache.put("b", 2);
        cache.put("c", 3);
        // "a" had expired, so "b" survives instead of the whole map starting over.
        assertNull(cache.get("a"));
        assertEquals(Integer.valueOf(2), cache.get("b"));
        assertEquals(Integer.valueOf(3), cache.get("c"));
    }

    @Test
    public void overwritingAKeyAtCapacityKeepsTheOthers() {
        TtlCache<String, Integer> cache = new TtlCache<>(60_000, 2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("a", 3);
        assertEquals(Integer.valueOf(3), cache.get("a"));
        assertEquals(Integer.valueOf(2), cache.get("b"));
    }

    @Test
    public void removesKeysByPredicate() {
        TtlCache<String, Integer> cache = new TtlCache<>(60_000, 10);
        cache.put("1_x", 1);
        cache.put("2_x", 2);
        cache.removeKeysIf(key -> key.startsWith("1_"));
        assertNull(cache.get("1_x"));
        assertEquals(Integer.valueOf(2), cache.get("2_x"));
    }
}
