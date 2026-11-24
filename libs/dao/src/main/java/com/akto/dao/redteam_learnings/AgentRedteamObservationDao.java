package com.akto.dao.redteam_learnings;

import com.akto.dto.redteam_learnings.AgentRedteamObservation;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.mongodb.client.model.Indexes;

/** Written and read by the database-abstractor during consolidation; the dashboard only creates its indexes. */
public class AgentRedteamObservationDao extends AgentRedteamDao<AgentRedteamObservation> {

    public static final String COLLECTION_NAME = "agent_redteam_observations";
    /** Serves counting a run's judged conversations per agent, and the per-cell aggregation. */
    public static final String RUN_IDENTITY_OUTCOME_INDEX = "testingRunResultSummaryId_apiCollectionId_url_method_outcome";
    public static final String EXPIRE_AT_TTL_INDEX = "expireAt_ttl";

    public static final AgentRedteamObservationDao instance = new AgentRedteamObservationDao();

    private AgentRedteamObservationDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentRedteamObservation> getClassT() {
        return AgentRedteamObservation.class;
    }

    public void createIndicesIfAbsent() {
        ensureCollection();
        createIndex(RUN_IDENTITY_OUTCOME_INDEX, Indexes.ascending(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID,
                AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamRecord.URL, AgentRedteamRecord.METHOD,
                AgentRedteamObservation.OUTCOME));
        createTtlIndex(EXPIRE_AT_TTL_INDEX, AgentRedteamObservation.EXPIRE_AT);
    }
}
