package com.akto.dao.redteam_learnings;

import java.util.Arrays;
import java.util.List;

import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;

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

    @Override
    public void createIndicesIfAbsent() {
        ensureCollection();
        createIndex(IDENTITY_STATUS_CREATED_AT_INDEX, Indexes.compoundIndex(
                Indexes.ascending(AgentRedteamRecord.API_COLLECTION_ID, AgentRedteamRecord.URL, AgentRedteamRecord.METHOD,
                        AgentRedteamTemplateDraft.STATUS),
                Indexes.descending(AgentRedteamRecord.CREATED_AT_MS)));
        createIndex(RUN_INDEX, Indexes.ascending(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID));
    }

    @Override
    protected List<String> indexNames() {
        return Arrays.asList(IDENTITY_STATUS_CREATED_AT_INDEX, RUN_INDEX);
    }

    /**
     * Inserts the drafts that do not exist yet. A draft with the same _id (the same suggestion from an earlier run) is
     * left exactly as it is, so a dismissed or saved draft keeps its status. Returns how many were inserted.
     */
    public int insertNew(List<AgentRedteamTemplateDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) return 0;
        try {
            getMCollection().insertMany(drafts, new InsertManyOptions().ordered(false));
            return drafts.size();
        } catch (MongoBulkWriteException e) {
            for (BulkWriteError error : e.getWriteErrors()) {
                if (ErrorCategory.fromErrorCode(error.getCode()) != ErrorCategory.DUPLICATE_KEY) throw e;
            }
            return drafts.size() - e.getWriteErrors().size();
        }
    }
}
