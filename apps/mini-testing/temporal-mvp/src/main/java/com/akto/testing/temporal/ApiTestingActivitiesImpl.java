package com.akto.testing.temporal;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs every test against each API in a batch. Two axes of parallelism:
 *   - across APIs: many of these activities run at once (Temporal + worker concurrency)
 *   - within an API: a bounded pool of size {@code concurrentTestsPerApi} (fairness + endpoint safety)
 * Each test has a timeout ceiling; a hung test is recorded TIMED_OUT and testing continues.
 * On retry (after a crash) tests already saved are skipped.
 *
 * PRODUCTION: replace runOneTest(...) with TestExecutor.runTestNew(...) and the ResultStore
 * calls with idempotent bulk upserts via ClientActor/cyborg.
 */
public class ApiTestingActivitiesImpl implements ApiTestingActivities {

    @Override
    public TestOutcomes testApiBatch(TestRunRequest req, List<Integer> apiIds) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        TestOutcomes outcomes = new TestOutcomes();
        Map<String, String> alreadySaved = ResultStore.loadSavedOutcomes(req.testRunId); // resume-skip source
        AtomicInteger testsRunThisAttempt = new AtomicInteger();

        for (int apiId : apiIds) {
            // one API cannot exceed concurrentTestsPerApi at once → fairness + endpoint safety
            ExecutorService pool = Executors.newFixedThreadPool(req.execution.concurrentTestsPerApi);
            try {
                CompletableFuture<?>[] tests = new CompletableFuture[req.testsPerApi];
                for (int testId = 0; testId < req.testsPerApi; testId++) {
                    final int api = apiId, test = testId;
                    tests[testId] = CompletableFuture.runAsync(
                            () -> runSingleTest(req, api, test, alreadySaved, outcomes, testsRunThisAttempt), pool);
                }
                CompletableFuture.allOf(tests).join();
            } finally {
                pool.shutdownNow();
            }
            ctx.heartbeat(testsRunThisAttempt.get()); // liveness + progress (well under heartbeat timeout)
        }
        return outcomes;
    }

    private void runSingleTest(TestRunRequest req, int apiId, int testId, Map<String, String> alreadySaved,
                              TestOutcomes outcomes, AtomicInteger testsRunThisAttempt) {
        String key = ResultStore.resultKey(req.testRunId, apiId, testId);
        String saved = alreadySaved.get(key);
        if (saved != null) {            // already done on a prior attempt → skip, still counted once
            outcomes.record(saved);
            return;
        }
        ResultStore.recordAttempt(req.testRunId, key);
        String outcome = runOneTest(req);
        ResultStore.saveOutcome(req.testRunId, key, outcome); // idempotent upsert (key-addressed)
        outcomes.record(outcome);
        testsRunThisAttempt.incrementAndGet();
    }

    /** STUB test execution: latency + injected fail/timeout. Swap for TestExecutor.runTestNew in prod. */
    private String runOneTest(TestRunRequest req) {
        double r = ThreadLocalRandom.current().nextDouble();
        String outcome = (r < req.stubTimeoutRate) ? "timedOut"
                : (r < req.stubTimeoutRate + req.stubFailRate) ? "failed" : "passed";
        int dur = Math.min(req.stubLatencyMs, req.execution.perTestTimeoutMs); // per-test ceiling
        try { Thread.sleep(dur); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return outcome;
    }
}
