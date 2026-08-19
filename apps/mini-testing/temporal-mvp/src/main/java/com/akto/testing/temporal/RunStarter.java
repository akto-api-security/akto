package com.akto.testing.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Triggers a test run for a customer, polls live progress via Query, prints durable accounting. */
public class RunStarter {
    public static void main(String[] args) throws Exception {
        String customerId = System.getenv().getOrDefault("CUSTOMER", "acme");
        int numApis = env("NUM_APIS", 120);
        int testsPerApi = env("TESTS_PER_API", 50);
        String testRunId = System.getenv().getOrDefault("TEST_RUN_ID", "run-" + customerId + "-" + numApis + "x" + testsPerApi);
        String taskQueue = "customer-" + customerId;
        String target = System.getenv().getOrDefault("TEMPORAL_TARGET", "127.0.0.1:7233");

        TestRunRequest req = new TestRunRequest();
        req.customerId = customerId;
        req.testRunId = testRunId;
        req.numApis = numApis;
        req.testsPerApi = testsPerApi;
        req.stubFailRate = Double.parseDouble(System.getenv().getOrDefault("FAIL_RATE", "0.07"));
        req.stubTimeoutRate = Double.parseDouble(System.getenv().getOrDefault("TIMEOUT_RATE", "0.03"));
        req.stubLatencyMs = env("LATENCY_MS", 150);
        req.execution.maxBatchesPerWorkflow = env("MAX_BATCHES_PER_WORKFLOW", 2000);
        req.execution.targetTestsPerBatch = env("TARGET_TESTS_PER_BATCH", 1000);
        req.execution.concurrentTestsPerApi = env("CONCURRENT_TESTS_PER_API", 6);

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        TestRunWorkflow run = client.newWorkflowStub(TestRunWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId(testRunId).build());
        WorkflowClient.start(run::runTests, req);
        System.out.println("[run] started customer=" + customerId + " testRunId=" + testRunId
                + " numApis=" + numApis + " testsPerApi=" + testsPerApi + " totalTests=" + req.totalTests());

        long started = System.currentTimeMillis();
        WorkflowStub untyped = WorkflowStub.fromTyped(run);
        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<RunProgress> result = ex.submit(() -> untyped.getResult(RunProgress.class));

        String last = "";
        while (!result.isDone()) {
            long sec = (System.currentTimeMillis() - started) / 1000;
            try {
                RunProgress p = run.progress();
                String line = "[t+" + sec + "s] strategy=" + p.strategy
                        + " testsCompleted=" + p.testsCompleted + "/" + p.totalTests
                        + " batchesInProgress=" + p.batchesInProgress
                        + " passed=" + p.outcomes.passed + " failed=" + p.outcomes.failed
                        + " timedOut=" + p.outcomes.timedOut;
                if (!line.equals(last)) { System.out.println(line); last = line; }
            } catch (Exception e) {
                System.out.println("[t+" + sec + "s] (progress query failed — worker down?) " + shortMsg(e));
            }
            Thread.sleep(1000);
        }

        RunProgress res = result.get();
        ex.shutdownNow();

        TestOutcomes durable = ResultStore.finalOutcomes(testRunId);
        int distinct = ResultStore.distinctTests(testRunId);
        int attempts = ResultStore.countAttempts(testRunId);
        int total = req.totalTests();
        int sum = res.outcomes.total();

        System.out.println("\n===== TEST RUN COMPLETE (customer=" + customerId + ") =====");
        System.out.println("workflow outcomes      : " + res.outcomes);
        System.out.println("saved distinct tests   : " + distinct + " (expected " + total + ")");
        System.out.println("saved outcomes         : " + durable);
        System.out.println("tests executed         : " + attempts + " (rework = " + (attempts - total) + " re-run after crash)");
        System.out.println("ACCOUNTING sum==total  : " + (sum == total) + " (" + sum + " == " + total + ")");
        System.out.println("NO LOST TESTS          : " + (distinct == total));
        System.exit(0);
    }

    static int env(String k, int d) { String v = System.getenv(k); return v == null ? d : Integer.parseInt(v); }
    static String shortMsg(Exception e) { String m = e.getMessage(); return m == null ? e.getClass().getSimpleName() : m.split("\n")[0]; }
}
