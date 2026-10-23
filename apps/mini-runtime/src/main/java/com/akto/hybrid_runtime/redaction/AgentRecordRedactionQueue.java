package com.akto.hybrid_runtime.redaction;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Redacts agent records on one worker thread so the consumer never waits. Unredacted ones are stored marked UNAVAILABLE, or dropped when skipWhenUnavailable is set. */
public class AgentRecordRedactionQueue {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentRecordRedactionQueue.class, LogDb.RUNTIME);

    private static final class Pending {
        final AgentQueryRecord record;
        final String method;
        final String host;
        final String path;
        final Consumer<AgentQueryRecord> sink;
        // Set once handed to the sink, so a later fallback never stores the record twice.
        boolean delivered;

        Pending(AgentQueryRecord record, String method, String host, String path, Consumer<AgentQueryRecord> sink) {
            this.record = record;
            this.method = method;
            this.host = host;
            this.path = path;
            this.sink = sink;
        }
    }

    private final GuardrailRedactionClient client;
    private final RedactionConfig config;
    private final BlockingQueue<Pending> queue;
    private final AtomicLong overflowed = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();

    public AgentRecordRedactionQueue(GuardrailRedactionClient client, RedactionConfig config) {
        this.client = client;
        this.config = config;
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity);
        Thread worker = new Thread(this::run, "agent-record-redaction");
        worker.setDaemon(true);
        worker.start();
    }

    public void submit(AgentQueryRecord record, String method, String host, String path, Consumer<AgentQueryRecord> sink) {
        if (!queue.offer(new Pending(record, method, host, path, sink))) {
            long n = overflowed.incrementAndGet();
            if (n == 1 || n % 100 == 0) {
                loggerMaker.errorAndAddToDb("Agent record redaction queue is full; " + n
                        + " record(s) stored unredacted so far");
            }
            deliverUnavailable(new Pending(record, method, host, path, sink));
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Pending first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                List<Pending> batch = new ArrayList<>();
                batch.add(first);
                long lingerUntil = System.currentTimeMillis() + config.lingerMs;
                while (batch.size() < config.chunkSize) {
                    long wait = lingerUntil - System.currentTimeMillis();
                    Pending next = wait > 0 ? queue.poll(wait, TimeUnit.MILLISECONDS) : queue.poll();
                    if (next == null) {
                        break;
                    }
                    batch.add(next);
                }
                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                loggerMaker.errorAndAddToDb("Agent record redaction worker error: " + t.getClass().getSimpleName());
            }
        }
    }

    void processBatch(List<Pending> batch) {
        try {
            storeBatch(batch);
        } catch (Throwable t) {
            loggerMaker.errorAndAddToDb("Agent record redaction failed, storing the rest of " + batch.size()
                    + " record(s) unredacted: " + t.getClass().getSimpleName());
            for (Pending p : batch) {
                if (!p.delivered) {
                    deliverUnavailable(p);
                }
            }
        }
    }

    private void deliver(Pending p, AgentQueryRecord record) {
        p.delivered = true;
        if (config.skipWhenUnavailable && RedactionResult.MARKER_UNAVAILABLE.equals(record.getPayloadRedaction())) {
            long n = skipped.incrementAndGet();
            if (n == 1 || n % 100 == 0) {
                loggerMaker.errorAndAddToDb("Guardrail redaction unavailable; " + n
                        + " agent record(s) not stored so far (skip-on-unavailable is on)");
            }
            return;
        }
        try {
            p.sink.accept(record);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Could not store agent record " + record.getSpanId() + ": " + e.getClass().getSimpleName());
        }
    }

    private void deliverUnavailable(Pending p) {
        deliver(p, p.record.withPayloads(null, null, OrigRedactor.MARKER_UNAVAILABLE));
    }

    private void storeBatch(List<Pending> batch) {
        List<RedactionItem> items = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            Pending p = batch.get(i);
            items.add(new RedactionItem(String.valueOf(i), p.method, p.host, p.path,
                    p.record.getQueryPayload(), p.record.getResponsePayload()));
        }
        Map<String, RedactionResult> results = client.redact(items, System.currentTimeMillis() + config.budgetMs);

        for (int i = 0; i < batch.size(); i++) {
            Pending p = batch.get(i);
            AgentQueryRecord toStore;
            try {
                toStore = redactedRecord(p.record, items.get(i), results.get(String.valueOf(i)));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Could not redact an agent record, storing it unredacted: " + e.getClass().getSimpleName());
                deliverUnavailable(p);
                continue;
            }
            deliver(p, toStore);
        }
    }

    private AgentQueryRecord redactedRecord(AgentQueryRecord record, RedactionItem item, RedactionResult result) {
        RedactionResult.Side q = result == null ? null : result.getRequest();
        RedactionResult.Side r = result == null ? null : result.getResponse();
        String marker = RedactionResult.markerFor(item, result);

        String query = q == null ? null : q.getPayload();
        String response = r == null ? null : r.getPayload();
        return record.withPayloads(query, response, marker);
    }
}
