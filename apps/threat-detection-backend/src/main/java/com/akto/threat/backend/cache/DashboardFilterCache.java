package com.akto.threat.backend.cache;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Short-lived in-process cache for dashboard filter queries.
 *
 * Cache key is (accountId, contextSource, dayBucket(startTs), dayBucket(endTs)) —
 * timestamps are floored to the day so that requests for the same preset sent at
 * different times of day hit the same key.
 *
 * TTL is configurable via DASHBOARD_FILTER_CACHE_TTL_SECONDS env var (default: 300s).
 * Set to 0 to disable caching entirely.
 */
public class DashboardFilterCache<V> {

    private static final LoggerMaker logger = new LoggerMaker(DashboardFilterCache.class, LogDb.THREAT_DETECTION);

    private static final long TTL_MS;
    static {
        String env = System.getenv("DASHBOARD_FILTER_CACHE_TTL_SECONDS");
        long ttlSeconds = 300;
        if (env != null) {
            try { ttlSeconds = Long.parseLong(env); } catch (NumberFormatException ignored) {}
        }
        TTL_MS = ttlSeconds * 1000L;
    }

    private static final long ONE_DAY_S = 86_400L;

    private static class Entry<V> {
        final V value;
        final long expiresAt;
        Entry(V value) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + TTL_MS;
        }
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    private final ConcurrentHashMap<String, FutureTask<Entry<V>>> store = new ConcurrentHashMap<>();

    /**
     * Floor epoch-seconds timestamp to day boundary.
     * Zero (no timestamp) is left as-is so "all time" queries share a stable key.
     */
    private static long bucketDay(long epochSeconds) {
        if (epochSeconds <= 0) return 0;
        return (epochSeconds / ONE_DAY_S) * ONE_DAY_S;
    }

    /**
     * Get from cache or compute.
     * Concurrent requests for the same key all block on one FutureTask — only one
     * MongoDB round trip happens regardless of concurrency.
     */
    public V get(String accountId, String contextSource, long startTs, long endTs, Callable<V> loader) {
        if (TTL_MS == 0) {
            try { return loader.call(); } catch (Exception e) {
                logger.error("Cache loader failed: " + e.getMessage(), e);
                return null;
            }
        }

        String key = accountId + "|" + contextSource + "|" + bucketDay(startTs) + "|" + bucketDay(endTs);

        while (true) {
            FutureTask<Entry<V>> existing = store.get(key);

            if (existing != null) {
                try {
                    Entry<V> entry = existing.get();
                    if (!entry.isExpired()) {
                        return entry.value;
                    }
                    store.remove(key, existing);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (ExecutionException e) {
                    store.remove(key, existing);
                }
            } else {
                FutureTask<Entry<V>> task = new FutureTask<>(() -> new Entry<>(loader.call()));
                if (store.putIfAbsent(key, task) == null) {
                    task.run();
                }
                // loop back to read result (ours or a concurrent winner's)
            }
        }
    }
}
