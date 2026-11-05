package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.redteam_learnings.AgentRedteamObservationDao;
import com.akto.dao.redteam_learnings.AgentRedteamProfileDao;
import com.akto.dao.redteam_learnings.AgentRedteamTemplateDraftDao;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dto.redteam_learnings.AgentRedteamObservation;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.akto.dto.redteam_learnings.VerdictCounts;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.struts2.json.JSONPopulator;
import org.apache.struts2.json.JSONUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The four red-team learning endpoints, driven through the action with request bodies populated the way the Struts
 * JSON interceptor populates them, against Mongo.
 */
public class RedteamLearningsActionTest extends MongoBasedTest {

    private static final int COLLECTION = 1779231193;
    private static final String URL = "https://agent.example.com/chat";
    private static final String RUN = "665f1c2ab1e2c3d4e5f60718";
    private static final long NOW = System.currentTimeMillis();

    @Before
    public void empty() {
        Context.accountId.set(ACCOUNT_ID);
        AgentRedteamObservationDao.instance.getMCollection().deleteMany(new BasicDBObject());
        AgentRedteamProfileDao.instance.getMCollection().deleteMany(new BasicDBObject());
        AgentRedteamTemplateDraftDao.instance.getMCollection().deleteMany(new BasicDBObject());
        AgentConversationResultDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    private static RedteamLearningsAction action(String requestBody) throws Exception {
        RedteamLearningsAction action = new RedteamLearningsAction();
        new JSONPopulator().populateObject(action, (Map) JSONUtil.deserialize(requestBody));
        return action;
    }

    private static String identity() {
        return "\"apiCollectionId\":" + COLLECTION + ",\"url\":\"" + URL + "\",\"method\":\"post\",\"testingRunResultSummaryId\":\"" + RUN + "\"";
    }

    private static String observation(String id, String outcome, String verdict, String strategyKey) {
        return "{\"id\":\"" + id + "\",\"outcome\":\"" + outcome + "\"," + (verdict == null ? "" : "\"verdict\":\"" + verdict + "\",")
                + "\"agentBehaviorClass\":\"INSTRUCTION\",\"judgeConfidence\":80,\"testSubType\":\"SECURITY_EXCESSIVE_AGENCY_BASE64\","
                + "\"gridKey\":{\"basePromptKey\":\"bp1\",\"basePromptLabel\":\"Excessive agency\",\"strategyKey\":\"" + strategyKey + "\",\"strategyLabel\":\"Base64\"},"
                + "\"evidenceQuote\":\"  quote  \",\"judgeReasoning\":\"why\",\"toolsSeen\":[\"search\",\"search\",\" \"],"
                + "\"turns\":4,\"responseChars\":15000,\"eventTimeMs\":" + NOW + ",\"apiCollectionId\":1,\"url\":\"spoofed\"}";
    }

    private static Map<String, Object> store(String... observations) throws Exception {
        RedteamLearningsAction action = action("{" + identity() + ",\"observations\":[" + String.join(",", observations) + "]}");
        assertEquals("SUCCESS", action.storeRedteamObservations());
        return action.getResponse();
    }

    private static String insight(String id) {
        return "{\"id\":\"" + id + "\",\"kind\":\"BYPASS\",\"text\":\"Base64 framing gets past the scope check\",\"importance\":3,"
                + "\"evidenceObservationIds\":[\"c1\"]}";
    }

    private static String draft(String basedOnInsight) {
        return "{\"name\":\"Scope escape via export\",\"basePrompt\":\"Ask for an export of all HCPs\",\"strategyKeys\":[\"s1\",\"s2\"],"
                + "\"basedOnInsightIds\":[\"" + basedOnInsight + "\"],\"yamlContent\":\"id: X\",\"status\":\"SAVED\",\"id\":\"client-chosen\"}";
    }

    private static RedteamLearningsAction save(int batchNo, String insights, String drafts) throws Exception {
        return action("{" + identity() + ",\"batchNo\":" + batchNo + ",\"finalVersion\":false,\"insights\":[" + insights + "]"
                + (drafts == null ? "" : ",\"drafts\":[" + drafts + "]") + "}");
    }

    @Test
    public void storesObservationsWithServerOwnedIdentityAndExpiry() throws Exception {
        Map<String, Object> response = store(
                observation("c1", "JUDGED", "2", "s1"),
                observation("c2", "TARGET_ERROR", "2", "s1"),
                observation("c3", "JUDGED", "7", "s1"),
                observation(" ", "JUDGED", "0", "s1"));

        assertEquals(2, response.get("stored"));
        List<?> rejected = (List<?>) response.get("rejected");
        assertEquals(2, rejected.size());
        assertTrue(rejected.get(0).toString().startsWith("2: "));
        assertTrue(rejected.get(1).toString().startsWith("3: "));
        assertEquals(1L, response.get("judgedCount"));
        assertEquals(1L, response.get("unjudgedCount"));

        AgentRedteamObservation stored = AgentRedteamObservationDao.instance.findOne(Filters.eq("_id", "c1"));
        assertEquals(COLLECTION, stored.getApiCollectionId());
        assertEquals(URL, stored.getUrl());
        assertEquals("POST", stored.getMethod());
        assertEquals(RUN, stored.getTestingRunResultSummaryId());
        assertEquals("quote", stored.getEvidenceQuote());
        assertEquals(Collections.singletonList("search"), stored.getToolsSeen());
        assertEquals("bp1", stored.getGridKey().getBasePromptKey());
        assertEquals(NOW + 30L * 24 * 3600 * 1000, stored.getExpireAt().getTime());
        assertNull("only a judged conversation has a verdict",
                AgentRedteamObservationDao.instance.findOne(Filters.eq("_id", "c2")).getVerdict());
    }

    @Test
    public void aRetriedBatchReplacesInsteadOfAdding() throws Exception {
        store(observation("c1", "JUDGED", "2", "s1"));
        Map<String, Object> response = store(observation("c1", "JUDGED", "1", "s1"));
        assertEquals(1L, response.get("judgedCount"));
        assertEquals(1, AgentRedteamObservationDao.instance.count(new BasicDBObject()));
        assertEquals("1", AgentRedteamObservationDao.instance.findOne(Filters.eq("_id", "c1")).getVerdict());
    }

    @Test
    public void aRequestWithoutAValidIdentityIsRefused() throws Exception {
        RedteamLearningsAction noUrl = action("{\"apiCollectionId\":1,\"method\":\"POST\",\"testingRunResultSummaryId\":\"r\",\"observations\":[]}");
        assertEquals("ERROR", noUrl.storeRedteamObservations());
        assertTrue(noUrl.getActionErrors().iterator().next().contains("url"));

        RedteamLearningsAction badSummary = action("{\"apiCollectionId\":1,\"url\":\"u\",\"method\":\"POST\",\"testingRunResultSummaryId\":\"../x\","
                + "\"observations\":[" + observation("c1", "JUDGED", "0", "s1") + "]}");
        assertEquals("ERROR", badSummary.storeRedteamObservations());

        RedteamLearningsAction badMethod = action("{\"apiCollectionId\":1,\"url\":\"u\",\"method\":\"TRACE\"}");
        assertEquals("ERROR", badMethod.fetchLatestRedteamProfile());
        assertEquals(0, AgentRedteamObservationDao.instance.count(new BasicDBObject()));
    }

    @Test
    public void consolidationInputJoinsTheStoredTurnsMostTellingFirst() throws Exception {
        store(observation("c0", "JUDGED", "0", "s1"), observation("c1", "JUDGED", "2", "s1"),
                observation("c2", "JUDGED", "1", "s2"), observation("c3", "JUDGE_ERROR", null, "s2"));
        List<AgentConversationResult> turns = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            AgentConversationResult turn = new AgentConversationResult();
            turn.setConversationId("c1");
            turn.setFinalSentPrompt("sent " + i);
            turn.setResponse("reply " + i);
            turn.setPrompt("tester instruction " + i);
            turns.add(turn);
        }
        AgentConversationResultDao.instance.insertMany(turns);

        RedteamLearningsAction action = action("{" + identity() + ",\"samplesPerVerdict\":5}");
        assertEquals("SUCCESS", action.fetchRedteamConsolidationInput());
        Map<String, Object> response = action.getResponse();

        VerdictCounts totals = (VerdictCounts) response.get("totals");
        assertEquals(3, totals.getJudged());
        assertEquals(1, totals.getCritical());
        assertEquals(1L, response.get("unjudgedCount"));
        assertEquals(2, ((List<?>) response.get("cells")).size());

        List<Map<String, Object>> samples = (List<Map<String, Object>>) response.get("samples");
        assertEquals(3, samples.size());
        assertEquals("c1", ((AgentRedteamObservation) samples.get(0).get("observation")).getId());
        List<Map<String, Object>> c1Turns = (List<Map<String, Object>>) samples.get(0).get("turns");
        assertEquals(2, c1Turns.size());
        assertEquals("sent 1", c1Turns.get(0).get("sentPrompt"));
        assertEquals("reply 2", c1Turns.get(1).get("response"));
        assertFalse("the tester's instruction is not part of a turn", c1Turns.get(0).containsKey("prompt"));
        assertEquals(true, samples.get(0).get("turnsAvailable"));
        assertEquals("not stored yet is reported, not hidden", false, samples.get(1).get("turnsAvailable"));

        assertNull(response.get("latestProfile"));
        assertNull(response.get("lastBatchNoInRun"));
        assertEquals(false, response.get("finalVersionSavedInRun"));
    }

    @Test
    public void anEmptyOrInsightlessVersionIsNeverSaved() throws Exception {
        RedteamLearningsAction nothingJudged = save(0, insight("i1"), null);
        assertEquals("ERROR", nothingJudged.saveRedteamProfile());

        store(observation("c1", "JUDGED", "2", "s1"));
        RedteamLearningsAction noInsights = save(0, "", null);
        assertEquals("ERROR", noInsights.saveRedteamProfile());

        RedteamLearningsAction uncited = save(0, "{\"id\":\"i1\",\"kind\":\"BYPASS\",\"text\":\"t\",\"importance\":2}", null);
        assertEquals("ERROR", uncited.saveRedteamProfile());
        assertEquals(0, AgentRedteamProfileDao.instance.count(new BasicDBObject()));
    }

    @Test
    public void savesOneVersionPerBatchWithCountsFromTheDatabase() throws Exception {
        store(observation("c1", "JUDGED", "2", "s1"), observation("c2", "JUDGED", "0", "s2"));

        RedteamLearningsAction first = save(1, insight("i1"), draft("i1"));
        assertEquals("SUCCESS", first.saveRedteamProfile());
        RedteamLearningsAction again = save(1, insight("i1"), draft("i1"));
        assertEquals("SUCCESS", again.saveRedteamProfile());

        String versionId = AgentRedteamProfile.buildVersionId(COLLECTION, "POST", URL, RUN, 1);
        assertEquals(versionId, again.getResponse().get("profileVersionId"));
        assertEquals(1, AgentRedteamProfileDao.instance.count(new BasicDBObject()));
        AgentRedteamProfile saved = AgentRedteamProfileDao.instance.findOne(Filters.eq("_id", versionId));
        assertEquals(2, saved.getTotals().getJudged());
        assertEquals(1, saved.getTotals().getCritical());
        assertEquals(2, saved.getCells().size());

        assertEquals(1, first.getResponse().get("draftsInserted"));
        assertEquals("an existing draft is not inserted again", 0, again.getResponse().get("draftsInserted"));
        AgentRedteamTemplateDraft draft = AgentRedteamTemplateDraftDao.instance.findOne(new BasicDBObject());
        assertEquals(AgentRedteamTemplateDraft.STATUS_DRAFT, draft.getStatus());
        assertTrue("the id is built by the server", draft.getId().startsWith(COLLECTION + ":POST:"));
        assertEquals(versionId, draft.getProfileVersionId());
    }

    @Test
    public void aDismissedDraftStaysDismissedWhenSuggestedAgain() throws Exception {
        store(observation("c1", "JUDGED", "2", "s1"));
        assertEquals("SUCCESS", save(1, insight("i1"), draft("i1")).saveRedteamProfile());
        AgentRedteamTemplateDraftDao.instance.getMCollection().updateMany(new BasicDBObject(),
                Updates.set(AgentRedteamTemplateDraft.STATUS, AgentRedteamTemplateDraft.STATUS_DISMISSED));

        assertEquals("SUCCESS", save(2, insight("i9"), draft("i9")).saveRedteamProfile());
        assertEquals(1, AgentRedteamTemplateDraftDao.instance.count(new BasicDBObject()));
        assertEquals(AgentRedteamTemplateDraft.STATUS_DISMISSED, AgentRedteamTemplateDraftDao.instance.findOne(new BasicDBObject()).getStatus());
    }

    @Test
    public void aDraftMustRestOnAnInsightInTheSameRequest() throws Exception {
        store(observation("c1", "JUDGED", "2", "s1"));
        RedteamLearningsAction action = save(1, insight("i1"), draft("not-an-insight"));
        assertEquals("ERROR", action.saveRedteamProfile());
        assertEquals(0, AgentRedteamProfileDao.instance.count(new BasicDBObject()));
    }

    @Test
    public void latestProfileIsNoneUntilLearnedAndIsScopedToTheAccount() throws Exception {
        RedteamLearningsAction before = action("{" + identity() + "}");
        assertEquals("SUCCESS", before.fetchLatestRedteamProfile());
        assertEquals("NONE", before.getResponse().get("state"));

        store(observation("c1", "JUDGED", "2", "s1"));
        assertEquals("SUCCESS", save(1, insight("i1"), null).saveRedteamProfile());

        RedteamLearningsAction after = action("{" + identity() + "}");
        assertEquals("SUCCESS", after.fetchLatestRedteamProfile());
        assertEquals("READY", after.getResponse().get("state"));
        AgentRedteamProfile profile = (AgentRedteamProfile) after.getResponse().get("profile");
        assertNotNull(profile);
        assertEquals(1, profile.getInsights().size());
        assertTrue("the enhancer read leaves the grid out", profile.getCells().isEmpty());

        Context.accountId.set(ACCOUNT_ID + 1);
        try {
            RedteamLearningsAction otherAccount = action("{" + identity() + "}");
            assertEquals("SUCCESS", otherAccount.fetchLatestRedteamProfile());
            assertEquals("NONE", otherAccount.getResponse().get("state"));
        } finally {
            Context.accountId.set(ACCOUNT_ID);
        }
    }
}
