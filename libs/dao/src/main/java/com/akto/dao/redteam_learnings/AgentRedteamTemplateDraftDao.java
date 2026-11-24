package com.akto.dao.redteam_learnings;

import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.mongodb.client.model.Indexes;

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
}
