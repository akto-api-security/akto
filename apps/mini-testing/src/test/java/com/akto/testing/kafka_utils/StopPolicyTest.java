package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.akto.testing.kafka_utils.StopPolicy.ProgressTracker;
import com.akto.testing.kafka_utils.StopPolicy.Signals;
import com.akto.testing.kafka_utils.TestRunMetrics.StopReason;

public class StopPolicyTest {

    private static final int MAX_RUNTIME = 600;
    private static final int STALL = 420;

    private static StopPolicy policy(int now) {
        return new StopPolicy(MAX_RUNTIME, new ProgressTracker(STALL, now));
    }

    private static Signals healthy(int processed, long workRemaining, long lag) {
        return new Signals(true, 10, false, processed, workRemaining, () -> lag);
    }

    @Test
    public void keepsDrainingWhileWorkRemains() {
        assertNull(policy(0).evaluate(healthy(3, 5, 5), 1));
    }

    @Test
    public void stoppedWinsOverEverything() {
        Signals s = new Signals(false, MAX_RUNTIME + 1, true, 0, 0, () -> 0);
        assertEquals(StopReason.STOPPED, policy(0).evaluate(s, 1));
    }

    @Test
    public void maxRuntimeBeatsDeadConsumer() {
        Signals s = new Signals(true, MAX_RUNTIME, true, 0, 0, () -> 0);
        assertEquals(StopReason.MAX_RUNTIME, policy(0).evaluate(s, 1));
    }

    @Test
    public void deadConsumerIsDetected() {
        Signals s = new Signals(true, 10, true, 0, 5, () -> 5);
        assertEquals(StopReason.CONSUMER_FAILED, policy(0).evaluate(s, 1));
    }

    @Test
    public void allProcessedOnlyWhenQueueEmptyAndLagZero() {
        assertEquals(StopReason.ALL_PROCESSED, policy(0).evaluate(healthy(10, 0, 0), 1));
        assertNull("lag unknown is not done", policy(0).evaluate(healthy(10, 0, -1), 1));
        assertNull("lag positive is not done", policy(0).evaluate(healthy(10, 0, 3), 1));
    }

    @Test
    public void lagIsNotConsultedWhileQueueNonEmpty() {
        Signals s = new Signals(true, 10, false, 1, 4, () -> { throw new AssertionError("lag queried"); });
        assertNull(policy(0).evaluate(s, 1));
    }

    @Test
    public void stallFiresOnlyAfterTimeoutWithoutProgress() {
        StopPolicy p = policy(0);
        assertNull(p.evaluate(healthy(1, 5, 5), 1));              // progress at t=1
        assertNull(p.evaluate(healthy(1, 5, 5), 1 + STALL));      // not yet past timeout
        assertEquals(StopReason.STALLED, p.evaluate(healthy(1, 5, 5), 2 + STALL));
    }

    @Test
    public void progressResetsStallClock() {
        StopPolicy p = policy(0);
        assertNull(p.evaluate(healthy(1, 5, 5), 1));
        assertNull(p.evaluate(healthy(2, 5, 5), 1 + STALL));      // progressed, clock reset
        assertNull(p.evaluate(healthy(2, 5, 5), 2 + STALL));
        assertEquals(StopReason.STALLED, p.evaluate(healthy(2, 5, 5), 2 + 2 * STALL));
    }

    @Test
    public void stallIgnoredWhenNoWorkRemains() {
        StopPolicy p = policy(0);
        assertNull(p.evaluate(healthy(1, 0, 3), 1));
        assertNull(p.evaluate(healthy(1, 0, 3), 5 + STALL));
    }
}
