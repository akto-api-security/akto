package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.context.Context;
import com.akto.dao.redteam_learnings.AgentRedteamProfileDao;
import com.akto.dao.redteam_learnings.AgentRedteamTemplateDraftDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamProfile.Insight;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.service.redteam_learnings.RedteamLearningsService;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

public class TestRedteamLearningsAction extends MongoBasedTest {

    private static final String URL = "https://agent.example.com/chat";
    private static final String RUN = "665f1c2ab1e2c3d4e5f60718";

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(7);
        AgentRedteamProfileDao.instance.getMCollection().deleteMany(new BasicDBObject());
        AgentRedteamTemplateDraftDao.instance.getMCollection().deleteMany(new BasicDBObject());
        YamlTemplateDao.instance.getMCollection().deleteMany(new BasicDBObject());
        AccountSettingsDao.instance.getMCollection().deleteMany(AccountSettingsDao.generateFilter());
    }

    @After
    public void clearUser() {
        Context.userId.remove();
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
        return p;
    }

    private static AgentRedteamTemplateDraft draft(String id, int apiCollectionId, String status, long createdAtMs) {
        AgentRedteamTemplateDraft d = new AgentRedteamTemplateDraft();
        d.setId(id);
        d.setApiCollectionId(apiCollectionId);
        d.setUrl(URL);
        d.setMethod("POST");
        d.setTestingRunResultSummaryId(RUN);
        d.setStatus(status);
        d.setCreatedAtMs(createdAtMs);
        return d;
    }

    private static RedteamLearningsAction agentRead(int apiCollectionId, String method) {
        RedteamLearningsAction action = new RedteamLearningsAction();
        action.setApiCollectionId(apiCollectionId);
        action.setUrl(URL);
        action.setMethod(method);
        return action;
    }

    private static RedteamLearningsAction runRead(String summaryId) {
        RedteamLearningsAction action = new RedteamLearningsAction();
        action.setTestingRunResultSummaryId(summaryId);
        return action;
    }

    private static RedteamLearningsAction draftUpdate(String draftId, String status, String templateId) {
        RedteamLearningsAction action = new RedteamLearningsAction();
        action.setDraftId(draftId);
        action.setStatus(status);
        action.setSavedTemplateId(templateId);
        return action;
    }

    private static MockedStatic<UsersCollectionsList> userSees(List<Integer> collectionIds) {
        MockedStatic<UsersCollectionsList> rbac = mockStatic(UsersCollectionsList.class);
        rbac.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(collectionIds);
        return rbac;
    }

    // ── agent ────────────────────────────────────────────────────────────────

    @Test
    public void anAgentWithNothingLearnedIsNoneAndLearningsCountAsOnUntilTurnedOff() {
        try (MockedStatic<UsersCollectionsList> rbac = userSees(null)) {
            RedteamLearningsAction action = agentRead(123, "post");
            assertEquals("SUCCESS", action.fetchAgentRedteamLearnings());
            assertEquals(RedteamLearningsService.STATE_NONE, action.getAgentLearnings().getState());
            assertNull(action.getAgentLearnings().getLatestProfile());
            assertEquals(Boolean.TRUE, action.getLearningsEnabled());

            AccountSettingsDao.instance.getMCollection().updateOne(AccountSettingsDao.generateFilter(),
                    Updates.set(AccountSettings.REDTEAM_LEARNINGS_ENABLED, false), new UpdateOptions().upsert(true));
            RedteamLearningsAction off = agentRead(123, "POST");
            assertEquals("SUCCESS", off.fetchAgentRedteamLearnings());
            assertEquals(Boolean.FALSE, off.getLearningsEnabled());
        }
    }

    @Test
    public void anAgentShowsItsLatestProfileHistoryAndOnlyOpenDrafts() {
        AgentRedteamProfileDao.instance.insertMany(Arrays.asList(
                profile(123, "run-1", 1, 1000), profile(123, "run-2", 1, 3000), profile(456, "run-2", 1, 9000)));
        AgentRedteamTemplateDraftDao.instance.insertMany(Arrays.asList(
                draft("d1", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000),
                draft("d2", 123, AgentRedteamTemplateDraft.STATUS_DISMISSED, 2000)));

        try (MockedStatic<UsersCollectionsList> rbac = userSees(null)) {
            RedteamLearningsAction action = agentRead(123, "POST");
            assertEquals("SUCCESS", action.fetchAgentRedteamLearnings());

            assertEquals(RedteamLearningsService.STATE_READY, action.getAgentLearnings().getState());
            assertEquals("123:POST:h:run-2:1", action.getAgentLearnings().getLatestProfile().getId());
            assertEquals(1, action.getAgentLearnings().getLatestProfile().getInsights().size());
            assertEquals(Arrays.asList(3000L, 1000L), action.getAgentLearnings().getVersions().stream()
                    .map(AgentRedteamRecord::getCreatedAtMs).collect(Collectors.toList()));
            assertEquals(Collections.singletonList("d1"), action.getAgentLearnings().getOpenDrafts().stream()
                    .map(AgentRedteamRecord::getId).collect(Collectors.toList()));
        }
    }

    @Test
    public void anAgentInACollectionTheUserCannotSeeIsForbiddenNotEmpty() {
        AgentRedteamProfileDao.instance.insertOne(profile(123, "run-1", 1, 1000));
        try (MockedStatic<UsersCollectionsList> rbac = userSees(Collections.singletonList(456))) {
            RedteamLearningsAction action = agentRead(123, "POST");
            assertEquals("FORBIDDEN", action.fetchAgentRedteamLearnings());
            assertNull(action.getAgentLearnings());
        }
    }

    @Test
    public void whenTheUsersCollectionsCannotBeReadTheRequestFailsInsteadOfShowingEverything() {
        AgentRedteamProfileDao.instance.insertOne(profile(123, "run-1", 1, 1000));
        try (MockedStatic<UsersCollectionsList> rbac = mockStatic(UsersCollectionsList.class)) {
            rbac.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenThrow(new RuntimeException("rbac down"));
            RedteamLearningsAction agent = agentRead(123, "POST");
            assertEquals("ERROR", agent.fetchAgentRedteamLearnings());
            assertNull(agent.getAgentLearnings());
            RedteamLearningsAction run = runRead("run-1");
            assertEquals("ERROR", run.fetchRunRedteamLearnings());
            assertNull(run.getRunLearnings());
        }
    }

    @Test
    public void anAgentRequestMustNameAFullIdentity() {
        assertEquals("ERROR", agentRead(123, "FETCH").fetchAgentRedteamLearnings());
        RedteamLearningsAction noUrl = agentRead(123, "POST");
        noUrl.setUrl("   ");
        assertEquals("ERROR", noUrl.fetchAgentRedteamLearnings());
        RedteamLearningsAction noCollection = new RedteamLearningsAction();
        noCollection.setUrl(URL);
        noCollection.setMethod("POST");
        assertEquals("ERROR", noCollection.fetchAgentRedteamLearnings());
    }

    // ── run ──────────────────────────────────────────────────────────────────

    @Test
    public void aRunShowsEachAgentsNewestVersionInFullAndOnlyCollectionsTheUserCanSee() {
        AgentRedteamProfileDao.instance.insertMany(Arrays.asList(
                profile(123, RUN, 1, 1000), profile(123, RUN, 2, 2000), profile(456, RUN, 1, 1500),
                profile(123, "other-run", 1, 9000)));
        AgentRedteamTemplateDraftDao.instance.insertMany(Arrays.asList(
                draft("d1", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000),
                draft("d2", 456, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000)));

        try (MockedStatic<UsersCollectionsList> rbac = userSees(null)) {
            RedteamLearningsAction action = runRead(RUN);
            assertEquals("SUCCESS", action.fetchRunRedteamLearnings());
            assertEquals(RedteamLearningsService.STATE_READY, action.getRunLearnings().getState());
            assertEquals(Arrays.asList("123:POST:h:" + RUN + ":2", "456:POST:h:" + RUN + ":1"), action.getRunLearnings()
                    .getAgentProfiles().stream().map(AgentRedteamRecord::getId).collect(Collectors.toList()));
            assertEquals(1, action.getRunLearnings().getAgentProfiles().get(0).getInsights().size());
            assertEquals(3, action.getRunLearnings().getVersions().size());
            assertTrue(action.getRunLearnings().getVersions().get(0).getInsights().isEmpty());
            assertEquals(2, action.getRunLearnings().getDrafts().size());
            assertFalse(action.getRunLearnings().isVersionsTruncated());
        }

        try (MockedStatic<UsersCollectionsList> rbac = userSees(Collections.singletonList(456))) {
            RedteamLearningsAction restricted = runRead(RUN);
            assertEquals("SUCCESS", restricted.fetchRunRedteamLearnings());
            assertTrue(restricted.getRunLearnings().getVersions().stream().allMatch(p -> p.getApiCollectionId() == 456));
            assertEquals(1, restricted.getRunLearnings().getAgentProfiles().size());
            assertEquals(Collections.singletonList("d2"), restricted.getRunLearnings().getDrafts().stream()
                    .map(AgentRedteamRecord::getId).collect(Collectors.toList()));
        }
    }

    @Test
    public void aRunThatLearnedNothingIsNoneAndABadRunIdIsRefused() {
        try (MockedStatic<UsersCollectionsList> rbac = userSees(null)) {
            RedteamLearningsAction empty = runRead(RUN);
            assertEquals("SUCCESS", empty.fetchRunRedteamLearnings());
            assertEquals(RedteamLearningsService.STATE_NONE, empty.getRunLearnings().getState());
            assertEquals("ERROR", runRead("../etc").fetchRunRedteamLearnings());
            assertEquals("ERROR", runRead(null).fetchRunRedteamLearnings());
        }
    }

    // ── draft status ─────────────────────────────────────────────────────────

    @Test
    public void aDraftIsMarkedSavedOnlyWithATemplateThatExists() {
        AgentRedteamTemplateDraftDao.instance.insertOne(draft("d1", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000));
        try (MockedStatic<UsersCollectionsList> rbac = userSees(null)) {
            assertEquals("ERROR", draftUpdate("d1", "saved", null).updateRedteamDraftStatus());
            assertEquals("ERROR", draftUpdate("d1", "SAVED", "CUSTOM_MISSING").updateRedteamDraftStatus());
            assertEquals(AgentRedteamTemplateDraft.STATUS_DRAFT, AgentRedteamTemplateDraftDao.instance.findOne("_id", "d1").getStatus());

            YamlTemplate template = new YamlTemplate();
            template.setId("AKTO_LEARNED_X_CUSTOM_1");
            YamlTemplateDao.instance.insertOne(template);
            assertEquals("SUCCESS", draftUpdate("d1", "saved", "AKTO_LEARNED_X_CUSTOM_1").updateRedteamDraftStatus());
            AgentRedteamTemplateDraft saved = AgentRedteamTemplateDraftDao.instance.findOne("_id", "d1");
            assertEquals(AgentRedteamTemplateDraft.STATUS_SAVED, saved.getStatus());
            assertEquals("AKTO_LEARNED_X_CUSTOM_1", saved.getSavedTemplateId());

            assertEquals("SUCCESS", draftUpdate("d1", "DISMISSED", "IGNORED").updateRedteamDraftStatus());
            AgentRedteamTemplateDraft dismissed = AgentRedteamTemplateDraftDao.instance.findOne("_id", "d1");
            assertEquals(AgentRedteamTemplateDraft.STATUS_DISMISSED, dismissed.getStatus());
            assertEquals("a dismissal keeps the saved template's id", "AKTO_LEARNED_X_CUSTOM_1", dismissed.getSavedTemplateId());
        }
    }

    @Test
    public void aDraftUpdateRefusesUnknownStatusesMissingDraftsAndOtherCollections() {
        AgentRedteamTemplateDraftDao.instance.insertOne(draft("d1", 123, AgentRedteamTemplateDraft.STATUS_DRAFT, 1000));
        try (MockedStatic<UsersCollectionsList> rbac = userSees(Collections.singletonList(456))) {
            assertEquals("ERROR", draftUpdate("d1", "DELETED", null).updateRedteamDraftStatus());
            assertEquals("ERROR", draftUpdate("nope", "DISMISSED", null).updateRedteamDraftStatus());
            // The RBAC filter hides the draft, so the answer does not even confirm it exists.
            RedteamLearningsAction otherCollection = draftUpdate("d1", "DISMISSED", null);
            assertEquals("ERROR", otherCollection.updateRedteamDraftStatus());
            assertTrue(otherCollection.getActionErrors().contains("No such draft"));
        }
        assertEquals(AgentRedteamTemplateDraft.STATUS_DRAFT,
                AgentRedteamTemplateDraftDao.instance.findOneNoRbacFilter(Filters.eq("_id", "d1"), null).getStatus());
        assertEquals("no draft is ever created", 1, AgentRedteamTemplateDraftDao.instance.getMCollection().countDocuments());
    }

    // ── account toggle ───────────────────────────────────────────────────────

    @Test
    public void theAccountToggleNeedsAValueAndStoresIt() {
        AccountSettingsDao.instance.getMCollection().updateOne(AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.GLOBAL_RATE_LIMIT, 0), new UpdateOptions().upsert(true));

        AdminSettingsAction absent = new AdminSettingsAction();
        assertEquals("ERROR", absent.updateRedteamLearningsEnabled());
        assertNull(AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter()).getRedteamLearningsEnabled());

        AdminSettingsAction off = new AdminSettingsAction();
        off.setRedteamLearningsEnabled(false);
        assertEquals("SUCCESS", off.updateRedteamLearningsEnabled());
        assertEquals(Boolean.FALSE, AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter()).getRedteamLearningsEnabled());
    }
}
