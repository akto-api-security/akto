package com.akto.threat.backend.dao;

import com.akto.threat.backend.db.AggregateSampleMaliciousEventModel;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.WriteModel;

import java.util.List;

/**
 * DAO for aggregate malicious event collections.
 * Unlike MaliciousEventDao, this handles DYNAMIC collection names (passed as parameter).
 * Extends AccountBasedDao to reuse getDatabase() but doesn't use getCollection() since
 * collection name varies per call.
 */
public class AggregateSampleMaliciousEventDao extends AccountBasedDao<AggregateSampleMaliciousEventModel> {

    public static final AggregateSampleMaliciousEventDao instance = new AggregateSampleMaliciousEventDao();

    private AggregateSampleMaliciousEventDao() {}

    @Override
    protected String getCollectionName() {
        return null; // Dynamic collection names - not used
    }

    @Override
    protected Class<AggregateSampleMaliciousEventModel> getClassType() {
        return AggregateSampleMaliciousEventModel.class;
    }

    public void bulkWrite(String accountId, String collectionName, List<WriteModel<AggregateSampleMaliciousEventModel>> bulkUpdates) {
        if (bulkUpdates == null || bulkUpdates.isEmpty()) {
            return;
        }

        MongoCollection<AggregateSampleMaliciousEventModel> collection = getDatabase(accountId)
            .getCollection(collectionName, getClassType());

        collection.bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
    }
}
