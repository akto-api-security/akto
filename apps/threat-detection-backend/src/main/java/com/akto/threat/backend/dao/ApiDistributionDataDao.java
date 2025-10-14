package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

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

    public void createIndicesIfAbsent(String accountId) {
        MongoCollection<ApiDistributionDataModel> coll = getCollection(accountId);

        java.util.Set<String> existing = new java.util.HashSet<>();
        try (MongoCursor<Document> it = coll.listIndexes().iterator()) {
            while (it.hasNext()) {
                Document idx = it.next();
                existing.add(idx.getString("name"));
            }
        }

        java.util.Map<String, org.bson.conversions.Bson> required = new java.util.LinkedHashMap<>();
        // Query uses equality on all 5 fields; create compound index in that order
        required.put("apiCollectionId_1_url_1_method_1_windowSize_1_windowStart_1",
            Indexes.compoundIndex(
                Indexes.ascending("apiCollectionId"),
                Indexes.ascending("url"),
                Indexes.ascending("method"),
                Indexes.ascending("windowSize"),
                Indexes.ascending("windowStart")
            )
        );

        for (java.util.Map.Entry<String, org.bson.conversions.Bson> e : required.entrySet()) {
            if (!existing.contains(e.getKey())) {
                coll.createIndex(e.getValue(), new IndexOptions().name(e.getKey()));
            }
        }
    }
}


