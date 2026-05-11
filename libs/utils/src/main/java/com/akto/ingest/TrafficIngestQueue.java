package com.akto.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Singleton in-memory queue that decouples traffic producers (Kafka, HTTP API)
 * from the mini-runtime processing pipeline.
 *
 * Capacity: TRAFFIC_INGEST_QUEUE_CAPACITY env var (default 10,000).
 * Callers should check the return value of {@link #offer} and handle back-pressure.
 */
public final class TrafficIngestQueue {

    public static final int DEFAULT_CAPACITY = 10_000;

    private static volatile TrafficIngestQueue instance;

    private final LinkedBlockingQueue<String> queue;

    private TrafficIngestQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public static TrafficIngestQueue getInstance() {
        if (instance == null) {
            synchronized (TrafficIngestQueue.class) {
                if (instance == null) {
                    int capacity = DEFAULT_CAPACITY;
                    String env = System.getenv("TRAFFIC_INGEST_QUEUE_CAPACITY");
                    if (env != null) {
                        try { capacity = Integer.parseInt(env.trim()); } catch (NumberFormatException ignored) {}
                    }
                    instance = new TrafficIngestQueue(capacity);
                }
            }
        }
        return instance;
    }

    /**
     * Non-blocking enqueue. Returns false if the queue is full (back-pressure signal).
     */
    public boolean offer(String message) {
        return queue.offer(message);
    }

    /**
     * Blocks up to {@code timeoutMs} for the first message, then drains up to
     * {@code maxBatch} total messages without further blocking.
     * Returns an empty list if no messages arrive within the timeout.
     */
    public List<String> drainBatch(int maxBatch, long timeoutMs) throws InterruptedException {
        List<String> batch = new ArrayList<>(maxBatch);
        String first = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (first != null) {
            batch.add(first);
            queue.drainTo(batch, maxBatch - 1);
        }
        return batch;
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
