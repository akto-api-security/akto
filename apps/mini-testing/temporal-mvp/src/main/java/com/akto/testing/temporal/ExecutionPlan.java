package com.akto.testing.temporal;

/**
 * How a run is decomposed, chosen automatically from (numApis, testsPerApi).
 * batch  = APIs one activity tests.
 * shard  = a large slice of the run handled by a child workflow (only at scale).
 */
public class ExecutionPlan {
    public enum Strategy {
        SINGLE_WORKFLOW,   // one workflow tests all batches directly (small/medium runs)
        SHARDED,           // fan out into child workflows, each covering a shard of APIs (large runs)
        SHARDED_WINDOWED   // SHARDED + windowed continuation for very large runs
    }

    public Strategy strategy;
    public int apisPerBatch;    // APIs tested by one activity
    public int numBatches;      // total batches (activities)
    public int numShards;       // child workflows (0 when SINGLE_WORKFLOW)
    public int apisPerShard;    // APIs per child workflow (0 when SINGLE_WORKFLOW)

    public ExecutionPlan() {}

    public ExecutionPlan(Strategy strategy, int apisPerBatch, int numBatches, int numShards, int apisPerShard) {
        this.strategy = strategy; this.apisPerBatch = apisPerBatch; this.numBatches = numBatches;
        this.numShards = numShards; this.apisPerShard = apisPerShard;
    }

    public String label() {
        return strategy + "(batches=" + numBatches + (numShards > 0 ? ",shards=" + numShards : "") + ")";
    }
}
