package com.akto.hybrid_runtime;

import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.log.LoggerMaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/**
 * Counts, per API collection and hour, the messages that carried each tracked sensitive data type,
 * and periodically adds those counts to the central store.
 *
 * The only state held here is what has been counted since the last flush. The central store applies
 * each flush with $inc, so nothing needs to be loaded on startup and several runtime instances can
 * count the same collection without coordinating. A pod that restarts simply starts again from zero.
 *
 * What can be lost is bounded by one flush interval: counts not yet flushed when a pod is killed
 * without warning. A graceful shutdown flushes first, see {@link #flush()}.
 */
public class SensitiveDataCounter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SensitiveDataCounter.class, LoggerMaker.LogDb.RUNTIME);

    public static final int FLUSH_INTERVAL_SECONDS = 60;

    /** Increments per request to the store. */
    static final int BATCH_SIZE = 500;

    /**
     * Most buckets held while the store is unreachable. Failed flushes are kept and retried, but an
     * outage must not grow memory without bound; past this, increments are dropped and logged.
     */
    static final int MAX_PENDING_BUCKETS = 50_000;

    public static final SensitiveDataCounter instance = new SensitiveDataCounter();

    private Map<Bucket, Long> pending = new HashMap<>();
    private long droppedIncrements = 0;

    private ScheduledExecutorService flusher;

    /** Writes one batch of increments, under an idempotency key, to the store; false if the write failed. */
    private volatile BiPredicate<String, List<SensitiveDataCount>> writer;

    SensitiveDataCounter() {
    }

    public synchronized void start(BiPredicate<String, List<SensitiveDataCount>> writer) {
        if (flusher != null) return;
        this.writer = writer;
        flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensitive-data-counter-flush");
            t.setDaemon(true);
            return t;
        });
        flusher.scheduleWithFixedDelay(this::flushQuietly, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Counts one message that carried these types. */
    public void record(int apiCollectionId, Set<String> types, int messageTimestamp) {
        if (types == null || types.isEmpty()) return;
        int hour = SensitiveDataCount.hourOf(messageTimestamp);
        synchronized (this) {
            for (String type : types) {
                add(new Bucket(apiCollectionId, type, hour), 1L);
            }
        }
    }

    /** Must be called holding the lock. */
    private void add(Bucket bucket, long count) {
        Long current = pending.get(bucket);
        if (current == null && pending.size() >= MAX_PENDING_BUCKETS) {
            droppedIncrements += count;
            return;
        }
        pending.put(bucket, current == null ? count : current + count);
    }

    private synchronized Map<Bucket, Long> takePending() {
        Map<Bucket, Long> taken = pending;
        pending = new HashMap<>();
        return taken;
    }

    /** Puts back a batch that could not be written, so the next flush retries it. */
    private synchronized void putBack(Map<Bucket, Long> unwritten) {
        for (Map.Entry<Bucket, Long> e : unwritten.entrySet()) {
            add(e.getKey(), e.getValue());
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "sensitive data counter flush failed: " + e.getMessage());
        }
    }

    /**
     * Writes everything counted so far. Safe to call from a shutdown hook; anything that fails to
     * write is kept for the next attempt.
     */
    public void flush() {
        BiPredicate<String, List<SensitiveDataCount>> writer = this.writer;
        if (writer == null) return;

        long dropped;
        synchronized (this) {
            dropped = droppedIncrements;
            droppedIncrements = 0;
        }
        if (dropped > 0) {
            loggerMaker.errorAndAddToDb("sensitive data counter dropped " + dropped
                    + " increments: more than " + MAX_PENDING_BUCKETS + " buckets were waiting to be written");
        }

        Map<Bucket, Long> taken = takePending();
        if (taken.isEmpty()) return;

        List<Map.Entry<Bucket, Long>> entries = new ArrayList<>(taken.entrySet());
        Map<Bucket, Long> unwritten = new HashMap<>();
        for (int from = 0; from < entries.size(); from += BATCH_SIZE) {
            List<Map.Entry<Bucket, Long>> chunk = entries.subList(from, Math.min(from + BATCH_SIZE, entries.size()));
            List<SensitiveDataCount> increments = new ArrayList<>(chunk.size());
            for (Map.Entry<Bucket, Long> e : chunk) {
                Bucket b = e.getKey();
                increments.add(new SensitiveDataCount(b.apiCollectionId, b.subType, b.hour, e.getValue()));
            }

            boolean written;
            try {
                written = writer.test(UUID.randomUUID().toString(), increments);
            } catch (Exception e) {
                written = false;
            }
            if (!written) {
                for (Map.Entry<Bucket, Long> e : chunk) unwritten.put(e.getKey(), e.getValue());
            }
        }

        if (!unwritten.isEmpty()) {
            putBack(unwritten);
            loggerMaker.errorAndAddToDb("sensitive data counter could not write " + unwritten.size() + " of "
                    + taken.size() + " buckets, will retry on the next flush");
        } else {
            loggerMaker.info("sensitive data counter flushed " + taken.size() + " buckets");
        }
    }

    synchronized Map<Bucket, Long> snapshot() {
        return new HashMap<>(pending);
    }

    void setWriter(BiPredicate<String, List<SensitiveDataCount>> writer) {
        this.writer = writer;
    }

    static final class Bucket {
        final int apiCollectionId;
        final String subType;
        final int hour;

        Bucket(int apiCollectionId, String subType, int hour) {
            this.apiCollectionId = apiCollectionId;
            this.subType = subType;
            this.hour = hour;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Bucket)) return false;
            Bucket b = (Bucket) o;
            return apiCollectionId == b.apiCollectionId && hour == b.hour && subType.equals(b.subType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiCollectionId, subType, hour);
        }
    }
}
