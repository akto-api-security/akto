package com.akto.test_editor.execution;

/**
 * Per-thread accumulator for the sub-phases of a single test's execution, so mini-testing can
 * break down {@code runTestNew} wall time into resolve-expression vs send-request vs validate,
 * and count how many requests a test fanned out (N).
 * <p>
 * Written at the mini-testing call sites ({@link ExecutorAlgorithm} for resolveExpression,
 * {@link Executor} for sendRequest/validate) so the shared libraries stay untouched. The test runs
 * synchronously on one worker thread, so the consumer resets this before {@code runTestNew} and reads
 * it after. Work a test fans out onto other threads (e.g. ParallelGraphExecutor) is not captured here.
 * <p>
 * Measurements are taken at the call site, not inside the (recursive) methods, so recursion does not
 * double-count — the outer nanoTime delta already contains any nested calls.
 */
public class TestPhaseTimer {

    // [0]=resolveExpression nanos, [1]=sendRequest nanos, [2]=sendRequest count, [3]=validate nanos
    private static final ThreadLocal<long[]> ACC = ThreadLocal.withInitial(() -> new long[4]);

    /** Zero this thread's accumulator (call before the unit of work you want to measure). */
    public static void reset() {
        long[] a = ACC.get();
        a[0] = a[1] = a[2] = a[3] = 0L;
    }

    public static void addResolveExpr(long nanos) { ACC.get()[0] += nanos; }
    public static void addSendRequest(long nanos) { long[] a = ACC.get(); a[1] += nanos; a[2]++; }
    public static void addValidate(long nanos)    { ACC.get()[3] += nanos; }

    public static long resolveExprNanos() { return ACC.get()[0]; }
    public static long sendReqNanos()     { return ACC.get()[1]; }
    public static long sendReqCount()     { return ACC.get()[2]; }
    public static long validateNanos()    { return ACC.get()[3]; }
}
