package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ApiRateLimitBucketStatisticsModel;
import com.mongodb.client.MongoCollection;

public class ApiRateLimitBucketStatisticsDao extends AccountBasedDao<ApiRateLimitBucketStatisticsModel> {

    public static final ApiRateLimitBucketStatisticsDao instance = new ApiRateLimitBucketStatisticsDao();

    private ApiRateLimitBucketStatisticsDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.API_RATE_LIMIT_BUCKET_STATISTICS;
    }

    @Override
    protected Class<ApiRateLimitBucketStatisticsModel> getClassType() {
        return ApiRateLimitBucketStatisticsModel.class;
    }

    public MongoCollection<ApiRateLimitBucketStatisticsModel> getCollection(String accountId) {
        return super.getCollection(accountId);
    }
}
