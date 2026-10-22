package com.akto.hybrid_runtime.redaction;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.elasticsearch.AgentQueryRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Redacts agent query records off the consumer thread. The consumer thread submits a record and
 * moves on; one daemon worker takes records in batches, masks their payloads through
 * guardrails-service and hands them to the sink (the data actor).
 *
 * By default a record is never dropped. If the queue is full, the service fails, a payload cannot
 * be redacted or anything unexpected happens, the record goes to the sink as it is, marked
 * payloadRedaction=UNAVAILABLE. With {@link RedactionConfig#skipWhenUnavailable} such a record is
 * instead not stored (counted and logged); a record that was redacted, even at BASIC strength, is
 * always stored.
 */
public class AgentRecordRedactionQueue {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentRecordRedactionQueue.class, LogDb.RUNTIME);

    private static final class Pending {
        final AgentQueryRecord record;
        final String method;
        final String host;
        final String path;
        final Consumer<AgentQueryRecord> sink;
        // Set once the record has been handed to the sink, so a later fallback never stores it twice.
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
    private final AtomicLong redacted = new AtomicLong();
    private final AtomicLong unavailable = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();

    public AgentRecordRedactionQueue(GuardrailRedactionClient client, RedactionConfig config) {
        this.client = client;
        this.config = config;
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity);
        Thread worker = new Thread(this::run, "agent-record-redaction");
        worker.setDaemon(true);
        worker.start();
    }

    /** Queues the record, or stores it unredacted and marked when the queue is full. */
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
                // processBatch stores its own records; this only guards the loop itself.
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

    /**
     * Hands a record to the sink once. A skipped record never reaches the sink. A sink that throws loses that record (the same as the data
     * actor failing before this feature existed) but never the rest of the batch.
     */
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
        unavailable.incrementAndGet();
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

        if (RedactionResult.MARKER_UNAVAILABLE.equals(marker)) {
            unavailable.incrementAndGet();
        } else if (query != null || response != null) {
            redacted.incrementAndGet();
        }
        // A null payload keeps the record's own, so an UNAVAILABLE side is stored as it is.
        return record.withPayloads(query, response, marker);
    }

    /** For tests and diagnostics. */
    Map<String, Long> stats() {
        Map<String, Long> m = new HashMap<>();
        m.put("overflowed", overflowed.get());
        m.put("redacted", redacted.get());
        m.put("unavailable", unavailable.get());
        m.put("skipped", skipped.get());
        return m;
    }
}
