package com.akto.dao.redteam_learnings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dto.redteam_learnings.AgentRedteamObservation;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.GridCell;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/** The database-abstractor side of the red-team learning DAOs: index self-healing, idempotent writes, and the run queries. */
public class TestAgentRedteamDaos extends MongoBasedTest {

    private static final String URL = "https://agent.example/chat";
    private static final String RUN = "run-1";

    @Before
    public void clean() {
        Context.accountId.set(ACCOUNT_ID);
        AgentRedteamObservationDao.instance.getMCollection().drop();
        AgentRedteamProfileDao.instance.getMCollection().drop();
        AgentRedteamTemplateDraftDao.instance.getMCollection().drop();
    }

    private static AgentRedteamObservation observation(String id, String run, int apiCollectionId, String outcome, String verdict,
                                                       String basePromptKey, String strategyKey, long eventTimeMs) {
        AgentRedteamObservation o = new AgentRedteamObservation();
        o.setId(id);
        o.setApiCollectionId(apiCollectionId);
        o.setUrl(URL);
        o.setMethod("POST");
        o.setTestingRunResultSummaryId(run);
        o.setOutcome(outcome);
        o.setVerdict(verdict);
        o.setTestSubType("SUB_" + id);
        o.getGridKey().setBasePromptKey(basePromptKey);
        o.getGridKey().setBasePromptLabel(basePromptKey == null ? null : "label " + basePromptKey);
        o.getGridKey().setStrategyKey(strategyKey);
        o.getGridKey().setStrategyLabel(strategyKey == null ? null : "label " + strategyKey);
        o.setJudgeReasoning("reasoning " + id);
        o.setEventTimeMs(eventTimeMs);
        return o;
    }

    private static AgentRedteamObservation judged(String id, String verdict, String basePromptKey, String strategyKey) {
        return observation(id, RUN, 123, AgentRedteamObservation.OUTCOME_JUDGED, verdict, basePromptKey, strategyKey, 1000);
    }

    private static AgentRedteamProfile profile(String run, int batchNo, long createdAtMs) {
        AgentRedteamProfile p = new AgentRedteamProfile();
        p.setId("123:POST:h:" + run + ":" + batchNo);
        p.setApiCollectionId(123);
        p.setUrl(URL);
        p.setMethod("POST");
        p.setTestingRunResultSummaryId(run);
        p.setBatchNo(batchNo);
        p.setCreatedAtMs(createdAtMs);
        return p;
    }

    private static AgentRedteamTemplateDraft draft(String id) {
        AgentRedteamTemplateDraft d = new AgentRedteamTemplateDraft();
        d.setId(id);
        d.setApiCollectionId(123);
        d.setUrl(URL);
        d.setMethod("POST");
        d.setTestingRunResultSummaryId(RUN);
        return d;
    }

    // ── index self-healing ───────────────────────────────────────────────────

    @Test
    public void theFirstWriteOfAnAccountMakesEveryIndex() {
        Context.accountId.set(ACCOUNT_ID + 1); // an account this process has not touched yet
        try {
            for (AgentRedteamDao<?> dao : Arrays.<AgentRedteamDao<?>>asList(AgentRedteamObservationDao.instance,
                    AgentRedteamProfileDao.instance, AgentRedteamTemplateDraftDao.instance)) {
                assertEquals(dao.indexNames(), dao.missingIndexNames());
                dao.ensureIndicesOnce();
                dao.ensureIndicesOnce();
                assertTrue(dao.getCollName() + " still misses " + dao.missingIndexNames(), dao.missingIndexNames().isEmpty());
            }
        } finally {
            Context.accountId.set(ACCOUNT_ID);
        }
    }

    // ── observations ─────────────────────────────────────────────────────────

    @Test
    public void aRetriedBatchReplacesInsteadOfAdding() {
        AgentRedteamObservationDao.instance.upsertAll(Arrays.asList(judged("c1", "0", "bp1", "st1"), judged("c2", "1", "bp1", "st1")));
        AgentRedteamObservationDao.instance.upsertAll(Arrays.asList(judged("c1", "2", "bp1", "st1"), judged("c2", "1", "bp1", "st1")));

        assertEquals(2, AgentRedteamObservationDao.instance.getMCollection().countDocuments());
        assertEquals("2", AgentRedteamObservationDao.instance.findOne("_id", "c1").getVerdict());
    }

    @Test
    public void judgedAndUnjudgedAreCountedApartForThatRunAndAgentOnly() {
        AgentRedteamObservationDao.instance.upsertAll(Arrays.asList(
                judged("c1", "0", "bp1", "st1"), judged("c2", "2", "bp1", "st1"),
                observation("c3", RUN, 123, AgentRedteamObservation.OUTCOME_TARGET_ERROR, null, "bp1", "st1", 1),
                observation("c4", "run-2", 123, AgentRedteamObservation.OUTCOME_JUDGED, "0", "bp1", "st1", 1),
                observation("c5", RUN, 456, AgentRedteamObservation.OUTCOME_JUDGED, "0", "bp1", "st1", 1)));

        assertEquals(2, AgentRedteamObservationDao.instance.countJudged(RUN, 123, URL, "POST"));
        assertEquals(1, AgentRedteamObservationDao.instance.countUnjudged(RUN, 123, URL, "POST"));
        assertEquals(0, AgentRedteamObservationDao.instance.countJudged(RUN, 123, URL, "GET"));
    }

    @Test
    public void cellsCountVerdictsPerBasePromptAndStrategy() {
        AgentRedteamObservationDao.instance.upsertAll(Arrays.asList(
                judged("a1", "2", "bp1", "st1"), judged("a2", "1", "bp1", "st1"), judged("a3", "0", "bp1", "st1"),
                judged("a4", "0", "bp1", "st1"), judged("b1", "2", "bp1", "st2"),
                judged("n1", "0", null, null),
                observation("e1", RUN, 123, AgentRedteamObservation.OUTCOME_JUDGE_ERROR, null, "bp1", "st1", 1),
                observation("o1", "run-2", 123, AgentRedteamObservation.OUTCOME_JUDGED, "2", "bp1", "st1", 1)));

        List<GridCell> cells = AgentRedteamObservationDao.instance.aggregateCells(RUN, 123, URL, "POST");

        assertEquals(3, cells.size());
        GridCell biggest = cells.get(0);
        assertEquals("bp1", biggest.getGridKey().getBasePromptKey());
        assertEquals("st1", biggest.getGridKey().getStrategyKey());
        assertEquals("label st1", biggest.getGridKey().getStrategyLabel());
        assertEquals("label bp1", biggest.getGridKey().getBasePromptLabel());
        assertEquals(Arrays.asList(4, 1, 1, 2), Arrays.asList(biggest.getCounts().getJudged(), biggest.getCounts().getCritical(),
                biggest.getCounts().getAttempted(), biggest.getCounts().getNormal()));
        assertNull("a keyed cell is not split by test", biggest.getTestSubType());
        GridCell unkeyed = cells.stream().filter(c -> c.getGridKey().getStrategyKey() == null).findFirst().get();
        assertEquals("SUB_n1", unkeyed.getTestSubType());
        assertEquals(1, unkeyed.getCounts().getNormal());
    }

    @Test
    public void samplesAreTheNewestOfOneVerdictWithOnlyWhatConsolidationReads() {
        AgentRedteamObservationDao.instance.upsertAll(Arrays.asList(
                observation("old", RUN, 123, AgentRedteamObservation.OUTCOME_JUDGED, "2", "bp1", "st1", 1000),
                observation("new", RUN, 123, AgentRedteamObservation.OUTCOME_JUDGED, "2", "bp1", "st1", 3000),
                observation("mid", RUN, 123, AgentRedteamObservation.OUTCOME_JUDGED, "2", "bp1", "st1", 2000),
                observation("zero", RUN, 123, AgentRedteamObservation.OUTCOME_JUDGED, "0", "bp1", "st1", 9000)));

        List<AgentRedteamObservation> samples = AgentRedteamObservationDao.instance.findSamples(RUN, 123, URL, "POST", "2", 2);

        assertEquals(Arrays.asList("new", "mid"), samples.stream().map(AgentRedteamRecord::getId).collect(Collectors.toList()));
        assertEquals("reasoning new", samples.get(0).getJudgeReasoning());
        assertEquals("st1", samples.get(0).getGridKey().getStrategyKey());
        assertNull("not projected", samples.get(0).getUrl());
    }

    // ── profiles ─────────────────────────────────────────────────────────────

    @Test
    public void savingAVersionAgainRewritesIt() {
        AgentRedteamProfile first = profile(RUN, 1, 1000);
        AgentRedteamProfileDao.instance.saveVersion(first);
        first.setUnjudgedConversations(5);
        AgentRedteamProfileDao.instance.saveVersion(first);

        assertEquals(1, AgentRedteamProfileDao.instance.getMCollection().countDocuments());
        assertEquals(5, AgentRedteamProfileDao.instance.findOne("_id", first.getId()).getUnjudgedConversations());
    }

    @Test
    public void latestOverallAndLatestInARunAreDifferentQuestions() {
        AgentRedteamProfileDao.instance.saveVersion(profile(RUN, 1, 1000));
        AgentRedteamProfileDao.instance.saveVersion(profile(RUN, 2, 2000));
        AgentRedteamProfileDao.instance.saveVersion(profile("run-2", 1, 3000));

        assertEquals("123:POST:h:run-2:1", AgentRedteamProfileDao.instance.findLatest(123, URL, "POST").getId());
        assertEquals(2, AgentRedteamProfileDao.instance.findLatestInRun(RUN, 123, URL, "POST").getBatchNo());
        assertNull(AgentRedteamProfileDao.instance.findLatestInRun("run-3", 123, URL, "POST"));
        assertNull(AgentRedteamProfileDao.instance.findLatest(999, URL, "POST"));
    }

    // ── drafts ───────────────────────────────────────────────────────────────

    @Test
    public void aRepeatedSuggestionKeepsTheStatusTheUserGaveIt() {
        assertEquals(2, AgentRedteamTemplateDraftDao.instance.insertNew(Arrays.asList(draft("d1"), draft("d2"))));
        AgentRedteamTemplateDraftDao.instance.getMCollection().updateOne(Filters.eq("_id", "d1"),
                Updates.set(AgentRedteamTemplateDraft.STATUS, AgentRedteamTemplateDraft.STATUS_DISMISSED));

        assertEquals(1, AgentRedteamTemplateDraftDao.instance.insertNew(Arrays.asList(draft("d1"), draft("d2"), draft("d3"))));

        assertEquals(3, AgentRedteamTemplateDraftDao.instance.getMCollection().countDocuments());
        assertEquals(AgentRedteamTemplateDraft.STATUS_DISMISSED, AgentRedteamTemplateDraftDao.instance.findOne("_id", "d1").getStatus());
        assertEquals(0, AgentRedteamTemplateDraftDao.instance.insertNew(null));
    }
}
