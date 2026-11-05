package com.akto.dao.redteam_learnings;

import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDaoWithRbac;
import com.akto.dao.MCollection;
import com.akto.dto.redteam_learnings.AgentRedteamRecord;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

/**
 * Shared by the red-team learning DAOs. Reads go through the RBAC overrides, scoped by the apiCollectionId every
 * record carries, so a user only sees learnings for collections they can see. The database-abstractor writes these
 * collections with its own DAOs; index names here, there and in Liquibase 034 must stay the same.
 */
public abstract class AgentRedteamDao<T extends AgentRedteamRecord> extends AccountsContextDaoWithRbac<T> {

    @Override
    public String getFilterKeyString() {
        return AgentRedteamRecord.API_COLLECTION_ID;
    }

    public static Bson identityFilter(int apiCollectionId, String url, String method) {
        return Filters.and(
                Filters.eq(AgentRedteamRecord.API_COLLECTION_ID, apiCollectionId),
                Filters.eq(AgentRedteamRecord.URL, url),
                Filters.eq(AgentRedteamRecord.METHOD, method));
    }

    /** First, because listing the indexes of a collection that does not exist yet fails and the index would be skipped. */
    protected void ensureCollection() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
    }

    protected void createIndex(String name, Bson keys) {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), keys, new IndexOptions().name(name));
    }

    /** The stored Date decides when a document expires. */
    protected void createTtlIndex(String name, String dateField) {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(dateField),
                new IndexOptions().name(name).expireAfter(0L, TimeUnit.SECONDS));
    }
}
