package com.akto.test_editor.execution;

/**
 * Per-thread accumulator for time spent hitting the API under test within a single {@code runTestNew}.
 *
 * <p>Written at the one send call site ({@link Executor} around {@code ApiExecutor.sendRequest}) — not on
 * {@code ApiExecutor.sendRequest} itself — so it captures only the test's target hits and never the other
 * callers (auth, status-code analyser, workflow nodes, etc.), and shared libraries stay untouched.
 *
 * <p>A test runs synchronously on one worker thread, so the consumer {@link #reset()}s this before
 * {@code runTestNew} and reads {@link #sendReqNanos()} after. Sends a test fans onto other threads (e.g.
 * workflow/graph nodes) go through different call sites and are not captured here.
 */
public class TestPhaseTimer {

    private static final ThreadLocal<long[]> SEND_REQ_NANOS = ThreadLocal.withInitial(() -> new long[1]);

    /** Zero this thread's accumulator (call before the unit of work you want to measure). */
    public static void reset() {
        SEND_REQ_NANOS.get()[0] = 0L;
    }

    public static void addSendRequest(long nanos) {
        if (nanos > 0) SEND_REQ_NANOS.get()[0] += nanos;
    }

    public static long sendReqNanos() {
        return SEND_REQ_NANOS.get()[0];
    }
}
