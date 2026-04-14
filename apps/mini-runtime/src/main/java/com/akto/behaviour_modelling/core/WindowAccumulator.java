package com.akto.behaviour_modelling.core;

import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.behaviour_modelling.model.WindowSnapshot;

/**
 * Accumulates API call and transition counts within a single window.
 *
 * The userId is passed through even in phase 1 (where it is ignored) so that
 * phase 2 (unique-user weighting) can be wired in by swapping the implementation
 * without changing any call sites.
 *
 * Implementations must be thread-safe.
 */
public interface WindowAccumulator {
    void recordApiCall(int apiId, String userId);
    void recordTransition(TransitionKey key, String userId);
    WindowSnapshot snapshot(long windowStart, long windowEnd);
    void reset();
}
