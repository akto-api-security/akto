package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Test;

import com.akto.testing.kafka_utils.RecordProcessor.Outcome;

public class RecordProcessorTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @After
    public void tearDown() {
        pool.shutdownNow();
    }

    @Test
    public void completesWhenRunnerReturns() {
        RecordProcessor p = new RecordProcessor(pool, 5, (msg, id) -> {});
        RecordProcessor.Result r = p.process("r1", "{}");
        assertEquals(Outcome.COMPLETED, r.outcome);
    }

    @Test
    public void timesOutAndInterruptsTheWorker() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        RecordProcessor p = new RecordProcessor(pool, 1, (msg, id) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });

        RecordProcessor.Result r = p.process("r1", "{}");

        assertEquals(Outcome.TIMED_OUT, r.outcome);
        assertTrue("worker should be cancelled with interrupt", interrupted.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void runnerExceptionIsErrored() {
        RecordProcessor p = new RecordProcessor(pool, 5, (msg, id) -> { throw new IllegalStateException("boom"); });
        RecordProcessor.Result r = p.process("r1", "{}");
        assertEquals(Outcome.ERRORED, r.outcome);
        assertNotNull(r.error);
    }

    @Test
    public void rejectedWhenExecutorIsShutDown() {
        pool.shutdown();
        AtomicBoolean ran = new AtomicBoolean();
        RecordProcessor p = new RecordProcessor(pool, 5, (msg, id) -> ran.set(true));

        RecordProcessor.Result r = p.process("r1", "{}");

        assertEquals(Outcome.REJECTED, r.outcome);
        assertEquals(false, ran.get());
    }

    @Test
    public void interruptedCallerIsReportedAndFlagRestored() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        RecordProcessor p = new RecordProcessor(pool, 5, (msg, id) -> {
            started.countDown();
            Thread.sleep(10_000);
        });
        Thread caller = Thread.currentThread();
        new Thread(() -> {
            try { started.await(); } catch (InterruptedException ignored) {}
            caller.interrupt();
        }).start();

        RecordProcessor.Result r = p.process("r1", "{}");

        assertEquals(Outcome.INTERRUPTED, r.outcome);
        assertTrue(Thread.interrupted()); // flag was restored; clear it for the next test
    }
}
