package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class InterruptAwareWaitTest {

    @AfterEach
    public void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    public void sleepRestoresInterruptFlagSoShutdownNowCanStopWorkers() throws Exception {
        Thread worker = new Thread(() -> {
            assertFalse(InterruptAwareWait.sleepMs(30_000));
            assertTrue(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(50);
        worker.interrupt();
        worker.join(2000);
        assertFalse(worker.isAlive(), "worker must exit after interrupt instead of swallowing it");
    }

    @Test
    public void alreadyInterruptedThreadAbortsRetries() {
        Thread.currentThread().interrupt();
        assertTrue(InterruptAwareWait.shouldAbortRetries());
    }
}
