package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ActorInfoModel;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;

public class ActorInfoDao extends AccountBasedDao<ActorInfoModel> {

    public static final ActorInfoDao instance = new ActorInfoDao();

    private ActorInfoDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.ACTOR_INFO;
    }

    @Override
    protected Class<ActorInfoModel> getClassType() {
        return ActorInfoModel.class;
    }

    public MongoCollection<ActorInfoModel> getCollection(String accountId) {
        return super.getCollection(accountId);
    }

    /**
     * Creates required indexes for actor_info collection.
     * Simple indexes on discoveredAt and lastAttackTs for sorting and filtering.
     */
    public void createIndicesIfAbsent(String accountId) {
        MongoCollection<Document> collection = getDatabase(accountId)
            .getCollection(getCollectionName(), Document.class);

        // Index for sorting by discoveredAt
        createIndexIfAbsent(collection, "idx_discoveredAt",
            Indexes.descending("discoveredAt"));

        // Index for filtering/sorting by lastAttackTs
        createIndexIfAbsent(collection, "idx_lastAttackTs",
            Indexes.descending("lastAttackTs"));
    }

    /**
     * Helper method to create index if it doesn't already exist.
     */
    private void createIndexIfAbsent(MongoCollection<Document> collection, String indexName, Bson keys) {
        try {
            // Check if index already exists
            boolean indexExists = false;
            for (Document index : collection.listIndexes()) {
                if (indexName.equals(index.getString("name"))) {
                    indexExists = true;
                    break;
                }
            }

            if (!indexExists) {
                IndexOptions options = new IndexOptions().name(indexName);
                collection.createIndex(keys, options);
            }
        } catch (Exception e) {
            // Log but don't fail - indexes are supplementary
            System.err.println("Error creating index " + indexName + ": " + e.getMessage());
        }
    }
}


