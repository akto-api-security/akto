package com.akto.dao;

import com.akto.dto.AgentTrafficLog;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import java.util.concurrent.TimeUnit;

/**
 * DAO for agent_traffic_logs collection.
 * Stores raw agent traffic data for future training and analysis.
 */
public class AgentTrafficLogDao extends AccountsContextDao<AgentTrafficLog> {

    public static final AgentTrafficLogDao instance = new AgentTrafficLogDao();

    @Override
    public String getCollName() {
        return "agent_traffic_logs";
    }

    @Override
    public Class<AgentTrafficLog> getClassT() {
        return AgentTrafficLog.class;
    }

    /**
     * Create indexes for the collection:
     * 1. TTL index on expiresAt - auto-deletes documents after expiry
     * 2. Compound index on apiCollectionId + timestamp for efficient queries
     * 3. Index on accountId for account-level queries
     * 4. Index on isBlocked for filtering blocked/allowed traffic
     */
    public void createIndicesIfAbsent() {
        // TTL index - MongoDB will automatically delete documents after expiresAt
        IndexOptions ttlOptions = new IndexOptions()
                .name("expiresAt_ttl_idx")
                .expireAfter(0L, TimeUnit.SECONDS);  // Delete immediately after expiresAt date
        MCollection.createIndexIfAbsent(
                getDBName(), 
                getCollName(), 
                Indexes.ascending("expiresAt"), 
                ttlOptions
        );
        
        // Compound index for collection-based training queries
        String[] collectionTimestampFields = {"apiCollectionId", "timestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), collectionTimestampFields, false);
        
        // Index on accountId for account isolation
        String[] accountIdField = {"accountId"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), accountIdField, false);
        
        // Compound index for collection + blocked status (useful for training)
        String[] collectionBlockedFields = {"apiCollectionId", "isBlocked", "timestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), collectionBlockedFields, false);
    }
}

