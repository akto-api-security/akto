package com.akto.testing;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.kafka_utils.TestingConfigurations;

import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public class StaleWorkReconciler {

    private static final LoggerMaker loggerMaker = new LoggerMaker(StaleWorkReconciler.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final int LAST_TEST_RUN_EXECUTION_DELTA = 5 * 60;
    private static final int MAX_RETRIES_FOR_FAILED_SUMMARIES = 3;

    public static class ReconciliationResult {
        public final boolean shouldContinue;
        public final TestingRunResultSummary trrs;
        public final ObjectId summaryId;
        public final boolean maxRetriesReached;
        ReconciliationResult(boolean shouldContinue, TestingRunResultSummary trrs, ObjectId summaryId, boolean maxRetriesReached) {
            this.shouldContinue = shouldContinue;
            this.trrs = trrs;
            this.summaryId = summaryId;
            this.maxRetriesReached = maxRetriesReached;
        }
    }

    public static ReconciliationResult reconcileStaleOrRunningWork(TestingRun testingRun, TestingRunResultSummary trrs,
            ObjectId summaryId, boolean isResumeCase, boolean isSummaryRunning, boolean isTestingRunRunning,
            boolean isTestingRunResultRerunCase, TestingConfigurations config, int start, String leaseToken) {
        boolean maxRetriesReached = false;

        if (isResumeCase) {
            loggerMaker.infoAndAddToDb("Resuming summary " + trrs.getHexId()
                    + ": production already complete, draining remaining messages");
        } else if (isSummaryRunning || isTestingRunRunning) {
            loggerMaker.infoAndAddToDb("TRRS or TR is in running state, checking if it should run it or not");
            TestingRunResultSummary testingRunResultSummary;
            if (trrs != null) {
                testingRunResultSummary = trrs;
            } else {
                Map<ObjectId, TestingRunResultSummary> objectIdTestingRunResultSummaryMap = dataActor.fetchTestingRunResultSummaryMap(testingRun.getId().toHexString());
                testingRunResultSummary = objectIdTestingRunResultSummaryMap.get(testingRun.getId());
            }
            // For rerun case, we need to check the original test results
            List<TestingRunResult> testingRunResults;
            if (testingRunResultSummary != null) {
                if (isTestingRunResultRerunCase) {
                    testingRunResults = dataActor.fetchLatestTestingRunResult(testingRunResultSummary.getOriginalTestingRunResultSummaryId().toHexString());
                } else {
                    testingRunResults = dataActor.fetchLatestTestingRunResult(testingRunResultSummary.getId().toHexString());
                }

                if (testingRunResults != null && !testingRunResults.isEmpty()) {
                    TestingRunResult testingRunResult = testingRunResults.get(0);
                    if (Context.now() - testingRunResult.getEndTimestamp() < LAST_TEST_RUN_EXECUTION_DELTA) {
                        loggerMaker.infoAndAddToDb("Skipping test run as it was executed recently, TRR_ID:"
                                + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId()
                                + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                        return new ReconciliationResult(true, trrs, summaryId, maxRetriesReached);
                    } else {
                        loggerMaker.infoAndAddToDb("Test run was executed long ago, TRR_ID:"
                                + testingRunResult.getHexId() + ", TRRS_ID:" + testingRunResultSummary.getHexId()
                                + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                                + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                        int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
                        int sinceTimestamp = Context.now() - ((MAX_RETRIES_FOR_FAILED_SUMMARIES + 1) * maxRunTime);

                        int countFailedSummaries = (int) dataActor.countTestingRunResultSummaries(
                                testingRun.getHexId(), sinceTimestamp, State.FAILED);
                        TestingRunResultSummary runResultSummary = dataActor.fetchTestingRunResultSummary(testingRunResultSummary.getId().toHexString());
                        TestingRunResultSummary summary;
                        if(countFailedSummaries >= (MAX_RETRIES_FOR_FAILED_SUMMARIES - 1)){
                            summary = dataActor.updateIssueCountInSummaryFenced(testingRunResultSummary.getId().toHexString(), runResultSummary.getCountIssues(), leaseToken);
                            loggerMaker.infoAndAddToDb("Max retries level reached for TRR_ID: " + testingRun.getHexId(), LogDb.TESTING);
                            maxRetriesReached = true;
                        }else{
                            summary = dataActor.markTestRunResultSummaryFailed(testingRunResultSummary.getId().toHexString(), leaseToken);
                        }

                        runResultSummary = dataActor.fetchTestingRunResultSummary(testingRunResultSummary.getId().toHexString());
                        if (summary == null) {
                            loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                            return new ReconciliationResult(true, trrs, summaryId, maxRetriesReached);
                        }
                        // TODO: Delete completely, disabled feature not used anymore
                        // GithubUtils.publishGithubComments(runResultSummary);
                    }
                } else {
                    loggerMaker.infoAndAddToDb("No executions made for this test, will need to restart it, TRRS_ID:"
                            + testingRunResultSummary.getHexId()
                            + (isTestingRunResultRerunCase ? " (rerun case) " : " ")
                            + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                    //won't reach here for testing run result rerun case, as there will be a minimum of 1 TRR
                    //for safety delete run result.
                    if (isTestingRunResultRerunCase) {
                        dataActor.deleteTestRunResultSummary(testingRunResultSummary.getId().toHexString());
                        config.setTestingRunResultList(null);
                        config.setRerunTestingRunResultSummary(null);
                        loggerMaker.infoAndAddToDb("Deleted for TestingRunResult rerun case for failed testrun TRRS: " + testingRunResultSummary.getId(), LogDb.TESTING);
                        return new ReconciliationResult(true, trrs, summaryId, maxRetriesReached);
                    }
                    TestingRunResultSummary summary = dataActor.markTestRunResultSummaryFailed(testingRunResultSummary.getId().toHexString(), leaseToken);
                    if (summary == null) {
                        loggerMaker.infoAndAddToDb("Skipping because some other thread picked it up, TRRS_ID:" + testingRunResultSummary.getHexId() + " TR_ID:" + testingRun.getHexId(), LogDb.TESTING);
                        return new ReconciliationResult(true, trrs, summaryId, maxRetriesReached);
                    }
                }

                // insert new summary based on old summary
                if(maxRetriesReached){
                    loggerMaker.infoAndAddToDb("Exiting out as maxRetries have been reached for testingRun: " + testingRun.getHexId(), LogDb.TESTING);
                }else{
                    if (summaryId != null) {
                        trrs.setId(new ObjectId());
                        trrs.setStartTimestamp(start);
                        trrs.setState(State.RUNNING);
                        // Same reasoning as the leased mint in the else branch below and in
                        // failTestingRun: an un-leased insert here is permanently unreachable
                        // via the safe TRRS-scoped discovery path from the moment of its
                        // creation, for the exact same reason - it's the same bug, just on
                        // the safe-path retry instead of the fallback-path retry.
                        trrs.setLeaseToken(leaseToken);
                        trrs.setLeaseExpiryTs(Context.now() + TestingLease.LEASE_SECONDS);
                        dataActor.insertTestingRunResultSummary(trrs);
                        TestingLease.getInstance().adopt(leaseToken);
                        summaryId = trrs.getId();
                    } else {
                        trrs = dataActor.createTRRSummaryIfAbsent(testingRun.getHexId(), start, leaseToken, TestingLease.LEASE_SECONDS);
                        if (trrs != null) TestingLease.getInstance().adopt(leaseToken);
                        summaryId = trrs.getId();
                    }
                }
            } else {
                loggerMaker.infoAndAddToDb("No summary found. Let's run it as usual");
            }
        }

        return new ReconciliationResult(false, trrs, summaryId, maxRetriesReached);
    }
}
