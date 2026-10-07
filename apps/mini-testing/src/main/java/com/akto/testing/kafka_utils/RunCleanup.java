package com.akto.testing.kafka_utils;

import java.util.concurrent.ExecutorService;

import com.akto.testing.kafka_utils.TestRunMetrics.StopReason;

/**
 * The tail of a consumer pass, as a deterministic function of {@link StopReason}. Which resources
 * are torn down how, whether the run's state/topic are discarded, and whether the process exits are
 * all derived here and nowhere else.
 */
final class RunCleanup {

    /** How the run's durable state is treated after this pass. */
    enum StateAction {
        /** Topic deleted, state file cleared: the run is over. */
        DISCARD,
        /** Topic and state file left intact so a restarted process resumes the same run. */
        KEEP_FOR_RESUME
    }

    /** Process-exit seam; production passes {@code code -> System.exit(code)}. */
    @FunctionalInterface
    interface ExitHandler {
        void exit(int code);
    }

    static boolean isAbrupt(StopReason reason) {
        return reason == StopReason.ERROR
                || reason == StopReason.CONSUMER_FAILED
                || reason == StopReason.STALLED;
    }

    /** Only reasons where a restart can legitimately pick the run back up keep its state. */
    static StateAction stateActionFor(StopReason reason) {
        return (reason == StopReason.CONSUMER_FAILED || reason == StopReason.STALLED)
                ? StateAction.KEEP_FOR_RESUME
                : StateAction.DISCARD;
    }

    /** Executor grace before the pool is forcibly reaped. */
    static int executorWaitSeconds(StopReason reason) {
        return isAbrupt(reason) ? 5 : 30;
    }

    /** Pool teardown; production is {@code TestingExecutorLifecycle::shutdownQuietly}. */
    @FunctionalInterface
    interface ExecutorShutdown {
        void shutdown(ExecutorService executor, int waitSeconds, boolean force);
    }

    /** Closes the parallel consumer and the underlying KafkaConsumer; {@code abrupt} skips draining. */
    @FunctionalInterface
    interface ConsumerCloser {
        void close(boolean abrupt);
    }

    private final ExecutorShutdown executorShutdown;
    private final Runnable flushLastTested;
    private final ConsumerCloser consumerCloser;
    private final Runnable discardRunState;
    private final ExitHandler exitHandler;

    /**
     * @param flushLastTested  bulk-writes lastTested for every API touched this pass
     * @param discardRunState  deletes the results topic and clears the state file
     * @param exitHandler      terminates the process when the run must be resumed by a restart
     */
    RunCleanup(ExecutorShutdown executorShutdown,
               Runnable flushLastTested,
               ConsumerCloser consumerCloser,
               Runnable discardRunState,
               ExitHandler exitHandler) {
        this.executorShutdown = executorShutdown;
        this.flushLastTested = flushLastTested;
        this.consumerCloser = consumerCloser;
        this.discardRunState = discardRunState;
        this.exitHandler = exitHandler;
    }

    void run(StopReason reason, ExecutorService executor) {
        boolean abrupt = isAbrupt(reason);
        flushLastTested.run();
        executorShutdown.shutdown(executor, executorWaitSeconds(reason), abrupt);
        consumerCloser.close(abrupt);
        if (stateActionFor(reason) == StateAction.KEEP_FOR_RESUME) {
            // state file/topic left intact for restart to resume
            exitHandler.exit(1);
            return;
        }
        discardRunState.run();
    }
}
