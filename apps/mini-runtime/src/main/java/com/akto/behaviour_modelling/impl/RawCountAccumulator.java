package com.akto.behaviour_modelling.impl;

import com.akto.behaviour_modelling.core.WindowAccumulator;
import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.behaviour_modelling.model.WindowSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Phase 1 accumulator: counts raw occurrences of API calls and transitions.
 * userId is accepted but intentionally ignored — the interface is ready for
 * phase 2 (unique-user weighting) without any call-site changes.
 *
 * Uses LongAdder per key for high-throughput concurrent increments with
 * minimal contention.
 */
public class RawCountAccumulator implements WindowAccumulator {

    private final ConcurrentHashMap<Integer, LongAdder> apiCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TransitionKey, LongAdder> transitionCounts = new ConcurrentHashMap<>();

    @Override
    public void recordApiCall(int apiId, String userId) {
        apiCounts.computeIfAbsent(apiId, k -> new LongAdder()).increment();
    }

    @Override
    public void recordTransition(TransitionKey key, String userId) {
        transitionCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    @Override
    public WindowSnapshot snapshot(long windowStart, long windowEnd) {
        Map<Integer, Long> apiCountsCopy = new HashMap<>(apiCounts.size());
        apiCounts.forEach((k, v) -> apiCountsCopy.put(k, v.sum()));

        Map<TransitionKey, Long> transitionCountsCopy = new HashMap<>(transitionCounts.size());
        transitionCounts.forEach((k, v) -> transitionCountsCopy.put(k, v.sum()));

        return new WindowSnapshot(windowStart, windowEnd, apiCountsCopy, transitionCountsCopy);
    }

    @Override
    public void reset() {
        apiCounts.clear();
        transitionCounts.clear();
    }
}
