package com.akto.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class TestIndexSyncJob {

    @Test
    public void successfulAccountIsCountedAndRunsOnce() {
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();

        InitializerListener.syncIndicesForAccount(1, calls::incrementAndGet, succeeded, failed);

        assertEquals(1, calls.get());
        assertEquals(1, succeeded.get());
        assertEquals(0, failed.get());
    }

    @Test
    public void failingAccountIsCountedAndDoesNotStopTheNextOne() {
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        InitializerListener.syncIndicesForAccount(1, () -> {
            throw new IllegalStateException("db down");
        }, succeeded, failed);
        InitializerListener.syncIndicesForAccount(2, () -> { }, succeeded, failed);

        assertEquals(1, failed.get());
        assertEquals(1, succeeded.get());
    }

    @Test
    public void disabledUnlessEnvIsTrue() {
        assumeTrue(System.getenv(InitializerListener.INDEX_SYNC_ENABLED_ENV) == null);
        assertFalse(InitializerListener.isIndexSyncEnabled());
    }
}
