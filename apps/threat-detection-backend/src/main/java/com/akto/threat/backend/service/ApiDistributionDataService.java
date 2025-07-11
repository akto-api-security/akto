package com.akto.threat.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataResponsePayload;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BucketStats;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchApiDistributionDataRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchApiDistributionDataResponse;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class ApiDistributionDataService {
    
    private static final LoggerMaker logger = new LoggerMaker(ApiDistributionDataService.class);
    private final MongoClient mongoClient;

    public ApiDistributionDataService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public ApiDistributionDataResponsePayload saveApiDistributionData(String accountId, ApiDistributionDataRequestPayload payload) {
        List<WriteModel<ApiDistributionDataModel>> bulkUpdates = new ArrayList<>();

        for (ApiDistributionDataRequestPayload.DistributionData protoData : payload.getDistributionDataList()) {
            Bson filter = Filters.and(
                Filters.eq("apiCollectionId", protoData.getApiCollectionId()),
                Filters.eq("url", protoData.getUrl()),
                Filters.eq("method", protoData.getMethod()),
                Filters.eq("windowSize", protoData.getWindowSize()),
                Filters.eq("windowStart", protoData.getWindowStartEpochMin())
            );

            Bson update = Updates.combine(
                Updates.set("distribution", protoData.getDistributionMap()),
                Updates.set("apiCollectionId", protoData.getApiCollectionId()),
                Updates.set("url", protoData.getUrl()),
                Updates.set("method", protoData.getMethod()),
                Updates.set("windowSize", protoData.getWindowSize()),
                Updates.set("windowStart", protoData.getWindowStartEpochMin())
            );

            UpdateOptions options = new UpdateOptions().upsert(true);

            bulkUpdates.add(new UpdateOneModel<>(filter, update, options));
        }

        this.mongoClient
            .getDatabase(accountId + "")
            .getCollection("api_distribution_data", ApiDistributionDataModel.class)
            .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));

        return ApiDistributionDataResponsePayload.newBuilder().build();
    }

    public FetchApiDistributionDataResponse getDistributionStats(String accountId, FetchApiDistributionDataRequest fetchApiDistributionDataRequest) {

        MongoCollection<ApiDistributionDataModel> coll =
        this.mongoClient
            .getDatabase(accountId)
            .getCollection("api_distribution_data", ApiDistributionDataModel.class);

        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", fetchApiDistributionDataRequest.getApiCollectionId()),
            Filters.eq("url", fetchApiDistributionDataRequest.getUrl()),
            Filters.eq("method", fetchApiDistributionDataRequest.getMethod()),
            Filters.eq("windowSize", 5),
            Filters.gte("windowStart", fetchApiDistributionDataRequest.getStartWindow()),
            Filters.lte("windowStart", fetchApiDistributionDataRequest.getEndWindow())
        );

        Map<String, List<Integer>> bucketToValues = new HashMap<>();
        try (MongoCursor<ApiDistributionDataModel> cursor = coll.find(filter).iterator()) {
            while (cursor.hasNext()) {
                ApiDistributionDataModel doc = cursor.next();
                if (doc.distribution == null) continue;

                for (Map.Entry<String, Integer> entry : doc.distribution.entrySet()) {
                    bucketToValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                  .add(entry.getValue());
                }
            }
        }
        
        FetchApiDistributionDataResponse.Builder responseBuilder = FetchApiDistributionDataResponse.newBuilder();

        for (Map.Entry<String, List<Integer>> entry : bucketToValues.entrySet()) {
            String bucket = entry.getKey();
            List<Integer> values = entry.getValue();
            if (values.isEmpty()) continue;

            Collections.sort(values);
            int min = values.get(0);
            int max = values.get(values.size() - 1);
            int p25 = percentile(values, 25);
            int p50 = percentile(values, 50); // median
            int p75 = percentile(values, 75);

            BucketStats stats = BucketStats.newBuilder()
                .setBucketLabel(bucket)
                .setMin(min)
                .setMax(max)
                .setP25(p25)
                .setP50(p50)
                .setP75(p75)
                .build();

            responseBuilder.addBucketStats(stats);
        }

        return responseBuilder.build();
    }

    private int percentile(List<Integer> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

}
