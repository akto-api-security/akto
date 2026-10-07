package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.akto.testing.kafka_utils.RunCleanup.StateAction;
import com.akto.testing.kafka_utils.TestRunMetrics.StopReason;

public class RunCleanupTest {

    private final List<String> calls = new ArrayList<>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    private RunCleanup cleanup() {
        return new RunCleanup(
                (exec, wait, force) -> calls.add("shutdown(" + wait + "," + force + ")"),
                () -> calls.add("flush"),
                abrupt -> calls.add("close(" + abrupt + ")"),
                () -> calls.add("discard"),
                code -> calls.add("exit(" + code + ")"));
    }

    @Test
    public void abruptReasonsAreExactlyErrorFailedStalled() {
        for (StopReason r : StopReason.values()) {
            boolean expected = r == StopReason.ERROR || r == StopReason.CONSUMER_FAILED || r == StopReason.STALLED;
            assertEquals(r.name(), expected, RunCleanup.isAbrupt(r));
        }
    }

    @Test
    public void onlyResumableFailuresKeepState() {
        assertEquals(StateAction.KEEP_FOR_RESUME, RunCleanup.stateActionFor(StopReason.CONSUMER_FAILED));
        assertEquals(StateAction.KEEP_FOR_RESUME, RunCleanup.stateActionFor(StopReason.STALLED));
        assertEquals(StateAction.DISCARD, RunCleanup.stateActionFor(StopReason.ERROR));
        assertEquals(StateAction.DISCARD, RunCleanup.stateActionFor(StopReason.ALL_PROCESSED));
        assertEquals(StateAction.DISCARD, RunCleanup.stateActionFor(StopReason.STOPPED));
        assertEquals(StateAction.DISCARD, RunCleanup.stateActionFor(StopReason.MAX_RUNTIME));
    }

    @Test
    public void cleanCompletionDrainsThenDiscards() {
        cleanup().run(StopReason.ALL_PROCESSED, pool);
        assertEquals(Arrays.asList("flush", "shutdown(30,false)", "close(false)", "discard"), calls);
    }

    @Test
    public void stalledRunExitsWithoutDiscarding() {
        cleanup().run(StopReason.STALLED, pool);
        assertEquals(Arrays.asList("flush", "shutdown(5,true)", "close(true)", "exit(1)"), calls);
        assertFalse(calls.contains("discard"));
    }

    @Test
    public void errorIsAbruptButStillDiscards() {
        cleanup().run(StopReason.ERROR, pool);
        assertEquals(Arrays.asList("flush", "shutdown(5,true)", "close(true)", "discard"), calls);
        assertTrue(pool.shutdownNow().isEmpty());
    }
}
