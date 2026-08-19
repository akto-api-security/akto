package com.akto.testing.temporal;

/**
 * Chooses how to decompose a run — a PURE, deterministic function of
 * (numApis, testsPerApi, config). Deterministic (no clock/random) so replay is stable.
 * Same code picks SINGLE_WORKFLOW for small runs, SHARDED / SHARDED_WINDOWED as APIs grow.
 */
public final class Planner {
    private Planner() {}

    public static ExecutionPlan planFor(int numApis, int testsPerApi, ExecutionConfig cfg) {
        int apisPerBatch = Math.max(1,
                Math.min(cfg.maxApisPerBatch, Math.max(1, cfg.targetTestsPerBatch / Math.max(testsPerApi, 1))));
        int numBatches = ceilDiv(numApis, apisPerBatch);

        if (numBatches <= cfg.maxBatchesPerWorkflow) {
            return new ExecutionPlan(ExecutionPlan.Strategy.SINGLE_WORKFLOW, apisPerBatch, numBatches, 0, 0);
        }
        int apisPerShard = cfg.maxBatchesPerWorkflow * apisPerBatch;
        int numShards = ceilDiv(numApis, apisPerShard);
        if (numShards <= cfg.maxBatchesPerWorkflow) {
            return new ExecutionPlan(ExecutionPlan.Strategy.SHARDED, apisPerBatch, numBatches, numShards, apisPerShard);
        }
        return new ExecutionPlan(ExecutionPlan.Strategy.SHARDED_WINDOWED, apisPerBatch, numBatches, numShards, apisPerShard);
    }

    static int ceilDiv(int a, int b) { return (a + b - 1) / b; }
}
