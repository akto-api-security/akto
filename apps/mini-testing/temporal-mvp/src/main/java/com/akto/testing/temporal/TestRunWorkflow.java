package com.akto.testing.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** One workflow per test run. Its completion IS the durable answer to "did the run finish". */
@WorkflowInterface
public interface TestRunWorkflow {
    @WorkflowMethod
    RunProgress runTests(TestRunRequest request);

    /** Live progress read from workflow memory — no datastore access. */
    @QueryMethod
    RunProgress progress();
}
