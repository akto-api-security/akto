package com.akto.dao.redteam_learnings;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

public class AgentRedteamTemplateDraftDao extends AgentRedteamDao<AgentRedteamTemplateDraft> {

    public static final String COLLECTION_NAME = "agent_redteam_template_drafts";
    /** Serves an agent's drafts in a given status, newest first. */
    public static final String IDENTITY_STATUS_CREATED_AT_INDEX = "apiCollectionId_url_method_status_createdAtMs";
    /** Serves the drafts suggested by one run. */
    public static final String RUN_INDEX = "testingRunResultSummaryId";

    public static final AgentRedteamTemplateDraftDao instance = new AgentRedteamTemplateDraftDao();

    private AgentRedteamTemplateDraftDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentRedteamTemplateDraft> getClassT() {
        return AgentRedteamTemplateDraft.class;
    }

    public void createIndicesIfAbsent() {
        ensureCollection();
        createIndex(IDENTITY_STATUS_CREATED_AT_INDEX, Indexes.compoundIndex(
                Indexes.ascending(AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamRecord.URL, AgentRedteamRecord.METHOD,
                        AgentRedteamTemplateDraft.STATUS),
                Indexes.descending(AgentRedteamRecord.CREATED_AT_MS)));
        createIndex(RUN_INDEX, Indexes.ascending(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID));
    }

    /** The agent's drafts in one status, newest first. */
    public List<AgentRedteamTemplateDraft> findByIdentity(int apiCollectionId, String url, String method, String status, int limit) {
        return findAll(Filters.and(identityFilter(apiCollectionId, url, method), Filters.eq(AgentRedteamTemplateDraft.STATUS, status)),
                0, limit, Sorts.descending(AgentRedteamRecord.CREATED_AT_MS), null);
    }

    /** The drafts one run suggested, newest first. */
    public List<AgentRedteamTemplateDraft> findByRun(String testingRunResultSummaryId, int limit) {
        return findAll(Filters.eq(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId), 0, limit,
                Sorts.descending(AgentRedteamRecord.CREATED_AT_MS), null);
    }

    /**
     * Sets the status, and the saved template's id when one is given: a null savedTemplateId leaves the stored one
     * as it is. Never creates a draft. Returns the draft as it was before the update, or null when there is no such
     * draft or the caller cannot see its collection.
     */
    public AgentRedteamTemplateDraft updateStatus(String draftId, String status, String savedTemplateId, long nowMs) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(AgentRedteamTemplateDraft.STATUS, status));
        updates.add(Updates.set(AgentRedteamTemplateDraft.UPDATED_AT_MS, nowMs));
        if (savedTemplateId != null) {
            updates.add(Updates.set(AgentRedteamTemplateDraft.SAVED_TEMPLATE_ID, savedTemplateId));
        }
        return updateOneNoUpsert(Filters.eq("_id", draftId), Updates.combine(updates));
    }
}
