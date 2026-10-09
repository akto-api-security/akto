package com.akto.hybrid_runtime;

import com.akto.dto.traffic.SensitiveDataCount;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SensitiveDataCounterTest {

    private static final int HOUR = 1791536400;

    /** Sums everything written, keyed the way the store would key it. */
    private static Map<String, Long> totals(List<List<SensitiveDataCount>> batches) {
        Map<String, Long> totals = new HashMap<>();
        for (List<SensitiveDataCount> batch : batches) {
            for (SensitiveDataCount c : batch) {
                totals.merge(c.getApiCollectionId() + "|" + c.getSubType() + "|" + c.getHour(), c.getCount(), Long::sum);
            }
        }
        return totals;
    }

    @Test
    public void countsMessagesPerCollectionTypeAndHour() {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        List<List<SensitiveDataCount>> written = new ArrayList<>();
        counter.setWriter((key, batch) -> written.add(new ArrayList<>(batch)));

        counter.record(1, new HashSet<>(Arrays.asList("EMAIL_X", "CARD_X")), HOUR + 10);
        counter.record(1, Collections.singleton("EMAIL_X"), HOUR + 3599);
        counter.record(1, Collections.singleton("EMAIL_X"), HOUR + 3600); // next hour
        counter.record(2, Collections.singleton("EMAIL_X"), HOUR + 20);   // other collection
        counter.record(3, Collections.<String>emptySet(), HOUR);           // nothing tracked

        counter.flush();

        Map<String, Long> totals = totals(written);
        assertEquals(Long.valueOf(2), totals.get("1|EMAIL_X|" + HOUR));
        assertEquals(Long.valueOf(1), totals.get("1|CARD_X|" + HOUR));
        assertEquals(Long.valueOf(1), totals.get("1|EMAIL_X|" + (HOUR + 3600)));
        assertEquals(Long.valueOf(1), totals.get("2|EMAIL_X|" + HOUR));
        assertEquals(4, totals.size());
        assertTrue("a successful flush leaves nothing pending", counter.snapshot().isEmpty());
    }

    @Test
    public void failedWriteIsRetriedOnTheNextFlushWithoutLosingOrDoublingCounts() {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        List<List<SensitiveDataCount>> written = new ArrayList<>();
        boolean[] storeUp = {false};
        counter.setWriter((key, batch) -> {
            if (!storeUp[0]) return false;
            written.add(new ArrayList<>(batch));
            return true;
        });

        counter.record(1, Collections.singleton("EMAIL_X"), HOUR);
        counter.flush();
        assertTrue(written.isEmpty());

        counter.record(1, Collections.singleton("EMAIL_X"), HOUR);
        storeUp[0] = true;
        counter.flush();

        assertEquals(Collections.singletonMap("1|EMAIL_X|" + HOUR, 2L), totals(written));
        assertTrue(counter.snapshot().isEmpty());
    }

    @Test
    public void writesInBatches() {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        List<List<SensitiveDataCount>> written = new ArrayList<>();
        counter.setWriter((key, batch) -> written.add(new ArrayList<>(batch)));

        int buckets = SensitiveDataCounter.BATCH_SIZE * 2 + 1;
        for (int i = 0; i < buckets; i++) {
            counter.record(i, Collections.singleton("EMAIL_X"), HOUR);
        }
        counter.flush();

        assertEquals(3, written.size());
        assertEquals(buckets, totals(written).size());
    }

    @Test
    public void stopsHoldingNewBucketsPastTheCapButKeepsCountingExistingOnes() {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        counter.setWriter((key, batch) -> false); // store unreachable

        for (int i = 0; i < SensitiveDataCounter.MAX_PENDING_BUCKETS; i++) {
            counter.record(i, Collections.singleton("EMAIL_X"), HOUR);
        }
        counter.record(SensitiveDataCounter.MAX_PENDING_BUCKETS, Collections.singleton("EMAIL_X"), HOUR);
        counter.record(0, Collections.singleton("EMAIL_X"), HOUR);

        Map<SensitiveDataCounter.Bucket, Long> pending = counter.snapshot();
        assertEquals(SensitiveDataCounter.MAX_PENDING_BUCKETS, pending.size());
        assertEquals(Long.valueOf(2), pending.get(new SensitiveDataCounter.Bucket(0, "EMAIL_X", HOUR)));
    }

    @Test
    public void concurrentRecordingDuringFlushesLosesAndDoublesNothing() throws Exception {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        List<List<SensitiveDataCount>> written = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        // every third write fails, so put-back runs concurrently with recording too
        counter.setWriter((key, batch) -> {
            if (calls.incrementAndGet() % 3 == 0) return false;
            written.add(new ArrayList<>(batch));
            return true;
        });

        int threads = 8, perThread = 20_000;
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread flusher = new Thread(() -> { while (!done.get()) counter.flush(); });
        flusher.start();
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int collection = t % 3;
            Thread w = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    counter.record(collection, Collections.singleton("EMAIL_X"), HOUR + (i % 2) * 3600);
                }
            });
            workers.add(w);
            w.start();
        }
        for (Thread w : workers) w.join();
        done.set(true);
        flusher.join();
        counter.setWriter((key, batch) -> written.add(new ArrayList<>(batch)));
        counter.flush();

        long total = 0;
        for (long v : totals(written).values()) total += v;
        assertEquals((long) threads * perThread, total);
        assertTrue(counter.snapshot().isEmpty());
    }

    @Test
    public void eachBatchGetsItsOwnIdempotencyKey() {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        List<String> keys = new ArrayList<>();
        counter.setWriter((key, batch) -> keys.add(key));
        for (int i = 0; i < SensitiveDataCounter.BATCH_SIZE + 1; i++) {
            counter.record(i, Collections.singleton("EMAIL_X"), HOUR);
        }
        counter.flush();
        assertEquals(2, keys.size());
        assertEquals(2, new java.util.HashSet<>(keys).size());
        assertTrue(keys.get(0) != null && !keys.get(0).isEmpty());
    }

    @Test
    public void flushWithoutAWriterKeepsCounts() {
        SensitiveDataCounter counter = new SensitiveDataCounter();
        counter.record(1, Collections.singleton("EMAIL_X"), HOUR);
        counter.flush();
        assertEquals(1, counter.snapshot().size());
    }
}
