package com.akto.testing.temporal;

/** Shows the planner choosing a strategy from actual (numApis, testsPerApi) — one code path. */
public class PlanDemo {
    public static void main(String[] args) {
        ExecutionConfig cfg = new ExecutionConfig();
        int[][] cases = { {10, 1000}, {2000, 1000}, {200000, 1000}, {10000000, 1000}, {50, 20} };
        System.out.println("=== execution planner (targetTestsPerBatch=" + cfg.targetTestsPerBatch
                + ", maxBatchesPerWorkflow=" + cfg.maxBatchesPerWorkflow + ") ===");
        for (int[] c : cases) {
            int numApis = c[0], testsPerApi = c[1];
            ExecutionPlan p = Planner.planFor(numApis, testsPerApi, cfg);
            System.out.printf("apis=%9d testsPerApi=%5d totalTests=%,15d -> %-16s apisPerBatch=%d batches=%,d%s%n",
                    numApis, testsPerApi, (long) numApis * testsPerApi, p.strategy, p.apisPerBatch, p.numBatches,
                    p.numShards > 0 ? " shards=" + p.numShards + " apisPerShard=" + p.apisPerShard : "");
        }
    }
}
