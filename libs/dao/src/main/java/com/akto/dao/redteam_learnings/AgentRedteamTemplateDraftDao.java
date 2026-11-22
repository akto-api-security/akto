package com.akto.dao.redteam_learnings;

import java.util.List;
import com.akto.dto.redteam_learnings.AgentRedteamTemplateDraft;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
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
