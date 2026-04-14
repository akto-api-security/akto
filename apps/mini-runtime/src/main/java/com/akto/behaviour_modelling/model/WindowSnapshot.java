package com.akto.behaviour_modelling.model;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of aggregated data for a completed window.
 * Produced by WindowAccumulator at the end of each window and handed to WindowFlusher.
 */
public final class WindowSnapshot {

    private final long windowStart;
    private final long windowEnd;

    // apiId -> total call count within the window
    private final Map<Integer, Long> apiCounts;

    // transition sequence -> total count within the window
    private final Map<TransitionKey, Long> transitionCounts;

    public WindowSnapshot(long windowStart, long windowEnd,
                          Map<Integer, Long> apiCounts,
                          Map<TransitionKey, Long> transitionCounts) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.apiCounts = Collections.unmodifiableMap(apiCounts);
        this.transitionCounts = Collections.unmodifiableMap(transitionCounts);
    }

    public long getWindowStart() {
        return windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    public Map<Integer, Long> getApiCounts() {
        return apiCounts;
    }

    public Map<TransitionKey, Long> getTransitionCounts() {
        return transitionCounts;
    }

    public boolean isEmpty() {
        return apiCounts.isEmpty();
    }
}
