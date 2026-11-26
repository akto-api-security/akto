package com.akto.dto.redteam_learnings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.Test;

import com.akto.DaoInit;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.GridCell;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.Insight;

/**
 * The storage shape of the red-team learning records, through the same codec registry the DAOs use. No Mongo is
 * needed: what matters here is what is written (the _id, the Date a TTL index needs, no class-name discriminator
 * that another version could not read) and what an older or newer writer's document reads back as.
 */
public class AgentRedteamDtoCodecTest {

    private static final CodecRegistry REGISTRY = DaoInit.createCodecRegistry();

    private static <T> BsonDocument encode(T value, Class<T> clazz) {
        BsonDocument doc = new BsonDocument();
        REGISTRY.get(clazz).encode(new BsonDocumentWriter(doc), value, EncoderContext.builder().build());
        return doc;
    }

    private static <T> T decode(BsonDocument doc, Class<T> clazz) {
        return REGISTRY.get(clazz).decode(new BsonDocumentReader(doc), DecoderContext.builder().build());
    }

    private static <T> T roundTrip(T value, Class<T> clazz) {
        return decode(encode(value, clazz), clazz);
    }

    private static TemplateGridKey gridKey() {
        TemplateGridKey key = new TemplateGridKey();
        key.setBasePromptKey("bp1");
        key.setBasePromptLabel("Competitor check");
        key.setStrategyKey("st1");
        key.setStrategyLabel("Base64");
        return key;
    }

    private static AgentRedteamObservation observation() {
        AgentRedteamObservation o = new AgentRedteamObservation();
        o.setId("conv-1");
        o.setApiCollectionId(123);
        o.setUrl("https://agent.example/chat");
        o.setMethod("POST");
        o.setTestingRunResultSummaryId("65f1c0a9e4b0a1b2c3d4e5f6");
        o.setOutcome(AgentRedteamObservation.OUTCOME_JUDGED);
        o.setVerdict(AgentRedteamObservation.VERDICT_CRITICAL);
        o.setTestSubType("BUSINESS_ALIGNMENT_COMPETITOR_CHECK_BASE64");
        o.setGridKey(gridKey());
        o.setToolsSeen(Arrays.asList("search", "send_email"));
        o.setEventTimeMs(1_790_000_000_123L);
        o.setCreatedAtMs(1_790_000_000_456L);
        o.setExpireAt(new Date(1_792_000_000_000L));
        return o;
    }

    private static AgentRedteamProfile profile() {
        AgentRedteamProfile profile = new AgentRedteamProfile();
        profile.setId("123:POST:abc:sum:2");
        profile.setApiCollectionId(123);
        profile.setBatchNo(2);
        profile.setFinalVersion(true);
        profile.getTotals().setJudged(200);
        profile.getTotals().setCritical(3);
        profile.setUnjudgedConversations(7);
        GridCell cell = new GridCell();
        cell.setGridKey(gridKey());
        cell.getCounts().setJudged(10);
        cell.getCounts().setCritical(3);
        profile.getCells().add(cell);
        Insight insight = new Insight();
        insight.setId("i1");
        insight.setKind(Insight.KIND_ADVICE);
        insight.setImportance(2);
        insight.setEvidenceObservationIds(Collections.singletonList("conv-1"));
        profile.getInsights().add(insight);
        return profile;
    }

    @Test
    public void observationIsStoredUnderItsIdWithTheIdentityAtTopLevel() {
        BsonDocument doc = encode(observation(), AgentRedteamObservation.class);

        assertEquals(new BsonString("conv-1"), doc.get("_id"));
        assertFalse("id must be stored as _id only", doc.containsKey("id"));
        assertEquals(new BsonInt32(123), doc.get(AgentRedteamRecord.API_COLLECTION_ID));
        assertEquals(new BsonString("https://agent.example/chat"), doc.get(AgentRedteamRecord.URL));
        assertEquals(new BsonString("POST"), doc.get(AgentRedteamRecord.METHOD));
        assertEquals(new BsonString("65f1c0a9e4b0a1b2c3d4e5f6"), doc.get(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID));
    }

    @Test
    public void observationCarriesBothKeysAndLabelsAGridCellIsBuiltFrom() {
        BsonDocument key = encode(observation(), AgentRedteamObservation.class).getDocument(AgentRedteamObservation.GRID_KEY);

        assertEquals(new BsonString("bp1"), key.get(TemplateGridKey.BASE_PROMPT_KEY));
        assertEquals(new BsonString("Competitor check"), key.get("basePromptLabel"));
        assertEquals(new BsonString("st1"), key.get(TemplateGridKey.STRATEGY_KEY));
        assertEquals(new BsonString("Base64"), key.get("strategyLabel"));
    }

    @Test
    public void observationTimesKeepMillisecondsAndItsExpiryIsADateForTheTtlIndex() {
        BsonDocument doc = encode(observation(), AgentRedteamObservation.class);

        assertTrue(doc.get(AgentRedteamObservation.EXPIRE_AT) instanceof BsonDateTime);
        assertEquals(1_792_000_000_000L, doc.getDateTime(AgentRedteamObservation.EXPIRE_AT).getValue());
        assertEquals(1_790_000_000_123L, doc.getInt64(AgentRedteamObservation.EVENT_TIME_MS).getValue());
        assertEquals(1_790_000_000_456L, doc.getInt64(AgentRedteamRecord.CREATED_AT_MS).getValue());
    }

    @Test
    public void nothingIsWrittenWithAClassNameDiscriminator() {
        assertFalse(encode(observation(), AgentRedteamObservation.class).containsKey("_t"));

        BsonDocument doc = encode(profile(), AgentRedteamProfile.class);
        assertFalse(doc.containsKey("_t"));
        assertFalse(doc.getDocument("totals").containsKey("_t"));
        assertFalse(doc.getArray("cells").get(0).asDocument().containsKey("_t"));
        assertFalse(doc.getArray("insights").get(0).asDocument().containsKey("_t"));
    }

    @Test
    public void observationRoundTripsAndKeepsUnknownAsNull() {
        AgentRedteamObservation back = roundTrip(observation(), AgentRedteamObservation.class);

        assertEquals("conv-1", back.getId());
        assertEquals(123, back.getApiCollectionId());
        assertEquals(AgentRedteamObservation.VERDICT_CRITICAL, back.getVerdict());
        assertEquals("Base64", back.getGridKey().getStrategyLabel());
        assertEquals(Arrays.asList("search", "send_email"), back.getToolsSeen());
        assertEquals(new Date(1_792_000_000_000L), back.getExpireAt());
        assertNull("an unreported confidence is not 0", back.getJudgeConfidence());
        assertNull(back.getTestCategory());
    }

    @Test
    public void anUnjudgedObservationHasNoVerdict() {
        AgentRedteamObservation o = observation();
        o.setOutcome(AgentRedteamObservation.OUTCOME_TARGET_ERROR);
        o.setVerdict(null);

        AgentRedteamObservation back = roundTrip(o, AgentRedteamObservation.class);

        assertEquals(AgentRedteamObservation.OUTCOME_TARGET_ERROR, back.getOutcome());
        assertNull(back.getVerdict());
    }

    @Test
    public void profileRoundTripsWithItsTotalsCellsAndInsights() {
        AgentRedteamProfile back = roundTrip(profile(), AgentRedteamProfile.class);

        assertEquals("123:POST:abc:sum:2", back.getId());
        assertTrue(back.isFinalVersion());
        assertEquals(200, back.getTotals().getJudged());
        assertEquals(7, back.getUnjudgedConversations());
        GridCell cell = back.getCells().get(0);
        assertEquals("Base64", cell.getGridKey().getStrategyLabel());
        assertEquals(3, cell.getCounts().getCritical());
        assertNull(cell.getTestSubType());
        assertEquals(Insight.KIND_ADVICE, back.getInsights().get(0).getKind());
        assertEquals(Collections.singletonList("conv-1"), back.getInsights().get(0).getEvidenceObservationIds());
    }

    @Test
    public void aNewDraftIsADraftFromTheLearningSource() {
        AgentRedteamTemplateDraft draft = new AgentRedteamTemplateDraft();

        assertEquals(AgentRedteamTemplateDraft.STATUS_DRAFT, draft.getStatus());
        assertEquals(AgentRedteamTemplateDraft.SOURCE_REDTEAM_LEARNING, draft.getSource());
        assertNull(draft.getSavedTemplateId());
    }

    @Test
    public void draftKeepsItsStatusAndSavedTemplateThroughARoundTrip() {
        AgentRedteamTemplateDraft draft = new AgentRedteamTemplateDraft();
        draft.setId("draft-hash");
        draft.setStatus(AgentRedteamTemplateDraft.STATUS_SAVED);
        draft.setSavedTemplateId("CUSTOM_1790000000");
        draft.setStrategyKeys(Arrays.asList("st1", "st2"));

        AgentRedteamTemplateDraft back = roundTrip(draft, AgentRedteamTemplateDraft.class);

        assertEquals(AgentRedteamTemplateDraft.STATUS_SAVED, back.getStatus());
        assertEquals("CUSTOM_1790000000", back.getSavedTemplateId());
        assertEquals(Arrays.asList("st1", "st2"), back.getStrategyKeys());
    }

    @Test
    public void aDocumentFromAnotherWriterReadsBackWithEmptyListsAndIgnoresUnknownFields() {
        BsonDocument shellDoc = new BsonDocument()
            .append("_id", new BsonString("draft-x"))
            .append(AgentRedteamRecord.API_COLLECTION_ID, new BsonInt32(5))
            .append(AgentRedteamTemplateDraft.STATUS, new BsonString(AgentRedteamTemplateDraft.STATUS_DISMISSED))
            .append("aFieldAFutureVersionAdds", new BsonArray(Collections.singletonList(new BsonString("x"))));

        AgentRedteamTemplateDraft back = decode(shellDoc, AgentRedteamTemplateDraft.class);

        assertEquals("draft-x", back.getId());
        assertEquals(AgentRedteamTemplateDraft.STATUS_DISMISSED, back.getStatus());
        assertTrue("an absent list reads as an empty list", back.getStrategyKeys().isEmpty());
        assertTrue(back.getBasedOnInsightIds().isEmpty());
        assertTrue(back.getEvidenceObservationIds().isEmpty());
    }

    @Test
    public void aVersionIdIsTheIdentityRunAndBatch() {
        String id = AgentRedteamProfile.buildVersionId(42, "POST", "https://a.example/chat", "run-1", 3);
        assertTrue(id.matches("42:POST:[0-9a-f]{16}:run-1:3"));
        assertEquals(id, AgentRedteamProfile.buildVersionId(42, "POST", "https://a.example/chat", "run-1", 3));
        assertFalse(id.equals(AgentRedteamProfile.buildVersionId(42, "POST", "https://a.example/other", "run-1", 3)));
    }

    @Test
    public void theSameSuggestionGetsTheSameDraftIdWhateverItsFormatting() {
        String id = AgentRedteamTemplateDraft.buildId(42, "POST", "u", "Ask for  ALL records", Arrays.asList("s2", "s1"), null);
        assertEquals(id, AgentRedteamTemplateDraft.buildId(42, "POST", "u", " ask for all records ", Arrays.asList("s1", "s2"), null));
        assertFalse(id.equals(AgentRedteamTemplateDraft.buildId(42, "POST", "u", "Ask for all records", Arrays.asList("s1"), null)));
        assertFalse(id.equals(AgentRedteamTemplateDraft.buildId(42, "POST", "u", "Ask for all records", Arrays.asList("s1", "s2"), "JAILBREAK")));
        assertFalse("another agent never shares a draft", id.equals(AgentRedteamTemplateDraft.buildId(43, "POST", "u", "Ask for all records", Arrays.asList("s1", "s2"), null)));
    }
}
