package com.akto.testing.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface ApiTestingActivities {
    /** Run every test against each API in the batch. Bounded per-API concurrency, idempotent resume. */
    @ActivityMethod
    TestOutcomes testApiBatch(TestRunRequest request, List<Integer> apiIds);
}
