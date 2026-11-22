package com.akto.dao.redteam_learnings;
import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.mongodb.client.model.Filters;
public abstract class AgentRedteamDao<T extends AgentRedteamRecord> extends AccountsContextDao<T> {

    public static Bson identityFilter(int apiCollectionId, String url, String method) {
        return Filters.and(
                Filters.eq(AgentRedteamRecord.API_COLLECTION_ID, apiCollectionId),
                Filters.eq(AgentRedteamRecord.URL, url),
                Filters.eq(AgentRedteamRecord.METHOD, method));
    }

    public static Bson runFilter(String testingRunResultSummaryId, int apiCollectionId, String url, String method) {
        return Filters.and(Filters.eq(AgentRedteamRecord.TESTING_RUN_RESULT_SUMMARY_ID, testingRunResultSummaryId),
                identityFilter(apiCollectionId, url, method));
    }
}
