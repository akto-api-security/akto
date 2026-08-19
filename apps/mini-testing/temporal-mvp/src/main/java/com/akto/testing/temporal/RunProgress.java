package com.akto.testing.temporal;

/** Live progress of a test run, served from workflow memory via a Query (no datastore read). */
public class RunProgress {
    public int totalTests;         // APIs × tests-per-API
    public int testsCompleted;     // tests finished so far
    public int batchesInProgress;  // API-batches still running
    public String strategy;        // how the run is decomposed (SINGLE_WORKFLOW / SHARDED / ...)
    public TestOutcomes outcomes = new TestOutcomes();

    public RunProgress() {}
}
