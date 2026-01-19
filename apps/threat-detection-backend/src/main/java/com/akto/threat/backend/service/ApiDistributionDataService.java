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
import com.akto.threat.backend.db.ApiRateLimitBucketStatisticsModel;
import com.akto.utils.ThreatApiDistributionUtils;
import com.akto.threat.backend.dao.ApiDistributionDataDao;
import com.akto.threat.backend.dao.ApiRateLimitBucketStatisticsDao;
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
    private final ApiDistributionDataDao apiDistributionDataDao;

    public ApiDistributionDataService(ApiDistributionDataDao apiDistributionDataDao) {
        this.apiDistributionDataDao = apiDistributionDataDao;
    }

    public ApiDistributionDataResponsePayload saveApiDistributionData(String accountId, ApiDistributionDataRequestPayload payload) {
        List<WriteModel<ApiDistributionDataModel>> bulkUpdates = new ArrayList<>();
        
        
        Map<String, List<ApiDistributionDataRequestPayload.DistributionData>> frequencyBuckets = new HashMap<>();

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

            
            frequencyBuckets.computeIfAbsent(
                    ApiRateLimitBucketStatisticsModel.getBucketStatsDocIdForApi(protoData.getApiCollectionId(),
                            protoData.getMethod(), protoData.getUrl(), protoData.getWindowSize()),
                    k -> new ArrayList<>()).add(protoData);

            UpdateOptions options = new UpdateOptions().upsert(true);

            bulkUpdates.add(new UpdateOneModel<>(filter, update, options));
        }

        apiDistributionDataDao.getCollection(accountId)
            .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));

        ApiRateLimitBucketStatisticsModel.calculateStatistics(accountId, ApiRateLimitBucketStatisticsDao.instance, frequencyBuckets);
        return ApiDistributionDataResponsePayload.newBuilder().build();
    }

    public static List<BucketStats> fetchBucketStats(String accountId, Bson filters, ApiDistributionDataDao dao) {
        MongoCollection<ApiDistributionDataModel> coll = dao.getCollection(accountId);

        Map<String, List<Integer>> bucketToValues = new HashMap<>();
        try (MongoCursor<ApiDistributionDataModel> cursor = coll.find(filters).iterator()) {
            while (cursor.hasNext()) {
                ApiDistributionDataModel doc = cursor.next();
                if (doc.distribution == null)
                    continue;

                for (Map.Entry<String, Integer> entry : doc.distribution.entrySet()) {
                    bucketToValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                            .add(entry.getValue());
                }
            }
        }
        
        List<BucketStats> bucketStats = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : bucketToValues.entrySet()) {
            String bucket = entry.getKey();
            List<Integer> values = entry.getValue();
            if (values.isEmpty()) continue;
    
            Collections.sort(values);
            int min = values.get(0);
            int max = values.get(values.size() - 1);
            int p25 = ThreatApiDistributionUtils.percentile(values, 25);
            int p50 = ThreatApiDistributionUtils.percentile(values, 50); // median
            int p75 = ThreatApiDistributionUtils.percentile(values, 75);
    
            BucketStats stats = BucketStats.newBuilder()
                .setBucketLabel(bucket)
                .setMin(min)
                .setMax(max)
                .setP25(p25)
                .setP50(p50)
                .setP75(p75)
                .build();
    
            bucketStats.add(stats);
        }
    
        return bucketStats;

    }

    public FetchApiDistributionDataResponse getDistributionStats(String accountId, FetchApiDistributionDataRequest fetchApiDistributionDataRequest) {
        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", fetchApiDistributionDataRequest.getApiCollectionId()),
            Filters.eq("url", fetchApiDistributionDataRequest.getUrl()),
            Filters.eq("method", fetchApiDistributionDataRequest.getMethod()),
            Filters.eq("windowSize", 5),
            Filters.gte("windowStart", 1726461999 / 60),
            Filters.lte("windowStart", 1757997999 / 60)
        );
        
        FetchApiDistributionDataResponse.Builder responseBuilder = FetchApiDistributionDataResponse.newBuilder();
        responseBuilder.addAllBucketStats(fetchBucketStats(accountId, filter, apiDistributionDataDao));

        return responseBuilder.build();
    }

}
