package com.akto.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Small bounded cache whose entries expire after a fixed time; safe for concurrent use. */
public final class TtlCache<K, V> {

    private static final class Entry<V> {
        private final V value;
        private final long loadedAtMs;

        private Entry(V value, long loadedAtMs) {
            this.value = value;
            this.loadedAtMs = loadedAtMs;
        }
    }

    private final long ttlMs;
    private final int maxEntries;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(long ttlMs, int maxEntries) {
        this.ttlMs = ttlMs;
        this.maxEntries = maxEntries;
    }

    /** The cached value, or null when absent or older than the TTL. */
    public V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry != null && System.currentTimeMillis() - entry.loadedAtMs < ttlMs ? entry.value : null;
    }

    public void put(K key, V value) {
        long nowMs = System.currentTimeMillis();
        // Only a full map is swept: expired entries go first, and if it is still full it starts over.
        if (!entries.containsKey(key) && entries.size() >= maxEntries) {
            entries.values().removeIf(entry -> nowMs - entry.loadedAtMs >= ttlMs);
            if (entries.size() >= maxEntries) entries.clear();
        }
        entries.put(key, new Entry<>(value, nowMs));
    }

    public void removeKeysIf(Predicate<K> predicate) {
        entries.keySet().removeIf(predicate);
    }
}
