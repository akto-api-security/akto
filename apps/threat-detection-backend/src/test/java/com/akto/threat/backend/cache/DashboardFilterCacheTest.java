package com.akto.threat.backend.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

// A loader that keeps failing used to be re-run in a tight loop by the request thread, which never returned.
public class DashboardFilterCacheTest {

    @Test
    public void failingLoaderFailsTheCallAndRunsOnce() {
        DashboardFilterCache<String> cache = new DashboardFilterCache<>();
        AtomicInteger calls = new AtomicInteger();

        // preemptive, so a return of the retry loop fails this test instead of hanging the build
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            assertThrows(IllegalArgumentException.class, () -> cache.get("k", () -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("loader failed");
            })));

        assertEquals(1, calls.get());
    }

    @Test
    public void checkedLoaderFailureIsWrappedWithItsCause() {
        DashboardFilterCache<String> cache = new DashboardFilterCache<>();
        Exception cause = new Exception("checked");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> cache.get("k", () -> { throw cause; }));

        assertSame(cause, thrown.getCause());
    }

    @Test
    public void failureIsNotCachedSoTheNextCallRetries() {
        DashboardFilterCache<String> cache = new DashboardFilterCache<>();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () -> cache.get("k", () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("first fails");
        }));

        assertEquals("ok", cache.get("k", () -> { calls.incrementAndGet(); return "ok"; }));
        assertEquals(2, calls.get());
    }

    @Test
    public void successfulLoaderIsServedFromCache() {
        DashboardFilterCache<String> cache = new DashboardFilterCache<>();
        AtomicInteger calls = new AtomicInteger();

        cache.get("k", () -> { calls.incrementAndGet(); return "v"; });
        cache.get("k", () -> { calls.incrementAndGet(); return "v"; });

        assertEquals(1, calls.get());
    }

    @Test
    public void callersWaitingOnALoadShareItInsteadOfLoadingAgain() throws Exception {
        DashboardFilterCache<String> cache = new DashboardFilterCache<>();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<String> first = pool.submit(() -> cache.get("k", () -> {
                calls.incrementAndGet();
                loaderStarted.countDown();
                release.await();
                return "partial";
            }));
            loaderStarted.await();
            Future<String> second = pool.submit(() -> cache.get("k", () -> { calls.incrementAndGet(); return "other"; }));
            Future<String> third = pool.submit(() -> cache.get("k", () -> { calls.incrementAndGet(); return "other"; }));
            // let the two waiters reach the in-flight load before it finishes
            Thread.sleep(200);
            release.countDown();

            assertEquals("partial", first.get(5, TimeUnit.SECONDS));
            assertEquals("partial", second.get(5, TimeUnit.SECONDS));
            assertEquals("partial", third.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void ownTtlExpiresAValue() throws Exception {
        DashboardFilterCache<String> cache = new DashboardFilterCache<String>().withTtl(150);
        AtomicInteger calls = new AtomicInteger();

        cache.get("k", () -> { calls.incrementAndGet(); return "v"; });
        Thread.sleep(350);
        cache.get("k", () -> { calls.incrementAndGet(); return "v"; });

        assertEquals(2, calls.get());
    }

    @Test
    public void removeMakesTheNextGetLoadAgain() {
        DashboardFilterCache<String> cache = new DashboardFilterCache<>();
        AtomicInteger calls = new AtomicInteger();

        cache.get("k", () -> { calls.incrementAndGet(); return "v"; });
        cache.remove("k");
        cache.get("k", () -> { calls.incrementAndGet(); return "v"; });

        assertEquals(2, calls.get());
    }
}
