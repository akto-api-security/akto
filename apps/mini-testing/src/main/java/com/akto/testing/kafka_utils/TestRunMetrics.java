package com.akto.testing.kafka_utils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;

/**
 * All observability for a single testing-run consumer pass: outcome counters, per-test timing,
 * the in-flight task registry, and every WARN-level {@code TESTRUN …} lifecycle line.
 * <p>
 * One instance per run (constructed in {@link ConsumerUtil#init(int)}), so "reset" is just a fresh
 * object. Counters/timing/registry are thread-safe (touched by every worker thread); the scheduling
 * fields used by {@link #tick(int, long)} are only ever read/written from the single drain-loop thread.
 * <p>
 * Keeping this out of {@link ConsumerUtil} leaves that class to the Kafka / threadpool / completion
 * business logic, and keeps all log formatting in exactly one place so it can never drift.
 */
public class TestRunMetrics {

    /** Why a consumer pass ended, reported as {@code reason=} on the TESTRUN END/RESTART lines. */
    public enum StopReason {
        /** Loop never reached a terminal branch (shouldn't happen in practice). */
        UNKNOWN,
        /** Test run was marked stopped by the user / dashboard. */
        STOPPED,
        /** effectiveMaxRunTime elapsed. */
        MAX_RUNTIME,
        /** All expected records processed and the queue drained. */
        ALL_PROCESSED,
        /** Queue drained but records still missing after the idle grace -> consumer restarted from last commit. */
        IDLE_RESTART,
        /** Queue drained, idle grace elapsed, nothing left to recover -> completed. */
        IDLE_COMPLETE,
        /** Polling/processing threw. */
        ERROR
    }

    /**
     * Pipeline stages timed per test, so a whole run can be billed by where wall time goes.
     * RUN_TEST is the full {@code runTestNew} wall; TARGET_HTTP is the slice of it spent blocked on the
     * API under test (from {@link com.akto.testing.ApiExecutor}); RUN_TEST minus TARGET_HTTP is compute.
     * PERSIST_LOGS + INSERT_RESULTS are the ultron persistence round-trips ("run away from ultron" cost).
     */
    public enum Stage { LOOKUP, RUN_TEST, TARGET_HTTP, PERSIST_LOGS, INSERT_RESULTS }

    /** WARN-level progress heartbeat cadence (M*N run-wide progress). */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    /** No increase in processed records for this long (while work is still in-flight) => log a stall dump. */
    private static final long STALL_THRESHOLD_MS = 60_000L;
    /** Don't spam stall dumps more often than this. */
    private static final long STALL_LOG_INTERVAL_MS = 30_000L;
    /** Max number of oldest in-flight tasks to list in a stall dump. */
    private static final int STALL_DUMP_LIMIT = 15;
    /** A slot is "stuck" if a single task has held it at least this long (live capacity-waste signal). */
    private static final long STUCK_AGE_MS = 60_000L;

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestRunMetrics.class, LogDb.TESTING);

    private static final class InflightTask {
        final String label;      // "subcategory apiInfoKey"
        final long startMs;
        final String threadName;
        InflightTask(String label, long startMs, String threadName) {
            this.label = label;
            this.startMs = startMs;
            this.threadName = threadName;
        }
    }

    // Run identity/context.
    private final String summaryId;
    private final int startTime;        // Context epoch seconds
    private final int expectedRecords;
    private final ExecutorService executor;

    // Outcome counters.
    private final AtomicInteger polled = new AtomicInteger(0);
    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger vulnerable = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final AtomicInteger timedOut = new AtomicInteger(0);
    private final AtomicInteger rejected = new AtomicInteger(0);
    private final AtomicInteger errored = new AtomicInteger(0);

    // Per-test timing aggregates (submit -> finish, includes queue wait).
    private final AtomicInteger durationSamples = new AtomicInteger(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private final AtomicLong maxDurationMs = new AtomicLong(0);
    private volatile String slowestLabel = "";

    // Per-stage wall-time bill (nanos summed across all worker threads) + call counts + worst single call.
    private final EnumMap<Stage, LongAdder> stageNanos = newStageAdders();
    private final EnumMap<Stage, LongAdder> stageCount = newStageAdders();
    private final EnumMap<Stage, AtomicLong> stageMaxNanos = newStageMax();
    /** CPU time (not wall) attributed to RUN_TEST, to separate compute from I/O wait. */
    private final LongAdder runTestCpuNanos = new LongAdder();

    private static EnumMap<Stage, LongAdder> newStageAdders() {
        EnumMap<Stage, LongAdder> m = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) m.put(s, new LongAdder());
        return m;
    }

    private static EnumMap<Stage, AtomicLong> newStageMax() {
        EnumMap<Stage, AtomicLong> m = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) m.put(s, new AtomicLong(0));
        return m;
    }

    /** recordId -> what is currently running, so a stall can name the stuck (api, test) cells and threads. */
    private final ConcurrentHashMap<String, InflightTask> inflightTasks = new ConcurrentHashMap<>();

    // Scheduling state for tick() — only touched by the drain-loop thread.
    private long lastHeartbeatMs = 0L;
    private long lastProgressMs = System.currentTimeMillis();
    private long lastStallLogMs = 0L;
    private int lastProcessedSeen = -1;

    public TestRunMetrics(String summaryId, int startTime, int expectedRecords, ExecutorService executor) {
        this.summaryId = summaryId;
        this.startTime = startTime;
        this.expectedRecords = expectedRecords;
        this.executor = executor;
    }

    // ----- task lifecycle (called from the poll callback / runTestFromMessage on worker threads) -----

    /** A record was pulled off the topic (counted even if we later skip submitting it). */
    public void onPolled() {
        polled.incrementAndGet();
    }

    /** A task was submitted to the executor; start tracking it as in-flight. */
    public void onSubmit(String recordId, String label, String threadName) {
        inflightTasks.put(recordId, new InflightTask(label, System.currentTimeMillis(), threadName));
    }

    /** A task finished (any outcome); stop tracking it and fold its wall-clock into the timing aggregates. */
    public void onComplete(String recordId) {
        InflightTask task = inflightTasks.remove(recordId);
        if (task == null) {
            return;
        }
        long dur = System.currentTimeMillis() - task.startMs;
        totalDurationMs.addAndGet(dur);
        durationSamples.incrementAndGet();
        long prevMax;
        while (dur > (prevMax = maxDurationMs.get())) {
            if (maxDurationMs.compareAndSet(prevMax, dur)) {
                slowestLabel = task.label; // best-effort; paired with the max we just set
                break;
            }
        }
    }

    public void markPassed()     { passed.incrementAndGet(); }
    public void markVulnerable() { vulnerable.incrementAndGet(); }
    public void markSkipped()    { skipped.incrementAndGet(); }
    public void markTimedOut()   { timedOut.incrementAndGet(); }
    public void markRejected()   { rejected.incrementAndGet(); }
    public void markErrored()    { errored.incrementAndGet(); }

    public int polled() { return polled.get(); }

    /** Fold one stage's wall-clock (nanos) for one test into the run-wide bill. Thread-safe. */
    public void recordStage(Stage stage, long nanos) {
        if (nanos < 0) return;
        stageNanos.get(stage).add(nanos);
        stageCount.get(stage).increment();
        AtomicLong max = stageMaxNanos.get(stage);
        long prev;
        while (nanos > (prev = max.get()) && !max.compareAndSet(prev, nanos)) { /* retry */ }
    }

    /** Fold the CPU time (nanos) a single {@code runTestNew} burned, to split compute from I/O wait. */
    public void recordRunTestCpu(long nanos) {
        if (nanos > 0) runTestCpuNanos.add(nanos);
    }

    // ----- lifecycle banners -----

    public void logStart(int accountId, int apiCount, int testCount, int poolSize,
                         int perTestTimeoutSec, int maxRunTimeSec) {
        long totalCells = (apiCount > 0 && testCount > 0) ? (long) apiCount * testCount : -1L;
        loggerMaker.warnAndAddToDb("TESTRUN START summaryId=" + summaryId
                + " account=" + accountId
                + " apis(M)=" + apiCount + " tests(N)=" + testCount + " totalCells(MxN)=" + totalCells
                + " expectedRecords=" + expectedRecords
                + " executorPoolSize=" + poolSize
                + " pcMaxConcurrency=" + poolSize
                + " perTestTimeout=" + perTestTimeoutSec + "s"
                + " maxRunTime=" + maxRunTimeSec + "s");
    }

    public void logConsumerUp(int attempt) {
        loggerMaker.warnAndAddToDb("TESTRUN CONSUMER-UP summaryId=" + summaryId + " attempt=" + attempt);
    }

    public void logRestart(long idleMs, int processed, long workRemaining) {
        loggerMaker.warnAndAddToDb("TESTRUN RESTART summaryId=" + summaryId
                + " reason=idle_incomplete idleMs=" + idleMs + " "
                + runStats(processed, workRemaining));
    }

    public void logEnd(StopReason reason, boolean failed, int processed) {
        int accountedFor = passed.get() + vulnerable.get() + skipped.get()
                + timedOut.get() + rejected.get() + errored.get();
        loggerMaker.warnAndAddToDb("TESTRUN END summaryId=" + summaryId
                + " reason=" + reason
                + " durationSec=" + (Context.now() - startTime)
                + " failed=" + failed
                + " " + runStats(processed, -1)
                + " accountedFor=" + accountedFor
                + " slowest=\"" + slowestLabel + "\"");
        logCostBreakdown();
    }

    // ----- periodic tick from the drain loop (decides internally when to emit) -----

    /** Call once per drain-loop iteration. Publishes the queue gauge and emits heartbeat/stall on cadence. */
    public void tick(int processed, long workRemaining) {
        AllMetrics.instance.setTestingKafkaQueuePending(workRemaining);
        long nowMs = System.currentTimeMillis();

        if (processed > lastProcessedSeen) {
            lastProcessedSeen = processed;
            lastProgressMs = nowMs;
        }

        if (nowMs - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
            lastHeartbeatMs = nowMs;
            logProgressHeartbeat(processed, workRemaining);
            logCostBreakdown();
        }

        // Two independent stall triggers, sharing the spam guard + dump:
        //   1. progress frozen  -> aggregate processed hasn't moved for STALL_THRESHOLD_MS (total hang)
        //   2. slots clogged    -> half+ the pool is tied up in tasks older than STUCK_AGE_MS (partial starvation)
        int pool = poolSize();
        long stuck = inflightAgeStats()[0];
        boolean progressFrozen = !inflightTasks.isEmpty() && (nowMs - lastProgressMs) >= STALL_THRESHOLD_MS;
        boolean slotsClogged = pool > 0 && stuck >= (pool / 2);
        if ((progressFrozen || slotsClogged) && (nowMs - lastStallLogMs) >= STALL_LOG_INTERVAL_MS) {
            lastStallLogMs = nowMs;
            String reason = progressFrozen
                    ? "progress_frozen_" + ((nowMs - lastProgressMs) / 1000) + "s"
                    : "slots_clogged_" + stuck + "/" + pool;
            logStall(reason, processed, workRemaining);
        }
    }

    // ----- formatting (single source of truth) -----

    /** Single source of truth for thread-pool saturation: active/pool + queued + completed. */
    private String executorStats() {
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
            return "active=" + tpe.getActiveCount() + "/" + tpe.getPoolSize()
                    + " queued=" + tpe.getQueue().size()
                    + " completed=" + tpe.getCompletedTaskCount();
        }
        return "n/a";
    }

    /** Configured max thread-pool size, or -1 if the executor isn't a ThreadPoolExecutor. */
    private int poolSize() {
        return (executor instanceof ThreadPoolExecutor) ? ((ThreadPoolExecutor) executor).getMaximumPoolSize() : -1;
    }

    /**
     * Single pass over the in-flight registry: [count of slots held >= STUCK_AGE_MS, oldest age in ms].
     * This is the live capacity-waste signal — how much of the pool is tied up in slow/hung tests right now.
     */
    private long[] inflightAgeStats() {
        long now = System.currentTimeMillis();
        long stuck = 0, oldest = 0;
        for (InflightTask t : inflightTasks.values()) {
            long age = now - t.startMs;
            if (age >= STUCK_AGE_MS) stuck++;
            if (age > oldest) oldest = age;
        }
        return new long[]{stuck, oldest};
    }

    /** Canonical run snapshot rendered by every TESTRUN lifecycle line (PROGRESS/STALL/RESTART/END). */
    private String runStats(int processed, long workRemaining) {
        int pct = expectedRecords > 0 ? (int) ((processed * 100L) / expectedRecords) : -1;
        long[] age = inflightAgeStats();
        return "done=" + processed + "/" + expectedRecords + (pct >= 0 ? " (" + pct + "%)" : "")
                + " pass=" + passed.get() + " vuln=" + vulnerable.get()
                + " skip=" + skipped.get() + " timeout=" + timedOut.get()
                + " rejected=" + rejected.get() + " err=" + errored.get()
                + " polled=" + polled.get()
                + " workRemaining=" + workRemaining
                + " inflight=" + inflightTasks.size()
                + " stuckSlots=" + age[0]
                + " oldestInflightMs=" + age[1]
                + " avgTestMs=" + (durationSamples.get() > 0 ? (totalDurationMs.get() / durationSamples.get()) : -1)
                + " maxTestMs=" + maxDurationMs.get()
                + " executor[" + executorStats() + "]";
    }

    /** WARN-level run-wide progress line: how far along the M*N matrix is, throughput, ETA and pool saturation. */
    private void logProgressHeartbeat(int processed, long workRemaining) {
        int elapsed = Math.max(1, Context.now() - startTime);
        double rate = processed / (double) elapsed;
        int remaining = expectedRecords > 0 ? Math.max(0, expectedRecords - processed) : -1;
        long eta = (remaining >= 0 && rate > 0.01) ? (long) (remaining / rate) : -1;

        loggerMaker.warnAndAddToDb("TESTRUN PROGRESS summaryId=" + summaryId
                + " elapsed=" + elapsed + "s"
                + " rate=" + String.format("%.1f", rate) + "/s"
                + (eta >= 0 ? " eta~" + eta + "s" : "")
                + " " + runStats(processed, workRemaining));
    }

    /**
     * WARN-level cost breakdown: of all per-test wall time measured so far, how much went to each stage.
     * Percentages are of the summed stage wall time (not run wall time — stages overlap across threads).
     * The single line that answers "what is taking so long": target-HTTP vs ultron vs compute.
     */
    private void logCostBreakdown() {
        long n = stageCount.get(Stage.RUN_TEST).sum();
        if (n <= 0) return; // nothing measured yet

        long lookup   = stageNanos.get(Stage.LOOKUP).sum();
        long runTest  = stageNanos.get(Stage.RUN_TEST).sum();
        long target   = stageNanos.get(Stage.TARGET_HTTP).sum();
        long persist  = stageNanos.get(Stage.PERSIST_LOGS).sum();
        long insertRt = stageNanos.get(Stage.INSERT_RESULTS).sum();
        long runCpu   = runTestCpuNanos.sum();
        long compute  = Math.max(0, runTest - target);      // runTestNew wall not spent blocked on the target API
        long ultron   = persist + insertRt;                  // persistence round-trips
        long billed   = lookup + runTest + ultron;           // total measured wall (RUN_TEST already includes target)

        long avgWallMs = ms(runTest + lookup + ultron) / n;
        loggerMaker.warnAndAddToDb("TESTRUN COST summaryId=" + summaryId
                + " n=" + n
                + " avgPerTestMs=" + avgWallMs
                + " | LOOKUP=" + pctOf(lookup, billed) + "(" + msPer(lookup, n) + "ms)"
                + " RUN_TEST=" + pctOf(runTest, billed) + "(" + msPer(runTest, n) + "ms)"
                + " [TARGET_HTTP=" + pctOf(target, billed) + "(" + msPer(target, n) + "ms, calls/test=" + per(stageCount.get(Stage.TARGET_HTTP).sum(), n) + ")"
                + " COMPUTE=" + pctOf(compute, billed) + "(" + msPer(compute, n) + "ms, cpu/test=" + msPer(runCpu, n) + "ms)]"
                + " PERSIST_LOGS=" + pctOf(persist, billed) + "(" + msPer(persist, n) + "ms)"
                + " INSERT_RESULTS=" + pctOf(insertRt, billed) + "(" + msPer(insertRt, n) + "ms)"
                + " ULTRON_TOTAL=" + pctOf(ultron, billed) + "(" + msPer(ultron, n) + "ms)");
    }

    private static long ms(long nanos) { return nanos / 1_000_000L; }
    private static long msPer(long nanos, long n) { return n > 0 ? (nanos / n) / 1_000_000L : 0; }
    private static String per(long v, long n) { return n > 0 ? String.format("%.1f", v / (double) n) : "0"; }
    private static String pctOf(long part, long whole) {
        return whole > 0 ? (part * 100L / whole) + "%" : "0%";
    }

    /** WARN-level dump of the oldest in-flight tasks, fired when the run stalls or the pool clogs. */
    private void logStall(String reason, int processed, long workRemaining) {
        List<InflightTask> tasks = new ArrayList<>(inflightTasks.values());
        tasks.sort((a, b) -> Long.compare(a.startMs, b.startMs)); // oldest first
        long nowMs = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("TESTRUN STALL summaryId=").append(summaryId)
                .append(" reason=").append(reason).append(" ")
                .append(runStats(processed, workRemaining))
                .append(" oldestTasks=[");

        int limit = Math.min(STALL_DUMP_LIMIT, tasks.size());
        for (int i = 0; i < limit; i++) {
            InflightTask t = tasks.get(i);
            sb.append("{age=").append((nowMs - t.startMs) / 1000).append("s ")
                    .append(t.label).append(" thread=").append(t.threadName).append("}");
            if (i < limit - 1) sb.append(", ");
        }
        sb.append("]");
        if (tasks.size() > limit) sb.append(" (+").append(tasks.size() - limit).append(" more)");

        loggerMaker.warnAndAddToDb(sb.toString());
    }
}
