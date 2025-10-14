package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.mongodb.client.MongoCollection;

public class ApiDistributionDataDao extends AccountBasedDao<ApiDistributionDataModel> {

    public static final ApiDistributionDataDao instance = new ApiDistributionDataDao();

    private ApiDistributionDataDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.API_DISTRIBUTION_DATA;
    }

    @Override
    protected Class<ApiDistributionDataModel> getClassType() {
        return ApiDistributionDataModel.class;
    }

    public MongoCollection<ApiDistributionDataModel> getCollection(String accountId) {
        return super.getCollection(accountId);
    }
}


