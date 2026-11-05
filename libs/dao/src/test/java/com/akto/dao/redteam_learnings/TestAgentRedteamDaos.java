package com.akto.dao.redteam_learnings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.akto.MongoBasedTest;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.Insight;

/** The dashboard side of the red-team learning DAOs: their indexes, the reads the pages make, and RBAC scoping. */
public class TestAgentRedteamDaos extends MongoBasedTest {

    private static final String URL = "https://agent.example/chat";
    private static final String CHANGELOG = "../utils/src/main/resources/db/changelog/account/034-agent-redteam-learnings.xml";

    @Before
    public void clean() {
        AgentRedteamObservationDao.instance.getMCollection().drop();
        AgentRedteamProfileDao.instance.getMCollection().drop();
        AgentRedteamTemplateDraftDao.instance.getMCollection().drop();
    }

    @After
    public void clearUser() {
        Context.userId.remove();
    }

    private static Map<String, Document> indexes(MCollection<?> dao) {
        Map<String, Document> byName = new HashMap<>();
        for (Document index : dao.getMCollection().listIndexes()) {
            byName.put(index.getString("name"), index);
        }
        return byName;
    }

    private static AgentRedteamProfile profile(int apiCollectionId, String summaryId, int batchNo, long createdAtMs) {
        AgentRedteamProfile p = new AgentRedteamProfile();
        p.setId(apiCollectionId + ":POST:h:" + summaryId + ":" + batchNo);
        p.setApiCollectionId(apiCollectionId);
        p.setUrl(URL);
        p.setMethod("POST");
        p.setTestingRunResultSummaryId(summaryId);
        p.setBatchNo(batchNo);
        p.setCreatedAtMs(createdAtMs);
        Insight insight = new Insight();
        insight.setId("i-" + batchNo);
        insight.setEvidenceObservationIds(Collections.singletonList("conv-1"));
        p.getInsights().add(insight);
        p.getCells().add(new AgentRedteamProfile.GridCell());
        return p;
    }

    private static AgentRedteamTemplateDraft draft(String id, int apiCollectionId, String status, long createdAtMs) {
        AgentRedteamTemplateDraft d = new AgentRedteamTemplateDraft();
        d.setId(id);
        d.setApiCollectionId(apiCollectionId);
        d.setUrl(URL);
        d.setMethod("POST");
        d.setTestingRunResultSummaryId("run-1");
        d.setStatus(status);
        d.setCreatedAtMs(createdAtMs);
        return d;
    }

    // ── indexes ──────────────────────────────────────────────────────────────

    @Test
    public void createIndicesMakesTheNamedIndexesAndCanRunAgain() {
        for (int i = 0; i < 2; i++) {
            AgentRedteamObservationDao.instance.createIndicesIfAbsent();
            AgentRedteamProfileDao.instance.createIndicesIfAbsent();
            AgentRedteamTemplateDraftDao.instance.createIndicesIfAbsent();
        }

        Map<String, Document> observation = indexes(AgentRedteamObservationDao.instance);
        assertTrue(observation.containsKey(AgentRedteamObservationDao.RUN_IDENTITY_OUTCOME_INDEX));
        assertEquals(0L, ((Number) observation.get(AgentRedteamObservationDao.EXPIRE_AT_TTL_INDEX).get("expireAfterSeconds")).longValue());
        Map<String, Document> profile = indexes(AgentRedteamProfileDao.instance);
        assertTrue(profile.containsKey(AgentRedteamProfileDao.IDENTITY_CREATED_AT_INDEX));
        assertTrue(profile.containsKey(AgentRedteamProfileDao.RUN_BATCH_INDEX));
        Map<String, Document> drafts = indexes(AgentRedteamTemplateDraftDao.instance);
        assertTrue(drafts.containsKey(AgentRedteamTemplateDraftDao.IDENTITY_STATUS_CREATED_AT_INDEX));
        assertTrue(drafts.containsKey(AgentRedteamTemplateDraftDao.RUN_INDEX));
    }

    @Test
    public void everyIndexNameMatchesTheLiquibaseChangelog() throws Exception {
        String changelog = new String(Files.readAllBytes(Paths.get(CHANGELOG)), StandardCharsets.UTF_8);
        for (String name : Arrays.asList(
                AgentRedteamObservationDao.RUN_IDENTITY_OUTCOME_INDEX, AgentRedteamObservationDao.EXPIRE_AT_TTL_INDEX,
                AgentRedteamProfileDao.IDENTITY_CREATED_AT_INDEX, AgentRedteamProfileDao.RUN_BATCH_INDEX,
                AgentRedteamTemplateDraftDao.IDENTITY_STATUS_CREATED_AT_INDEX, AgentRedteamTemplateDraftDao.RUN_INDEX)) {
            assertTrue(name + " is not in the changelog", changelog.contains("\"name\": \"" + name + "\""));
        }
        for (String collection : Arrays.asList(AgentRedteamObservationDao.COLLECTION_NAME,
                AgentRedteamProfileDao.COLLECTION_NAME, AgentRedteamTemplateDraftDao.COLLECTION_NAME)) {
            assertTrue(changelog.contains("collectionName=\"" + collection + "\""));
        }
    }

    // ── profile reads ────────────────────────────────────────────────────────

    @Test
    public void latestIsTheNewestVersionOfThatAgentOnly() {
        AgentRedteamProfileDao.instance.insertMany(Arrays.asList(
                profile(123, "run-1", 1, 1000), profile(123, "run-2", 1, 3000), profile(456, "run-2", 1, 9000)));

        assertEquals("123:POST:h:run-2:1", AgentRedteamProfileDao.instance.findLatest(123, URL, "POST").getId());
        assertNull("another method is another agent", AgentRedteamProfileDao.instance.findLatest(123, URL, "GET"));
        assertNull("nothing learned yet is null", AgentRedteamProfileDao.instance.findLatest(789, URL, "POST"));
    }

    @Test
    public void versionsAreNewestFirstAndCarryNoCellsOrInsights() {
        AgentRedteamProfileDao.instance.insertMany(Arrays.asList(
                profile(123, "run-1", 1, 1000), profile(123, "run-1", 2, 2000), profile(123, "run-2", 1, 3000)));

        List<AgentRedteamProfile> versions = AgentRedteamProfileDao.instance.findVersions(123, URL, "POST", 2);

        assertEquals(Arrays.asList(3000L, 2000L), versions.stream().map(AgentRedteamRecord::getCreatedAtMs).collect(Collectors.toList()));
        assertTrue(versions.get(0).getCells().isEmpty());
        assertTrue(versions.get(0).getInsights().isEmpty());
    }

    @Test
    public void aRunsVersionsAreThatRunsOnlyLatestBatchFirst() {
        AgentRedteamProfileDao.instance.insertMany(Arrays.asList(
                profile(123, "run-1", 1, 1000), profile(123, "run-1", 2, 2000), profile(456, "run-1", 1, 1500),
                profile(123, "run-2", 1, 3000)));

        List<AgentRedteamProfile> found = AgentRedteamProfileDao.instance.findByRun("run-1", 10);

        assertEquals(3, found.size());
        assertEquals(2, found.get(0).getBatchNo());
        assertTrue(found.stream().allMatch(p -> "run-1".equals(p.getTestingRunResultSummaryId())));
    }

    // ── drafts ───────────────────────────────────────────────────────────────

    @Test
    public void draftsAreFilteredByStatusNewestFirst() {
        AgentRedteamTemplateDraftDao.instance.insertMany(Arrays.asList(
                draft("d1", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000),
                draft("d2", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 2000),
                draft("d3", 123, AgentRedteamTemplateDraft.STATUS_DISMISSED, 3000)));

        List<AgentRedteamTemplateDraft> open = AgentRedteamTemplateDraftDao.instance.findByIdentity(
                123, URL, "POST", AgentRedteamTemplateDraft.STATUS_DRAFT, 10);

        assertEquals(Arrays.asList("d2", "d1"), open.stream().map(AgentRedteamRecord::getId).collect(Collectors.toList()));
        assertEquals(3, AgentRedteamTemplateDraftDao.instance.findByRun("run-1", 10).size());
    }

    @Test
    public void updatingStatusLeavesTheSavedTemplateAloneWhenNoneIsGiven() {
        AgentRedteamTemplateDraft saved = draft("d1", 123, AgentRedteamTemplateDraft.STATUS_SAVED, 1000);
        saved.setSavedTemplateId("CUSTOM_1");
        AgentRedteamTemplateDraftDao.instance.insertOne(saved);

        AgentRedteamTemplateDraft before = AgentRedteamTemplateDraftDao.instance.updateStatus(
                "d1", AgentRedteamTemplateDraft.STATUS_DISMISSED, null, 5000);

        assertEquals(AgentRedteamTemplateDraft.STATUS_SAVED, before.getStatus());
        AgentRedteamTemplateDraft after = AgentRedteamTemplateDraftDao.instance.findOne("_id", "d1");
        assertEquals(AgentRedteamTemplateDraft.STATUS_DISMISSED, after.getStatus());
        assertEquals("CUSTOM_1", after.getSavedTemplateId());
        assertEquals(5000L, after.getUpdatedAtMs());
    }

    @Test
    public void updatingAMissingDraftCreatesNothing() {
        assertNull(AgentRedteamTemplateDraftDao.instance.updateStatus("nope", AgentRedteamTemplateDraft.STATUS_SAVED, "CUSTOM_1", 1));
        assertEquals(0, AgentRedteamTemplateDraftDao.instance.getMCollection().countDocuments());
    }

    // ── RBAC ─────────────────────────────────────────────────────────────────

    @Test
    public void aUserOnlySeesAndChangesLearningsOfCollectionsTheyCanAccess() {
        AgentRedteamProfileDao.instance.insertMany(Arrays.asList(profile(123, "run-1", 1, 1000), profile(456, "run-1", 1, 1000)));
        AgentRedteamTemplateDraftDao.instance.insertOne(draft("d1", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000));
        Context.userId.set(7);

        try (MockedStatic<UsersCollectionsList> rbac = mockStatic(UsersCollectionsList.class)) {
            rbac.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(Collections.singletonList(456));

            assertNull(AgentRedteamProfileDao.instance.findLatest(123, URL, "POST"));
            assertEquals(456, AgentRedteamProfileDao.instance.findByRun("run-1", 10).get(0).getApiCollectionId());
            assertEquals(1, AgentRedteamProfileDao.instance.findByRun("run-1", 10).size());
            assertNull(AgentRedteamTemplateDraftDao.instance.updateStatus("d1", AgentRedteamTemplateDraft.STATUS_DISMISSED, null, 1));
        }

        Context.userId.remove();
        assertEquals(AgentRedteamTemplateDraft.STATUS_DRAFT, AgentRedteamTemplateDraftDao.instance.findOne("_id", "d1").getStatus());
    }
}
