package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.SplunkIntegrationModel;
import com.mongodb.client.MongoCollection;

public class SplunkIntegrationDao extends AccountBasedDao<SplunkIntegrationModel> {

    public static final SplunkIntegrationDao instance = new SplunkIntegrationDao();

    private SplunkIntegrationDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.SPLUNK_INTEGRATION_CONFIG;
    }

    @Override
    protected Class<SplunkIntegrationModel> getClassType() {
        return SplunkIntegrationModel.class;
    }

    public MongoCollection<SplunkIntegrationModel> getCollection(String accountId) {
        return super.getCollection(accountId);
    }
}


