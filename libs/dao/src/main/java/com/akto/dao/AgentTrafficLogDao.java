package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.AgentTrafficLog;
import com.akto.util.DbMode;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
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

    public static final int maxDocuments = 100_000;
    public static final int sizeInBytes = 100_000_000; // 100MB - standard size for log collections

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
     * 2. Compound index on (apiCollectionId, timestamp, isBlocked) for all query patterns
     * 
     * Index order optimized for cardinality: high (apiCollectionId) -> high (timestamp) -> low (isBlocked)
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
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(), new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }

        // TTL Index on expiresAt - auto-deletes documents when expiresAt date is reached
        // Setting expireAfter to 0 means MongoDB will delete documents when the expiresAt date field value is reached
        Bson ttlIndex = Indexes.ascending(AgentTrafficLog.EXPIRES_AT);
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl")
                .expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);

        // Compound index optimized for cardinality: high -> high -> low
        // Order: apiCollectionId (high cardinality) -> timestamp (high cardinality) -> isBlocked (low cardinality)
        String[] fieldNames = new String[]{
                AgentTrafficLog.API_COLLECTION_ID,
                AgentTrafficLog.TIMESTAMP,
                AgentTrafficLog.IS_BLOCKED
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }
}

