package com.akto.test_editor.execution;

/**
 * Per-thread accumulator for time spent in named sub-phases of a single {@code runTestNew}.
 *
 * <p>Written at the exact call sites confirmed against captured flamegraphs from the perf
 * investigation (see git history around this class): SEND_REQUEST wraps {@code ApiExecutor.sendRequest}
 * in {@link Executor#execute}; FILTER wraps {@code TestPlugin.validateFilter} in
 * {@code YamlTestTemplate.filter()}; WORDLIST wraps {@code VariableResolver.resolveDynamicWordList}
 * in {@link Executor#execute}; VALIDATE wraps {@link Executor#validate}. Together with RUN_TEST's
 * wall clock and INSERT_RESULTS, these let {@code TestRunMetrics}'s previously-undivided OTHER
 * bucket (RUN_TEST minus SEND_REQUEST) be split into named, always-on measurements instead of a
 * subtraction that only a live profiler could explain.
 *
 * <p>A test runs synchronously on one worker thread, so the consumer {@link #reset()}s this before
 * {@code runTestNew} and reads the accumulators after. A test can pass through FILTER/WORDLIST/VALIDATE
 * multiple times within one {@code runTestNew} (e.g. one WORDLIST resolve + N send+validate iterations
 * over attack payloads); each add() call accumulates, matching how SEND_REQUEST already behaves.
 * Sends a test fans onto other threads (e.g. workflow/graph nodes) go through different call sites
 * and are not captured here.
 */
public class TestPhaseTimer {

    private static final ThreadLocal<long[]> NANOS = ThreadLocal.withInitial(() -> new long[4]);
    private static final int SEND_REQUEST = 0;
    private static final int FILTER = 1;
    private static final int WORDLIST = 2;
    private static final int VALIDATE = 3;

    /** Zero this thread's accumulators (call before the unit of work you want to measure). */
    public static void reset() {
        long[] a = NANOS.get();
        for (int i = 0; i < a.length; i++) a[i] = 0L;
    }

    public static void addSendRequest(long nanos) { add(SEND_REQUEST, nanos); }
    public static void addFilter(long nanos)      { add(FILTER, nanos); }
    public static void addWordlist(long nanos)    { add(WORDLIST, nanos); }
    public static void addValidate(long nanos)    { add(VALIDATE, nanos); }

    public static long sendReqNanos()  { return NANOS.get()[SEND_REQUEST]; }
    public static long filterNanos()   { return NANOS.get()[FILTER]; }
    public static long wordlistNanos() { return NANOS.get()[WORDLIST]; }
    public static long validateNanos() { return NANOS.get()[VALIDATE]; }

    private static void add(int idx, long nanos) {
        if (nanos > 0) NANOS.get()[idx] += nanos;
    }
}
