package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.dto.SequenceCount;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class SequenceAnalyzerConfig {

    public static final int DEFAULT_N_MAX = 2;
    public static final int DEFAULT_MAX_HISTORY = DEFAULT_N_MAX;
    public static final long DEFAULT_SESSION_IDLE_MS = 10 * 60 * 1000L;
    public static final long DEFAULT_FLUSH_INTERVAL_MS = 5 * 60 * 1000L;
    public static final long DEFAULT_EVICTION_INTERVAL_MS = 60 * 1000L;

    private final int nMax;
    private final int maxHistory;
    private final long sessionIdleMs;
    private final long flushIntervalMs;
    private final long evictionIntervalMs;
    private final UserIdentifier userIdentifier;
    private final Consumer<List<SequenceCount>> sink;
    private final AktoPolicyNew aktoPolicyNew;
    private final LongSupplier clockMs;

    public SequenceAnalyzerConfig(int nMax, int maxHistory, long sessionIdleMs, long flushIntervalMs,
                                  long evictionIntervalMs, UserIdentifier userIdentifier,
                                  Consumer<List<SequenceCount>> sink, AktoPolicyNew aktoPolicyNew,
                                  LongSupplier clockMs) {
        if (nMax < 0) throw new IllegalArgumentException("nMax must be >= 0");
        if (maxHistory < Math.max(1, nMax)) throw new IllegalArgumentException("maxHistory must be >= max(1, nMax)");
        this.nMax = nMax;
        this.maxHistory = maxHistory;
        this.sessionIdleMs = sessionIdleMs;
        this.flushIntervalMs = flushIntervalMs;
        this.evictionIntervalMs = evictionIntervalMs;
        this.userIdentifier = userIdentifier;
        this.sink = sink;
        this.aktoPolicyNew = aktoPolicyNew;
        this.clockMs = clockMs;
    }

    public static SequenceAnalyzerConfig withDefaults(UserIdentifier userIdentifier,
                                                      Consumer<List<SequenceCount>> sink,
                                                      AktoPolicyNew aktoPolicyNew) {
        return new SequenceAnalyzerConfig(DEFAULT_N_MAX, DEFAULT_MAX_HISTORY, DEFAULT_SESSION_IDLE_MS,
                DEFAULT_FLUSH_INTERVAL_MS, DEFAULT_EVICTION_INTERVAL_MS, userIdentifier, sink, aktoPolicyNew,
                System::currentTimeMillis);
    }

    public int getNMax() {
        return nMax;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public long getSessionIdleMs() {
        return sessionIdleMs;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public long getEvictionIntervalMs() {
        return evictionIntervalMs;
    }

    public UserIdentifier getUserIdentifier() {
        return userIdentifier;
    }

    public Consumer<List<SequenceCount>> getSink() {
        return sink;
    }

    public AktoPolicyNew getAktoPolicyNew() {
        return aktoPolicyNew;
    }

    public LongSupplier getClockMs() {
        return clockMs;
    }
}
