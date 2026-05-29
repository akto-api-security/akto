package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.behaviour_modelling.core.WindowAccumulator;
import com.akto.behaviour_modelling.core.WindowFlusher;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;

import java.util.function.Supplier;

public class SequenceAnalyzerConfig {

    // Number of APIs in a tracked sequence. 2 = pairs (A→B), 3 = triplets (A→B→C).
    private final int sequenceLength;

    private final long windowDurationMs;
    private final UserIdentifier userIdentifier;

    // Factory so the analyzer can create a fresh accumulator on each window flip.
    private final Supplier<WindowAccumulator> accumulatorFactory;

    private final WindowFlusher flusher;

    // Used for URL templatization (e.g. /users/123 → /users/INTEGER)
    // and as a guard to skip URLs not yet known in the catalog.
    private final AktoPolicyNew aktoPolicyNew;

    public SequenceAnalyzerConfig(int sequenceLength, long windowDurationMs,
                                  UserIdentifier userIdentifier,
                                  Supplier<WindowAccumulator> accumulatorFactory,
                                  WindowFlusher flusher,
                                  AktoPolicyNew aktoPolicyNew) {
        if (sequenceLength < 2) throw new IllegalArgumentException("sequenceLength must be >= 2");
        this.sequenceLength = sequenceLength;
        this.windowDurationMs = windowDurationMs;
        this.userIdentifier = userIdentifier;
        this.accumulatorFactory = accumulatorFactory;
        this.flusher = flusher;
        this.aktoPolicyNew = aktoPolicyNew;
    }

    public int getSequenceLength() {
        return sequenceLength;
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
