package com.akto.testing.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class TestRunWorkflowImpl implements TestRunWorkflow {

    private final ApiTestingActivities apiTesting = Workflow.newActivityStub(
            ApiTestingActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(8)) // dead/stuck worker detected fast → retry
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(5))
                            .setBackoffCoefficient(2)
                            .setMaximumAttempts(20)
                            .build())
                    .build());

    // ---- in-memory run state (served by the query; reconstructed on replay) ----
    private int totalTests;
    private int testsCompleted;
    private int batchesInProgress;
    private String strategy = "";
    private final TestOutcomes outcomes = new TestOutcomes();

    @Override
    public RunProgress runTests(TestRunRequest req) {
        ExecutionPlan plan = Planner.planFor(req.numApis, req.testsPerApi, req.execution);
        this.strategy = plan.label();
        this.totalTests = req.totalTests();

        if (plan.strategy == ExecutionPlan.Strategy.SINGLE_WORKFLOW) {
            List<List<Integer>> batches = partition(allApiIds(req.numApis), plan.apisPerBatch);
            this.batchesInProgress = batches.size();
            List<Promise<TestOutcomes>> pending = new ArrayList<>();
            for (List<Integer> batch : batches) {
                pending.add(Async.function(apiTesting::testApiBatch, req, batch).thenApply(this::mergeBatchResult));
            }
            Promise.allOf(pending).get();
        } else {
            // SHARDED: fan out child workflows so no single workflow's history grows too large.
            List<List<Integer>> shards = partition(allApiIds(req.numApis), plan.apisPerShard);
            this.batchesInProgress = shards.size();
            List<Promise<TestOutcomes>> pending = new ArrayList<>();
            for (int i = 0; i < shards.size(); i++) {
                ApiShardWorkflow child = Workflow.newChildWorkflowStub(
                        ApiShardWorkflow.class,
                        ChildWorkflowOptions.newBuilder()
                                .setWorkflowId(req.testRunId + "-shard-" + i)
                                .build());
                pending.add(Async.function(child::testApiShard, req, shards.get(i)).thenApply(this::mergeBatchResult));
            }
            Promise.allOf(pending).get();
        }
        this.batchesInProgress = 0;
        return progress();
    }

    private TestOutcomes mergeBatchResult(TestOutcomes batchOutcomes) {
        outcomes.add(batchOutcomes);
        testsCompleted += batchOutcomes.total();
        batchesInProgress--;
        return batchOutcomes;
    }

    @Override
    public RunProgress progress() {
        RunProgress p = new RunProgress();
        p.totalTests = totalTests;
        p.testsCompleted = testsCompleted;
        p.batchesInProgress = batchesInProgress;
        p.strategy = strategy;
        TestOutcomes snapshot = new TestOutcomes();
        snapshot.add(outcomes);
        p.outcomes = snapshot;
        return p;
    }

    // ---- deterministic helpers (safe in workflow code) ----
    private static List<Integer> allApiIds(int numApis) {
        List<Integer> ids = new ArrayList<>(numApis);
        for (int i = 0; i < numApis; i++) ids.add(i);
        return ids;
    }

    private static List<List<Integer>> partition(List<Integer> apiIds, int size) {
        List<List<Integer>> parts = new ArrayList<>();
        for (int i = 0; i < apiIds.size(); i += size) {
            parts.add(new ArrayList<>(apiIds.subList(i, Math.min(i + size, apiIds.size()))));
        }
        return parts;
    }
}
