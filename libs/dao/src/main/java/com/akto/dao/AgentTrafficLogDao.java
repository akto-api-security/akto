package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgentTrafficLog;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

/**
 * DAO for AgentTrafficLog collection.
 * Stores raw agent traffic data for training purposes.
 */
public class AgentTrafficLogDao extends AccountsContextDao<AgentTrafficLog> {

    public static final String COLLECTION_NAME = "agent_traffic_logs";
    public static final AgentTrafficLogDao instance = new AgentTrafficLogDao();

    private AgentTrafficLogDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<AgentTrafficLog> getClassT() {
        return AgentTrafficLog.class;
    }

    /**
     * Creates indexes for the collection:
     * 1. TTL index on expiresAt (7 days)
     * 2. Compound index on (apiCollectionId, timestamp) for collection-based queries
     * 3. Index on accountId for account isolation
     * 4. Index on isBlocked for filtering blocked/allowed traffic
     * 5. Compound index on (apiCollectionId, isBlocked, timestamp) for training queries
     */
    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get() + "";
        MongoDatabase db = clients[0].getDatabase(dbName);
        
        for (String col : db.listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            db.createCollection(getCollName());
        }

        // TTL Index on expiresAt - auto-deletes documents when expiresAt date is reached
        // Setting expireAfter to 0 meansx MongoDB will delete documents when the expiresAt date field value is reached
        Bson ttlIndex = Indexes.ascending(AgentTrafficLog.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);

        // Compound index on (apiCollectionId, timestamp) for collection-based training queries
        String[] fieldNames = {AgentTrafficLog.API_COLLECTION_ID, AgentTrafficLog.TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index on accountId for account isolation
        fieldNames = new String[]{AgentTrafficLog.ACCOUNT_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index on isBlocked for filtering blocked/allowed traffic
        fieldNames = new String[]{AgentTrafficLog.IS_BLOCKED};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Compound index on (apiCollectionId, isBlocked, timestamp) for training with labels
        fieldNames = new String[]{
                AgentTrafficLog.API_COLLECTION_ID,
                AgentTrafficLog.IS_BLOCKED,
                AgentTrafficLog.TIMESTAMP
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }
}

