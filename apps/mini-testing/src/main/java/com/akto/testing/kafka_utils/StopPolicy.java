package com.akto.testing.kafka_utils;

import java.util.function.LongSupplier;

import com.akto.testing.kafka_utils.TestRunMetrics.StopReason;

/**
 * Decides whether the drain loop should stop, and why. Pure: every input arrives through
 * {@link Signals}, so each terminal branch of the loop is a one-line unit test.
 *
 * <p>Order matters and mirrors the loop it replaced: an explicit stop beats the clock, the clock
 * beats a dead engine, a dead engine beats a stall, and only an idle, lag-free queue counts as done.
 */
final class StopPolicy {

    /** What the loop observed on this iteration. */
    record Signals(
            boolean testRunning,
            int elapsedSec,
            boolean consumerFailed,
            int processed,
            long workRemaining,
            /** Kafka lag; only consulted when {@code workRemaining == 0}. -1 means unknown. */
            LongSupplier lag) {
    }

    /**
     * Tracks whether {@code processed} has advanced recently. Stateful by necessity; kept tiny so
     * it can be tested with a fake clock.
     */
    static final class ProgressTracker {
        private final int stallTimeoutSec;
        private int lastSeenProcessed = -1;
        private int lastProgressTs;

        ProgressTracker(int stallTimeoutSec, int now) {
            this.stallTimeoutSec = stallTimeoutSec;
            this.lastProgressTs = now;
        }

        /** @return true if progress was observed on this call */
        boolean observe(int processed, int now) {
            if (processed != lastSeenProcessed) {
                lastSeenProcessed = processed;
                lastProgressTs = now;
                return true;
            }
            return false;
        }

        boolean isStalled(int now) {
            return now - lastProgressTs > stallTimeoutSec;
        }
    }

    private final int maxRunTimeSec;
    private final ProgressTracker progress;

    StopPolicy(int maxRunTimeSec, ProgressTracker progress) {
        this.maxRunTimeSec = maxRunTimeSec;
        this.progress = progress;
    }

    /**
     * @return the reason to stop, or {@code null} to keep draining
     */
    StopReason evaluate(Signals s, int now) {
        if (!s.testRunning()) {
            return StopReason.STOPPED;
        }
        if (s.elapsedSec() >= maxRunTimeSec) {
            return StopReason.MAX_RUNTIME;
        }
        if (s.consumerFailed()) {
            return StopReason.CONSUMER_FAILED;
        }
        boolean progressed = progress.observe(s.processed(), now);
        if (!progressed && s.workRemaining() > 0 && progress.isStalled(now)) {
            return StopReason.STALLED;
        }
        if (s.workRemaining() == 0 && s.lag().getAsLong() == 0) {
            return StopReason.ALL_PROCESSED;
        }
        return null;
    }
}
