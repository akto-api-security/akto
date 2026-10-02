package com.akto.data_actor;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunResultSummary;
import com.mongodb.client.model.Filters;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TestClaimNextTestWork extends MongoBasedTest {

    private static final String MODULE = "akto-testing-module";
    private static final int NOW = 1_700_000_000;

    @Before
    public void clearCollections() {
        Context.accountId.set(ACCOUNT_ID);
        TestingRunDao.instance.getMCollection().deleteMany(Filters.empty());
        TestingRunResultSummariesDao.instance.getMCollection().deleteMany(Filters.empty());
    }

    private TestingRun freshTestingRun(State state, String miniTestingServiceName, List<String> allowed) {
        TestingRun run = new TestingRun();
        run.setId(new ObjectId());
        run.setState(state);
        run.setScheduleTimestamp(NOW - 100);
        run.setMiniTestingServiceName(miniTestingServiceName);
        if (allowed != null) run.setAllowedMiniTestingServiceNames(allowed);
        TestingRunDao.instance.insertOne(run);
        return run;
    }

    private TestingRunResultSummary insertTrrs(ObjectId testingRunId, State state, Integer leaseExpiryTs,
            boolean producerDone, Map<String, String> metadata, ObjectId originalId) {
        TestingRunResultSummary trrs = new TestingRunResultSummary();
        trrs.setId(new ObjectId());
        trrs.setTestingRunId(testingRunId);
        trrs.setState(state);
        trrs.setStartTimestamp(NOW - 200);
        trrs.setProducerDone(producerDone);
        if (leaseExpiryTs != null) trrs.setLeaseExpiryTs(leaseExpiryTs);
        if (metadata != null) trrs.setMetadata(metadata);
        if (originalId != null) trrs.setOriginalTestingRunResultSummaryId(originalId);
        TestingRunResultSummariesDao.instance.insertOne(trrs);
        return trrs;
    }

    // ---- 1. FRESH_RUN ----
    @Test
    public void freshRun_noExistingTrrs_mintsOneAndClaims() {
        TestingRun run = freshTestingRun(State.SCHEDULED, MODULE, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-1", 360);

        assertEquals(DbLayer.VERDICT_FRESH_RUN, result.verdict);
        assertNotNull(result.trrs);
        assertEquals(run.getId(), result.trrs.getTestingRunId());
        assertEquals(State.RUNNING, result.trrs.getState());
        assertEquals("token-1", result.trrs.getLeaseToken());
        assertFalse(result.trrs.getProducerDone());

        TestingRun persistedRun = TestingRunDao.instance.findOne(Filters.eq("_id", run.getId()));
        assertEquals(State.RUNNING, persistedRun.getState());

        long trrsCount = TestingRunResultSummariesDao.instance.getMCollection()
                .countDocuments(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, run.getId()));
        assertEquals(1, trrsCount);
    }

    // ---- 2. CICD ----
    @Test
    public void cicd_preCreatedScheduledTrrsWithMetadata() {
        TestingRun run = freshTestingRun(State.SCHEDULED, MODULE, null);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("repository", "akto-api-security/akto");
        metadata.put("commit_sha_head", "abc123");
        insertTrrs(run.getId(), State.SCHEDULED, null, false, metadata, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-2", 360);

        assertEquals(DbLayer.VERDICT_CICD, result.verdict);
        assertEquals(State.RUNNING, result.trrs.getState());
        assertEquals("token-2", result.trrs.getLeaseToken());
    }

    // ---- 3. RERUN_SPECIFIC_TESTCASES ----
    @Test
    public void rerunSpecificTestcases_preCreatedScheduledTrrsWithOriginalId() {
        TestingRun run = freshTestingRun(State.SCHEDULED, MODULE, null);
        ObjectId originalId = new ObjectId();
        insertTrrs(run.getId(), State.SCHEDULED, null, false, null, originalId);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-3", 360);

        assertEquals(DbLayer.VERDICT_RERUN_SPECIFIC_TESTCASES, result.verdict);
        assertEquals(originalId, result.trrs.getOriginalTestingRunResultSummaryId());
    }

    // ---- 4. RECLAIMED_ABANDONED, producerDone=true ----
    @Test
    public void reclaimedAbandoned_producerDoneTrue() {
        TestingRun run = freshTestingRun(State.RUNNING, MODULE, null);
        insertTrrs(run.getId(), State.RUNNING, NOW - 500, true, null, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-4", 360);

        assertEquals(DbLayer.VERDICT_RECLAIMED_ABANDONED, result.verdict);
        assertTrue(result.trrs.getProducerDone());
        assertEquals("token-4", result.trrs.getLeaseToken());
    }

    // ---- 5. RECLAIMED_ABANDONED, producerDone=false - the conflation bug this guards against ----
    @Test
    public void reclaimedAbandoned_producerDoneFalse_isNotMisreportedAsCicd() {
        TestingRun run = freshTestingRun(State.RUNNING, MODULE, null);
        insertTrrs(run.getId(), State.RUNNING, NOW - 500, false, null, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-5", 360);

        assertEquals("a reclaimed mid-fanout summary must report RECLAIMED_ABANDONED, not CICD, "
                + "regardless of producerDone", DbLayer.VERDICT_RECLAIMED_ABANDONED, result.verdict);
        assertFalse(result.trrs.getProducerDone());
    }

    // ---- 6. NO_WORK_FOUND - nothing eligible at all ----
    @Test
    public void noWorkFound_whenNothingEligible() {
        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-6", 360);

        assertEquals(DbLayer.VERDICT_NO_WORK_FOUND, result.verdict);
        assertNull(result.trrs);
        assertNull(result.testingRun);
    }

    // ---- 7. a healthy, live-leased RUNNING summary is never touched ----
    @Test
    public void liveLeaseIsNeverReclaimed() {
        TestingRun run = freshTestingRun(State.RUNNING, MODULE, null);
        TestingRunResultSummary live = insertTrrs(run.getId(), State.RUNNING, Context.now() + 1000, true, null, null);
        live.setLeaseToken("someone-elses-token");
        TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", live.getId()),
                com.mongodb.client.model.Updates.set(TestingRunResultSummary.LEASE_TOKEN, "someone-elses-token"));

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "attacker-token", 360);

        assertEquals(DbLayer.VERDICT_NO_WORK_FOUND, result.verdict);
        TestingRunResultSummary stillLive = TestingRunResultSummariesDao.instance.findOne(Filters.eq("_id", live.getId()));
        assertEquals("someone-elses-token", stillLive.getLeaseToken());
    }

    // ---- 8. orphan exclusion: terminal parent must not be resurrected ----
    @Test
    public void orphanedTrrsWithTerminalParent_isNeverResurrected() {
        TestingRun completedRun = freshTestingRun(State.COMPLETED, MODULE, null);
        insertTrrs(completedRun.getId(), State.RUNNING, NOW - 500, true, null, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-8", 360);

        assertEquals(DbLayer.VERDICT_NO_WORK_FOUND, result.verdict);
    }

    // ---- 9a. eligible via exact miniTestingServiceName match ----
    @Test
    public void eligible_viaExactMiniTestingServiceNameMatch() {
        TestingRun run = freshTestingRun(State.SCHEDULED, MODULE, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-9a", 360);

        assertEquals(DbLayer.VERDICT_FRESH_RUN, result.verdict);
        assertEquals(run.getId(), result.testingRun.getId());
    }

    // ---- 9b. eligible via allowedMiniTestingServiceNames list membership ----
    @Test
    public void eligible_viaAllowedMiniTestingServiceNamesList() {
        TestingRun run = freshTestingRun(State.SCHEDULED, "some-other-primary-name",
                Arrays.asList("another-module", MODULE));

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-9b", 360);

        assertEquals(DbLayer.VERDICT_FRESH_RUN, result.verdict);
        assertEquals(run.getId(), result.testingRun.getId());
    }

    // ---- 9c. ineligible on both fields ----
    @Test
    public void ineligible_matchesNeitherField() {
        freshTestingRun(State.SCHEDULED, "some-other-module", Arrays.asList("yet-another-module"));

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-9c", 360);

        assertEquals(DbLayer.VERDICT_NO_WORK_FOUND, result.verdict);
    }

    // ---- 10. whole-document response shape ----
    @Test
    public void responseCarriesWholeDocuments_notATrimmedSubset() {
        TestingRun run = freshTestingRun(State.SCHEDULED, MODULE, null);
        run.setTestRunTime(7200);
        TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", run.getId()),
                com.mongodb.client.model.Updates.set("testRunTime", 7200));

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-10", 360);

        assertEquals(DbLayer.VERDICT_FRESH_RUN, result.verdict);
        assertEquals(7200, result.testingRun.getTestRunTime());
    }

    // ---- 11. race on the fresh-pickup insert: exactly one TRRS ever created ----
    @Test
    public void race_freshPickup_onlyOneTrrsEverCreated() throws Exception {
        TestingRun run = freshTestingRun(State.SCHEDULED, MODULE, null);

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLine.await();
                    Context.accountId.set(ACCOUNT_ID);
                    DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "race-token-" + idx, 360);
                    if (DbLayer.VERDICT_FRESH_RUN.equals(result.verdict)) {
                        wins.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        startLine.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals("exactly one racer may win the claim", 1, wins.get());
        long trrsCount = TestingRunResultSummariesDao.instance.getMCollection()
                .countDocuments(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, run.getId()));
        assertEquals("exactly one TRRS may ever be minted for this run", 1, trrsCount);
    }

    // ---- 12. race on claiming an existing abandoned/pre-created TRRS: exactly one winner ----
    @Test
    public void race_existingTrrsClaim_onlyOneWinner() throws Exception {
        TestingRun run = freshTestingRun(State.RUNNING, MODULE, null);
        insertTrrs(run.getId(), State.RUNNING, NOW - 500, true, null, null);

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLine.await();
                    Context.accountId.set(ACCOUNT_ID);
                    DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "race-token-b-" + idx, 360);
                    if (DbLayer.VERDICT_RECLAIMED_ABANDONED.equals(result.verdict)) {
                        wins.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        startLine.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals("exactly one racer may win the reclaim", 1, wins.get());
    }

    // ---- 13. a dead rerun-specific-testcases job must report abandonment, not its origin ----
    @Test
    public void deadRerunJob_isReclaimedAbandonedNotRerunSpecific() {
        TestingRun run = freshTestingRun(State.RUNNING, MODULE, null);
        ObjectId originalId = new ObjectId();
        insertTrrs(run.getId(), State.RUNNING, NOW - 500, false, null, originalId);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-13", 360);

        assertEquals("a rerun-job TRRS that died mid-run must report abandonment, not its origin",
                DbLayer.VERDICT_RECLAIMED_ABANDONED, result.verdict);
        assertEquals(originalId, result.trrs.getOriginalTestingRunResultSummaryId());
    }

    // ---- 14. retrying with the same leaseToken re-affirms your own still-valid claim ----
    @Test
    public void retryWithSameLeaseToken_reAffirmsOwnStillLiveClaim() {
        TestingRun run = freshTestingRun(State.RUNNING, MODULE, null);
        insertTrrs(run.getId(), State.RUNNING, Context.now() + 1000, false, null, null)
                .getId();
        TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, run.getId()),
                com.mongodb.client.model.Updates.set(TestingRunResultSummary.LEASE_TOKEN, "my-token"));

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "my-token", 360);

        assertEquals("a caller retrying with its own still-valid lease token must be let back in",
                DbLayer.VERDICT_RECLAIMED_ABANDONED, result.verdict);
        assertEquals("my-token", result.trrs.getLeaseToken());
    }

    // ---- 15. the actual incident shape: an unrelated due SCHEDULED run must never block reclaim ----
    @Test
    public void unrelatedScheduledRun_doesNotBlockReclaimOfAbandonedRun() {
        TestingRun unrelatedDueRun = freshTestingRun(State.SCHEDULED, MODULE, null);
        TestingRun abandonedRun = freshTestingRun(State.RUNNING, MODULE, null);
        TestingRunResultSummary abandonedTrrs = insertTrrs(abandonedRun.getId(), State.RUNNING, NOW - 500, true, null, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-15", 360);

        assertEquals(DbLayer.VERDICT_RECLAIMED_ABANDONED, result.verdict);
        assertEquals(abandonedTrrs.getId(), result.trrs.getId());
        TestingRun stillScheduled = TestingRunDao.instance.findOne(Filters.eq("_id", unrelatedDueRun.getId()));
        assertEquals("the unrelated due run must be left completely untouched",
                State.SCHEDULED, stillScheduled.getState());
    }

    // ---- 17. two distinct eligible runs at once: claims exactly one, leaves the other untouched ----
    @Test
    public void multipleEligibleCandidates_claimsExactlyOneLeavesSiblingUntouched() {
        TestingRun runA = freshTestingRun(State.RUNNING, MODULE, null);
        TestingRunResultSummary trrsA = insertTrrs(runA.getId(), State.RUNNING, NOW - 500, true, null, null);
        TestingRun runB = freshTestingRun(State.SCHEDULED, MODULE, null);

        DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-17", 360);

        assertEquals(DbLayer.VERDICT_RECLAIMED_ABANDONED, result.verdict);
        assertEquals(trrsA.getId(), result.trrs.getId());
        TestingRun stillScheduledB = TestingRunDao.instance.findOne(Filters.eq("_id", runB.getId()));
        assertEquals(State.SCHEDULED, stillScheduledB.getState());
        long trrsCountForB = TestingRunResultSummariesDao.instance.getMCollection()
                .countDocuments(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, runB.getId()));
        assertEquals("no TRRS should have been minted for the untouched sibling run", 0, trrsCountForB);
    }

    // ---- 18. race across two distinct fresh-pickup runs: exactly one winner per run, never 0 or 2+ ----
    @Test
    public void race_twoDistinctRuns_exactlyOneWinnerEach() throws Exception {
        TestingRun runA = freshTestingRun(State.SCHEDULED, MODULE, null);
        TestingRun runB = freshTestingRun(State.SCHEDULED, MODULE, null);

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger freshRunWins = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLine.await();
                    Context.accountId.set(ACCOUNT_ID);
                    DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "race-token-c-" + idx, 360);
                    if (DbLayer.VERDICT_FRESH_RUN.equals(result.verdict)) {
                        freshRunWins.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        startLine.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals("exactly one winner per distinct run, never fewer or more", 2, freshRunWins.get());
        long trrsCountA = TestingRunResultSummariesDao.instance.getMCollection()
                .countDocuments(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, runA.getId()));
        long trrsCountB = TestingRunResultSummariesDao.instance.getMCollection()
                .countDocuments(Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, runB.getId()));
        assertEquals(1, trrsCountA);
        assertEquals(1, trrsCountB);
    }

    // ---- 19. result.testingRun is correct for CICD/RERUN/RECLAIMED, not just FRESH_RUN ----
    @Test
    public void testingRunIsCorrect_forCicdRerunAndReclaimedVerdicts() {
        TestingRun cicdRun = freshTestingRun(State.SCHEDULED, MODULE, null);
        cicdRun.setTestRunTime(111);
        TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", cicdRun.getId()), com.mongodb.client.model.Updates.set("testRunTime", 111));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("repository", "akto-api-security/akto");
        insertTrrs(cicdRun.getId(), State.SCHEDULED, null, false, metadata, null);
        DbLayer.ClaimResult cicdResult = DbLayer.claimNextTestWork(MODULE, "token-19a", 360);
        assertEquals(DbLayer.VERDICT_CICD, cicdResult.verdict);
        assertNotNull(cicdResult.testingRun);
        assertEquals(cicdRun.getId(), cicdResult.testingRun.getId());
        assertEquals(111, cicdResult.testingRun.getTestRunTime());

        TestingRun rerunRun = freshTestingRun(State.SCHEDULED, MODULE, null);
        rerunRun.setTestRunTime(222);
        TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", rerunRun.getId()), com.mongodb.client.model.Updates.set("testRunTime", 222));
        insertTrrs(rerunRun.getId(), State.SCHEDULED, null, false, null, new ObjectId());
        DbLayer.ClaimResult rerunResult = DbLayer.claimNextTestWork(MODULE, "token-19b", 360);
        assertEquals(DbLayer.VERDICT_RERUN_SPECIFIC_TESTCASES, rerunResult.verdict);
        assertEquals(rerunRun.getId(), rerunResult.testingRun.getId());
        assertEquals(222, rerunResult.testingRun.getTestRunTime());

        TestingRun reclaimedRun = freshTestingRun(State.RUNNING, MODULE, null);
        reclaimedRun.setTestRunTime(333);
        TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", reclaimedRun.getId()), com.mongodb.client.model.Updates.set("testRunTime", 333));
        insertTrrs(reclaimedRun.getId(), State.RUNNING, NOW - 500, true, null, null);
        DbLayer.ClaimResult reclaimedResult = DbLayer.claimNextTestWork(MODULE, "token-19c", 360);
        assertEquals(DbLayer.VERDICT_RECLAIMED_ABANDONED, reclaimedResult.verdict);
        assertEquals(reclaimedRun.getId(), reclaimedResult.testingRun.getId());
        assertEquals(333, reclaimedResult.testingRun.getTestRunTime());
    }

    // ---- 20. orphan exclusion covers every terminal state, not just COMPLETED ----
    @Test
    public void orphanedTrrs_excludedForEveryTerminalParentState() {
        for (State terminal : new State[] { State.COMPLETED, State.FAILED, State.STOPPED }) {
            clearCollections();
            TestingRun terminalRun = freshTestingRun(terminal, MODULE, null);
            insertTrrs(terminalRun.getId(), State.RUNNING, NOW - 500, true, null, null);

            DbLayer.ClaimResult result = DbLayer.claimNextTestWork(MODULE, "token-20-" + terminal, 360);

            assertEquals("a " + terminal + " parent must never let its orphaned TRRS be resurrected",
                    DbLayer.VERDICT_NO_WORK_FOUND, result.verdict);
        }
    }
}
