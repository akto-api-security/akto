package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import org.bson.Document;
import com.mongodb.client.MongoCollection;

public class ThreatConfigurationDao extends AccountBasedDao<Document> {

    public static final ThreatConfigurationDao instance = new ThreatConfigurationDao();

    private ThreatConfigurationDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.THREAT_CONFIGURATION;
    }

    @Override
    protected Class<Document> getClassType() {
        return Document.class;
    }

    public MongoCollection<Document> getCollection(String accountId) {
        return super.getCollection(accountId);
    }
}


