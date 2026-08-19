package com.akto.testing.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ApiShardWorkflowImpl implements ApiShardWorkflow {

    private final ApiTestingActivities apiTesting = Workflow.newActivityStub(
            ApiTestingActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setHeartbeatTimeout(Duration.ofSeconds(8))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(5))
                            .setBackoffCoefficient(2)
                            .setMaximumAttempts(20)
                            .build())
                    .build());

    @Override
    public TestOutcomes testApiShard(TestRunRequest req, List<Integer> apiIds) {
        ExecutionPlan plan = Planner.planFor(req.numApis, req.testsPerApi, req.execution);
        TestOutcomes outcomes = new TestOutcomes();
        List<Promise<TestOutcomes>> pending = new ArrayList<>();
        for (int i = 0; i < apiIds.size(); i += plan.apisPerBatch) {
            List<Integer> batch = new ArrayList<>(apiIds.subList(i, Math.min(i + plan.apisPerBatch, apiIds.size())));
            pending.add(Async.function(apiTesting::testApiBatch, req, batch));
        }
        Promise.allOf(pending).get();
        for (Promise<TestOutcomes> p : pending) outcomes.add(p.get());
        return outcomes;
    }
}
