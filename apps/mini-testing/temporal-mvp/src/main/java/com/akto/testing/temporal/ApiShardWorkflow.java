package com.akto.testing.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/** Child workflow covering one shard (large slice) of a run's APIs — keeps each workflow's history small at scale. */
@WorkflowInterface
public interface ApiShardWorkflow {
    @WorkflowMethod
    TestOutcomes testApiShard(TestRunRequest request, List<Integer> apiIds);
}
