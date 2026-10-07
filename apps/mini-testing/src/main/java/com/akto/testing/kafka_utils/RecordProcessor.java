package com.akto.testing.kafka_utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one Kafka record's test on the worker pool with a hard wall-clock timeout and reports what
 * happened as an {@link Outcome}. This is the body of the parallel-consumer poll callback with the
 * logging, metrics and timed-out-result bookkeeping pulled out: the caller maps the outcome to those
 * side effects, so this class can be exercised with a {@link TestRunner} that sleeps or throws.
 *
 * <p>Note {@code executor.submit} is inside the guarded region: on the previous inline version the
 * {@link RejectedExecutionException} handler was dead code because submit threw before the try.
 */
final class RecordProcessor {

    /** Result of one record, in the order the caller usually cares about it. */
    enum Outcome {
        /** The runner returned normally. */
        COMPLETED,
        /** The runner did not return within the timeout; the task was cancelled. */
        TIMED_OUT,
        /** The waiting (poll) thread was interrupted; the task was cancelled. */
        INTERRUPTED,
        /** The executor refused the task (shut down or saturated). */
        REJECTED,
        /** The runner threw, or waiting on it failed for any other reason. */
        ERRORED
    }

    /** The unit of work for one record. */
    @FunctionalInterface
    interface TestRunner {
        void run(String message, String recordId) throws Exception;
    }

    /** Last exception seen by {@link #process}, for the caller's log line. */
    static final class Result {
        final Outcome outcome;
        final Exception error;

        Result(Outcome outcome, Exception error) {
            this.outcome = outcome;
            this.error = error;
        }
    }

    private final ExecutorService executor;
    private final int timeoutSeconds;
    private final TestRunner runner;

    RecordProcessor(ExecutorService executor, int timeoutSeconds, TestRunner runner) {
        this.executor = executor;
        this.timeoutSeconds = timeoutSeconds;
        this.runner = runner;
    }

    Result process(String recordId, String message) {
        Future<?> future = null;
        try {
            future = executor.submit(() -> {
                runner.run(message, recordId);
                return null;
            });
            future.get(timeoutSeconds, TimeUnit.SECONDS);
            return new Result(Outcome.COMPLETED, null);
        } catch (TimeoutException e) {
            cancel(future);
            return new Result(Outcome.TIMED_OUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(future);
            return new Result(Outcome.INTERRUPTED, e);
        } catch (RejectedExecutionException e) {
            return new Result(Outcome.REJECTED, e);
        } catch (Exception e) {
            cancel(future);
            return new Result(Outcome.ERRORED, e);
        }
    }

    private static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }
}
