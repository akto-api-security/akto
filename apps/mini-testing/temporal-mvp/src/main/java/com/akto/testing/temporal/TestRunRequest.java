package com.akto.testing.temporal;

/**
 * A request to run tests for one customer: run every test against every API.
 * Public fields only (no getters) so the Temporal UI shows one clean key each.
 */
public class TestRunRequest {
    public String customerId;   // which customer/tenant this run belongs to (→ task queue)
    public String testRunId;    // unique id for this run (→ workflow id)
    public int numApis;         // how many APIs are under test
    public int testsPerApi;     // how many tests run against each API

    // --- stub-execution knobs (removed once real TestExecutor is wired in) ---
    public double stubFailRate;
    public double stubTimeoutRate;
    public int stubLatencyMs;

    public ExecutionConfig execution = new ExecutionConfig();

    public TestRunRequest() {}

    /** Total individual tests this run will perform = APIs × tests-per-API. */
    public int totalTests() { return numApis * testsPerApi; }
}
