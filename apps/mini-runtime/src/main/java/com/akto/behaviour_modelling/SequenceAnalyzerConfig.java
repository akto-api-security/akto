package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.behaviour_modelling.core.WindowAccumulator;
import com.akto.behaviour_modelling.core.WindowFlusher;

import java.util.function.Supplier;

public class SequenceAnalyzerConfig {

    // Number of APIs in a tracked sequence. 2 = pairs (A→B), 3 = triplets (A→B→C).
    private final int sequenceLength;

    private final long windowDurationMs;
    private final UserIdentifier userIdentifier;

    // Factory so the analyzer can create a fresh accumulator on each window flip.
    private final Supplier<WindowAccumulator> accumulatorFactory;

    private final WindowFlusher flusher;

    public SequenceAnalyzerConfig(int sequenceLength, long windowDurationMs,
                                  UserIdentifier userIdentifier,
                                  Supplier<WindowAccumulator> accumulatorFactory,
                                  WindowFlusher flusher) {
        if (sequenceLength < 2) throw new IllegalArgumentException("sequenceLength must be >= 2");
        this.sequenceLength = sequenceLength;
        this.windowDurationMs = windowDurationMs;
        this.userIdentifier = userIdentifier;
        this.accumulatorFactory = accumulatorFactory;
        this.flusher = flusher;
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
}
