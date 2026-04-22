package com.akto.behaviour_modelling.model;

import com.akto.dto.ApiInfo.ApiInfoKey;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of aggregated data for a completed window.
 * Produced by WindowAccumulator at the end of each window and handed to WindowFlusher.
 * Self-describing: ApiInfoKey carries collectionId, url, and method — no registry needed.
 */
public final class WindowSnapshot {

    private final long windowStart;
    private final long windowEnd;

    // ApiInfoKey -> total call count within the window
    private final Map<ApiInfoKey, Long> apiCounts;

    // transition sequence -> total count within the window
    private final Map<TransitionKey, Long> transitionCounts;

    public WindowSnapshot(long windowStart, long windowEnd,
                          Map<ApiInfoKey, Long> apiCounts,
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

    public Map<ApiInfoKey, Long> getApiCounts() {
        return apiCounts;
    }

    public Map<TransitionKey, Long> getTransitionCounts() {
        return transitionCounts;
    }

    public boolean isEmpty() {
        return apiCounts.isEmpty();
    }
}
