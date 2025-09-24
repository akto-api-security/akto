package com.akto.threat.backend.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.utils.ThreatApiDistributionUtils;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Filters;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiRateLimitBucketStatisticsModel {
    
    public static final String ID = "_id";
    public static final String BUCKETS = "buckets";
    
    private String id; // Format: apiCollectionId_method_url_windowSize
    private List<Bucket> buckets;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        public static final String LABEL = "bucketLabel";
        public static final String USER_COUNTS = "userCounts";
        public static final String STATS = "stats";
        
        // SeeThreatApiDistributionUtils.BUCKET_RANGES
        private String label;  
        private List<UserCountData> userCounts; 
        private Stats stats;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCountData {
        public static final String USERS = "users";
        public static final String WINDOW_START = "windowStart";
        
        private int users;
        private int windowStart;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        public static final String MIN = "min";
        public static final String MAX = "max";
        public static final String P25 = "p25";
        public static final String P50 = "p50";
        public static final String P75 = "p75";
        
        private int min;
        private int max;
        private int p25;
        private int p50;
        private int p75;
    }

    public static String getBucketStatsDocIdForApi(int apiCollectionId, String method, String url, int windowSize){
        return String.valueOf(apiCollectionId) + "_" + method + "_" + url + "_" + windowSize;
    }

    public static void calculateStatistics(
            String accountId,
            MongoClient mongoClient,
            Map<String, List<ApiDistributionDataRequestPayload.DistributionData>> frequencyBuckets) {
        if (frequencyBuckets == null || frequencyBuckets.isEmpty()) return;

        MongoCollection<ApiRateLimitBucketStatisticsModel> coll = mongoClient
            .getDatabase(accountId)
            .getCollection("api_rate_limit_bucket_statistics", ApiRateLimitBucketStatisticsModel.class);

        for (Map.Entry<String, List<ApiDistributionDataRequestPayload.DistributionData>> entry : frequencyBuckets.entrySet()) {
            String docId = entry.getKey();
            List<ApiDistributionDataRequestPayload.DistributionData> updates = entry.getValue();
            if (updates == null || updates.isEmpty()) continue;

            int windowSize = updates.get(0).getWindowSize();
            int capacity = capacityForWindowSize(windowSize);

            ApiRateLimitBucketStatisticsModel doc = Optional.ofNullable(coll.find(Filters.eq(ID, docId)).first())
                .orElseGet(() -> {
                    ApiRateLimitBucketStatisticsModel m = new ApiRateLimitBucketStatisticsModel();
                    m.id = docId;
                    m.buckets = new ArrayList<>();
                    // Initialize all standard buckets
                    for (ThreatApiDistributionUtils.Range range : ThreatApiDistributionUtils.getBucketRanges()) {
                        m.buckets.add(new Bucket(range.label, new ArrayList<>(), new Stats(0,0,0,0,0)));
                    }
                    return m;
                });

            for (ApiDistributionDataRequestPayload.DistributionData u : updates) {
                int windowStart = (int) u.getWindowStartEpochMin();
                Map<String, Integer> dist = u.getDistributionMap();

                for (Bucket bucket : doc.buckets) {
                    int users = dist.getOrDefault(bucket.label, 0);
                    upsertUserCount(bucket.userCounts, windowStart, users);
                    evictToCapacity(bucket.userCounts, capacity);
                }
            }

            for (Bucket b : doc.buckets) {
                List<Integer> values = b.userCounts.stream().map(uc -> uc.users).collect(Collectors.toList());
                if (values.isEmpty()) {
                    b.stats = new Stats(0,0,0,0,0);
                } else {
                    Collections.sort(values);
                    int min = values.get(0);
                    int max = values.get(values.size() - 1);
                    int p25 = ThreatApiDistributionUtils.percentile(values, 25);
                    int p50 = ThreatApiDistributionUtils.percentile(values, 50);
                    int p75 = ThreatApiDistributionUtils.percentile(values, 75);
                    b.stats = new Stats(min, max, p25, p50, p75);
                }
            }

            coll.replaceOne(Filters.eq(ID, docId), doc, new ReplaceOptions().upsert(true));
        }
    }


    private static void upsertUserCount(List<UserCountData> list, int windowStart, int users) {
        if (list == null) return;
        int idx = Collections.binarySearch(list, new UserCountData(0, windowStart), Comparator.comparingInt(a -> a.windowStart));
        if (idx >= 0) {
            list.get(idx).users = users;
        } else {
            int insertAt = -idx - 1;
            list.add(insertAt, new UserCountData(users, windowStart));
        }
    }

    private static void evictToCapacity(List<UserCountData> list, int capacity) {
        if (list == null) return;
        while (list.size() > capacity) {
            list.remove(0);
        }
    }

    private static int capacityForWindowSize(int windowSize) {
        if (windowSize == 5) return 576;
        if (windowSize == 15) return 192;
        if (windowSize == 30) return 96;
        int approx = (2 * 24 * 60) / Math.max(1, windowSize);
        return Math.max(1, approx);
    }

}
