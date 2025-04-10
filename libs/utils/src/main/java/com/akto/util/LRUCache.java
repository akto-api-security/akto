package com.akto.util;

import com.maxmind.db.CacheKey;
import com.maxmind.db.CHMCache;
import com.maxmind.db.DecodedValue;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;


public class LRUCache extends CHMCache {

    private final int capacity;
    private final Map<CacheKey, DecodedValue> cache;

    public LRUCache(int capacity) {
        super(capacity);
        this.capacity = capacity;

        this.cache = new LinkedHashMap<CacheKey, DecodedValue>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, DecodedValue> eldest) {
                // Remove the eldest entry when the size exceeds the capacity
                return size() > LRUCache.this.capacity;
            }
        };
    }

    @Override
    public DecodedValue get(CacheKey key, Loader loader) throws IOException {
        DecodedValue value = cache.get(key);
        if (value == null) {
            value = loader.load(key);
            cache.put(key, value);
        }
        return value;
    }
}
