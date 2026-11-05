package com.akto.dao.redteam_learnings;

import java.util.List;

import com.akto.dto.redteam_learnings.AgentRedteamProfile;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

public class AgentRedteamProfileDao extends AgentRedteamDao<AgentRedteamProfile> {

    public static final String COLLECTION_NAME = "agent_redteam_profiles";
    /** Serves the latest version and the version history of one agent. */
    public static final String IDENTITY_CREATED_AT_INDEX = "apiCollectionId_url_method_createdAtMs";
    /** Serves the versions written during one run. */
    public static final String RUN_BATCH_INDEX = "testingRunResultSummaryId_batchNo";

    public static final AgentRedteamProfileDao instance = new AgentRedteamProfileDao();

    private AgentRedteamProfileDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentRedteamProfile> getClassT() {
        return AgentRedteamProfile.class;
    }

    public void createIndicesIfAbsent() {
        ensureCollection();
        createIndex(IDENTITY_CREATED_AT_INDEX, Indexes.compoundIndex(
                Indexes.ascending(AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamRecord.URL, AgentRedteamRecord.METHOD),
                Indexes.descending(AgentRedteamRecord.CREATED_AT_MS)));
        createIndex(RUN_BATCH_INDEX, Indexes.ascending(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID,
                AgentRedteamProfile.BATCH_NO));
    }

    /** The newest version for the agent; null when nothing has been learned about it or the caller cannot see its collection. */
    public AgentRedteamProfile findLatest(int apiCollectionId, String url, String method) {
        List<AgentRedteamProfile> found = findAll(identityFilter(apiCollectionId, url, method), 0, 1,
                Sorts.descending(AgentRedteamRecord.CREATED_AT_MS), null);
        return found.isEmpty() ? null : found.get(0);
    }

    /** The agent's versions, newest first, without their cells and insights. */
    public List<AgentRedteamProfile> findVersions(int apiCollectionId, String url, String method, int limit) {
        return findAll(identityFilter(apiCollectionId, url, method), 0, limit,
                Sorts.descending(AgentRedteamRecord.CREATED_AT_MS),
                Projections.exclude(AgentRedteamProfile.CELLS, AgentRedteamProfile.INSIGHTS));
    }

    /** Every version written during one run, across the agents it tested, latest batch first. */
    public List<AgentRedteamProfile> findByRun(String testingRunResultSummaryId, int limit) {
        return findAll(Filters.eq(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId), 0, limit,
                Sorts.descending(AgentRedteamProfile.BATCH_NO), null);
    }
}
