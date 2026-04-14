package com.akto.behaviour_modelling;

import com.akto.dto.ApiInfo.ApiInfoKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Assigns a stable integer ID to each unique ApiInfoKey.
 * IDs are assigned on first seen and never change, surviving window flips.
 * Thread-safe; shared across all windows.
 */
public class ApiRegistry {

    private final ConcurrentHashMap<ApiInfoKey, Integer> registry = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Returns the existing ID for this key, or assigns and returns a new one.
     */
    public int resolve(ApiInfoKey key) {
        return registry.computeIfAbsent(key, k -> counter.incrementAndGet());
    }

    /**
     * Reverse lookup: returns the ApiInfoKey for a given ID, or null if not found.
     * O(n) — intended for use at flush time, not the hot path.
     */
    public ApiInfoKey lookup(int id) {
        for (ConcurrentHashMap.Entry<ApiInfoKey, Integer> entry : registry.entrySet()) {
            if (entry.getValue() == id) return entry.getKey();
        }
        return null;
    }

    public int size() {
        return registry.size();
    }
}
