package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.behaviour_modelling.core.WindowAccumulator;
import com.akto.behaviour_modelling.core.WindowFlusher;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;

import java.util.function.Supplier;

public class SequenceAnalyzerConfig {

    // Maximum Markov chain order. 10 means track orders 1 through 10.
    // Order 1 = pairs (A→B), order 2 = triples (A→B→C), etc.
    private final int maxOrder;

    private final long windowDurationMs;
    private final UserIdentifier userIdentifier;

    // Factory so the analyzer can create a fresh accumulator on each window flip.
    private final Supplier<WindowAccumulator> accumulatorFactory;

    private final WindowFlusher flusher;

    // Used for URL templatization (e.g. /users/123 → /users/INTEGER)
    // and as a guard to skip URLs not yet known in the catalog.
    private final AktoPolicyNew aktoPolicyNew;

    public SequenceAnalyzerConfig(int maxOrder, long windowDurationMs,
                                  UserIdentifier userIdentifier,
                                  Supplier<WindowAccumulator> accumulatorFactory,
                                  WindowFlusher flusher,
                                  AktoPolicyNew aktoPolicyNew) {
        if (maxOrder < 1) throw new IllegalArgumentException("maxOrder must be >= 1");
        this.maxOrder = maxOrder;
        this.windowDurationMs = windowDurationMs;
        this.userIdentifier = userIdentifier;
        this.accumulatorFactory = accumulatorFactory;
        this.flusher = flusher;
        this.aktoPolicyNew = aktoPolicyNew;
    }

    public int getMaxOrder() {
        return maxOrder;
    }

    public long getWindowDurationMs() {
        return windowDurationMs;
    }

    public UserIdentifier getUserIdentifier() {
        return userIdentifier;
    }

    public Supplier<WindowAccumulator> getAccumulatorFactory() {
        return accumulatorFactory;
    }

    public WindowFlusher getFlusher() {
        return flusher;
    }

    public AktoPolicyNew getAktoPolicyNew() {
        return aktoPolicyNew;
    }

}
