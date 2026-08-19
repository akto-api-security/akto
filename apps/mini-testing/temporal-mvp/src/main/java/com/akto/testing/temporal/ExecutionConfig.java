package com.akto.testing.temporal;

/**
 * Tuning knobs for how a run is executed. Pinned at run start for replay-safety.
 * A "batch" = the set of APIs one activity tests as a single scheduled/retried unit.
 * Public fields only (no getters) so the Temporal UI shows one clean key each.
 */
public class ExecutionConfig {
    /** Aim for ~this many tests per batch (an activity tests a batch of APIs). */
    public int targetTestsPerBatch = 1000;
    /** Max batches (activities/child workflows) per single workflow (history budget). */
    public int maxBatchesPerWorkflow = 2000;
    /** Cap on how many APIs are packed into one batch. */
    public int maxApisPerBatch = 200;
    /** How many tests run at once against a single API (fairness + endpoint safety). */
    public int concurrentTestsPerApi = 6;
    /** Ceiling on one test's runtime (represents the production 5-min cap). */
    public int perTestTimeoutMs = 5000;

    public ExecutionConfig() {}
}
