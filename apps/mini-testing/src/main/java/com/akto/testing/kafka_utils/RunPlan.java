package com.akto.testing.kafka_utils;

import java.util.Optional;

import com.akto.dto.testing.TestingRun;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

/**
 * Everything {@link ConsumerUtil#init(int)} needs to know about the run it is about to drain,
 * resolved once from the persisted state document. Pure data: building one does no I/O, so the
 * defaulting rules (picked-up timestamp vs now, per-run max time vs the global default, missing
 * account id) can be tested against hand-built state documents.
 */
public record RunPlan(
        String summaryId,
        int accountId,
        int startTime,
        int maxRunTimeSec,
        int expectedRecords,
        int concurrency,
        String topic,
        String groupId) {

    /**
     * @param state           the document from {@link TestingStateStore#read()}; may be null
     * @param concurrency     worker pool size / parallel-consumer concurrency for this run
     * @param defaultMaxRunTime used when the state document carries no per-run max time
     * @param now             epoch seconds; the run's start time when no picked-up timestamp was persisted
     * @param fallbackAccountId used when the state document carries no account id (-1 = none)
     * @return empty when there is no state to resume from
     */
    public static Optional<RunPlan> from(BasicDBObject state, int concurrency, int defaultMaxRunTime,
                                         int now, int fallbackAccountId) {
        if (state == null) {
            return Optional.empty();
        }
        String summaryId = state.getString(TestingStateStore.SUMMARY_ID);
        if (summaryId == null) {
            return Optional.empty();
        }

        int startTime = state.containsField(TestingRun.PICKED_UP_TIMESTAMP)
                ? state.getInt(TestingRun.PICKED_UP_TIMESTAMP, now)
                : now;
        int maxRunTime = state.containsField(TestingStateStore.TEST_RUN_MAX_TIME_SECONDS)
                ? state.getInt(TestingStateStore.TEST_RUN_MAX_TIME_SECONDS, defaultMaxRunTime)
                : defaultMaxRunTime;
        int expectedRecords = state.containsField(TestingStateStore.EXPECTED_RECORDS)
                ? state.getInt(TestingStateStore.EXPECTED_RECORDS)
                : -1;
        int accountId = state.containsField(TestingStateStore.ACCOUNT_ID)
                ? state.getInt(TestingStateStore.ACCOUNT_ID)
                : fallbackAccountId;

        return Optional.of(new RunPlan(
                summaryId,
                accountId,
                startTime,
                maxRunTime,
                expectedRecords,
                concurrency,
                Constants.getTestResultsTopicName(summaryId),
                Constants.getKafkaGroupIdConfig(summaryId)));
    }

    /** Seconds of the run's budget still unspent at {@code now}, floored at zero. */
    public int remainingSec(int now) {
        return Math.max(0, maxRunTimeSec - (now - startTime));
    }
}
