package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

import com.akto.dto.testing.TestingRun;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

public class RunPlanTest {

    private static final String SUMMARY = "66f1a2b3c4d5e6f7a8b9c0d1";

    @Test
    public void noStateYieldsNoPlan() {
        assertFalse(RunPlan.from(null, 10, 3600, 1000, -1).isPresent());
        assertFalse(RunPlan.from(new BasicDBObject(), 10, 3600, 1000, -1).isPresent());
    }

    @Test
    public void defaultsApplyWhenStateIsMinimal() {
        BasicDBObject state = new BasicDBObject(TestingStateStore.SUMMARY_ID, SUMMARY);

        RunPlan plan = RunPlan.from(state, 7, 3600, 1000, 42).orElseThrow();

        assertEquals(SUMMARY, plan.summaryId());
        assertEquals(1000, plan.startTime());
        assertEquals(3600, plan.maxRunTimeSec());
        assertEquals(-1, plan.expectedRecords());
        assertEquals(42, plan.accountId());
        assertEquals(7, plan.concurrency());
        assertEquals(Constants.getTestResultsTopicName(SUMMARY), plan.topic());
        assertEquals(Constants.getKafkaGroupIdConfig(SUMMARY), plan.groupId());
    }

    @Test
    public void persistedFieldsOverrideDefaults() {
        BasicDBObject state = new BasicDBObject(TestingStateStore.SUMMARY_ID, SUMMARY)
                .append(TestingRun.PICKED_UP_TIMESTAMP, 500)
                .append(TestingStateStore.TEST_RUN_MAX_TIME_SECONDS, 120)
                .append(TestingStateStore.EXPECTED_RECORDS, 9)
                .append(TestingStateStore.ACCOUNT_ID, 1000000);

        RunPlan plan = RunPlan.from(state, 7, 3600, 1000, 42).orElseThrow();

        assertEquals(500, plan.startTime());
        assertEquals(120, plan.maxRunTimeSec());
        assertEquals(9, plan.expectedRecords());
        assertEquals(1000000, plan.accountId());
    }

    @Test
    public void remainingTimeIsFlooredAtZero() {
        BasicDBObject state = new BasicDBObject(TestingStateStore.SUMMARY_ID, SUMMARY)
                .append(TestingRun.PICKED_UP_TIMESTAMP, 500)
                .append(TestingStateStore.TEST_RUN_MAX_TIME_SECONDS, 100);
        RunPlan plan = RunPlan.from(state, 1, 3600, 1000, -1).orElseThrow();

        assertEquals(40, plan.remainingSec(560));
        assertEquals(0, plan.remainingSec(700));
    }
}
