package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class TestingExecutorLifecycleTest {

    @Test
    public void forceShutdownTerminatesWorkersThatHonorInterrupt() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch started = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                started.countDown();
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));

        TestingExecutorLifecycle.shutdownQuietly(pool, 2, true);

        assertTrue(pool.isTerminated());
    }

    @Test
    public void forceShutdownCannotReapWorkersThatSwallowInterrupt() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger keepGoing = new AtomicInteger(1);
        CountDownLatch started = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                started.countDown();
                while (keepGoing.get() == 1) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));

        TestingExecutorLifecycle.shutdownQuietly(pool, 1, true);

        assertFalse("swallowed interrupt is the leak we saw in production", pool.isTerminated());
        keepGoing.set(0);
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
    }
}
