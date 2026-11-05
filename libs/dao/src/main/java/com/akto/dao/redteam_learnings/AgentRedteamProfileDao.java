package com.akto.dao.redteam_learnings;

import java.util.Arrays;
import java.util.List;

import org.bson.conversions.Bson;

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

    @Override
    public void createIndicesIfAbsent() {
        ensureCollection();
        createIndex(IDENTITY_CREATED_AT_INDEX, Indexes.compoundIndex(
                Indexes.ascending(AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamRecord.URL, AgentRedteamRecord.METHOD),
                Indexes.descending(AgentRedteamRecord.CREATED_AT_MS)));
        createIndex(RUN_BATCH_INDEX, Indexes.ascending(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID,
                AgentRedteamProfile.BATCH_NO));
    }

    @Override
    protected List<String> indexNames() {
        return Arrays.asList(IDENTITY_CREATED_AT_INDEX, RUN_BATCH_INDEX);
    }

    /** Writes the version whole under its deterministic id, so repeating a consolidation rewrites the same version. */
    public void saveVersion(AgentRedteamProfile profile) {
        replaceOne(Filters.eq("_id", profile.getId()), profile);
    }

    /**
     * The newest version for the agent, without its grid cells (the writers here need its insights and totals; the
     * cells are rebuilt from the observations); null when nothing has been learned about it.
     */
    public AgentRedteamProfile findLatest(int apiCollectionId, String url, String method) {
        return first(findAll(identityFilter(apiCollectionId, url, method), 0, 1,
                Sorts.descending(AgentRedteamRecord.CREATED_AT_MS), WITHOUT_CELLS));
    }

    /** The highest batch this run has consolidated for the agent, without its cells; null when it has consolidated none. */
    public AgentRedteamProfile findLatestInRun(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        return first(findAll(runFilter(testingRunResultSummaryId, apiCollectionId, url, method), 0, 1,
                Sorts.descending(AgentRedteamProfile.BATCH_NO), WITHOUT_CELLS));
    }

    private static final Bson WITHOUT_CELLS = Projections.exclude(AgentRedteamProfile.CELLS);

    private static AgentRedteamProfile first(List<AgentRedteamProfile> found) {
        return found.isEmpty() ? null : found.get(0);
    }
}
