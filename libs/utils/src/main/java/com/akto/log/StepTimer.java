package com.akto.log;

/**
 * Times the steps of one request and logs a single breakdown line when it was slow.
 *
 * Usage:
 *   StepTimer t = new StepTimer("uploadFileInspectionResult");
 *   ...work...  t.mark("findExisting");
 *   ...work...  t.mark("replace");
 *   t.logIfSlow(loggerMaker, 1000, "matches=" + n);
 *
 * Logs via LoggerMaker.info (stdout only, never a DB write), so it adds no load on Mongo.
 * Not thread-safe: use one instance per request/thread.
 */
public class StepTimer {

    /** Requests at or above this total are logged by {@link #logIfSlow(LoggerMaker, String)}. */
    public static final long DEFAULT_SLOW_MS = 1000;

    private final String name;
    private final long start;
    private long last;
    private final StringBuilder steps = new StringBuilder();

    public StepTimer(String name) {
        this.name = name;
        this.start = System.currentTimeMillis();
        this.last = this.start;
    }

    /** Records the time since the previous mark (or since construction) under the given step name. */
    public void mark(String step) {
        long now = System.currentTimeMillis();
        steps.append(' ').append(step).append("Ms=").append(now - last);
        last = now;
    }

    public long totalMs() {
        return System.currentTimeMillis() - start;
    }

    public void logIfSlow(LoggerMaker loggerMaker, String extra) {
        logIfSlow(loggerMaker, DEFAULT_SLOW_MS, extra);
    }

    /** Logs "[api-timing] name totalMs=.. step1Ms=.. ... extra" when the total reached thresholdMs. */
    public void logIfSlow(LoggerMaker loggerMaker, long thresholdMs, String extra) {
        long total = totalMs();
        if (total < thresholdMs) {
            return;
        }
        loggerMaker.info("[api-timing] {} totalMs={}{}{}", name, total, steps, extra == null || extra.isEmpty() ? "" : " " + extra);
    }
}
