package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.MaliciousEventModel;
import com.mongodb.client.MongoCollection;

public class MaliciousEventDao extends AccountBasedDao<MaliciousEventModel> {

    public static final MaliciousEventDao instance = new MaliciousEventDao();

    private MaliciousEventDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.MALICIOUS_EVENTS;
    }

    @Override
    protected Class<MaliciousEventModel> getClassType() {
        return MaliciousEventModel.class;
    }

    public void insertOne(String accountId, MaliciousEventModel event) {
        getCollection(accountId).insertOne(event);
    }

    public MongoCollection<MaliciousEventModel> getCollection(String accountId) {
        return super.getCollection(accountId);
    }
}
