package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.dao.ApiInfoDao;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.UpdateOptions;

public class PercentilesCron {

    private static final LoggerMaker logger = new LoggerMaker(PercentilesCron.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final MongoClient mongoClient;

    public PercentilesCron(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public void cron(String accountId) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    runOnce(accountId);
                } catch (Exception e) {
                    logger.errorAndAddToDb("error in PercentilesCron: accountId " + accountId + " " + e.getMessage(), LoggerMaker.LogDb.RUNTIME);
                }
            }
        }, 0, 10, TimeUnit.MINUTES);
    }

    public void runOnce(String accountId) {
        MongoCollection<ApiDistributionDataModel> coll = this.mongoClient
                .getDatabase(accountId)
                .getCollection("api_distribution_data", ApiDistributionDataModel.class);

        Set<String> keys = new HashSet<>();
        try (MongoCursor<ApiDistributionDataModel> cursor = coll.find().iterator()) {
            while (cursor.hasNext()) {
                ApiDistributionDataModel doc = cursor.next();
                String key = doc.apiCollectionId + "|" + doc.url + "|" + doc.method;
                keys.add(key);
            }
        }

        for (String key : keys) {
            String[] parts = key.split("\\|", -1);
            int apiCollectionId = Integer.parseInt(parts[0]);
            String url = parts[1];
            String method = parts[2];
            PercentilesResult r = calculatePercentiles(accountId, apiCollectionId, url, method);
            try {
                ApiInfoDao.instance.getMCollection().updateOne(
                        ApiInfoDao.getFilter(url, method, apiCollectionId),
                        Updates.combine(
                                Updates.set("rateLimits.p50", r.p50),
                                Updates.set("rateLimits.p75", r.p75),
                                Updates.set("rateLimits.p90", r.p90)
                        ),
                        new UpdateOptions().upsert(false)
                );
                logger.infoAndAddToDb("Updated rateLimits for apiCollectionId " + apiCollectionId + " url " + url + " method " + method,
                        LoggerMaker.LogDb.RUNTIME);
            } catch (Exception e) {
                logger.errorAndAddToDb("Failed updating rateLimits for apiCollectionId " + apiCollectionId + " url " + url + " method " + method + ": " + e.getMessage(),
                        LoggerMaker.LogDb.RUNTIME);
            }
        }
    }

    private PercentilesResult calculatePercentiles(String accountId, int apiCollectionId, String url, String method) {
        MongoCollection<ApiDistributionDataModel> coll = this.mongoClient
                .getDatabase(accountId)
                .getCollection("api_distribution_data", ApiDistributionDataModel.class);

        Map<String, Long> bucketToCount = new HashMap<>();

        Bson filter = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("url", url),
                Filters.eq("method", method)
        );

        try (MongoCursor<ApiDistributionDataModel> cursor = coll.find(filter).iterator()) {
            while (cursor.hasNext()) {
                ApiDistributionDataModel doc = cursor.next();
                if (doc.distribution == null) continue;
                for (Map.Entry<String, Integer> e : doc.distribution.entrySet()) {
                    bucketToCount.merge(e.getKey(), e.getValue() == null ? 0L : e.getValue().longValue(), Long::sum);
                }
            }
        }

        List<String> bucketOrder = Arrays.asList("b1","b2","b3","b4","b5","b6","b7","b8","b9","b10","b11","b12","b13","b14");
        List<Integer> bucketUpperBounds = Arrays.asList(10, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 20000, 35000, 50000, 100000, Integer.MAX_VALUE);

        long totalUsers = 0;
        for (String b : bucketOrder) totalUsers += bucketToCount.getOrDefault(b, 0L);
        if (totalUsers <= 0) return new PercentilesResult(0, 0, 0);

        double p50Target = totalUsers * 0.5d;
        double p75Target = totalUsers * 0.75d;
        double p90Target = totalUsers * 0.9d;

        long cumulative = 0;
        Integer p50Val = null, p75Val = null, p90Val = null;
        for (int i = 0; i < bucketOrder.size(); i++) {
            String b = bucketOrder.get(i);
            long countInBucket = bucketToCount.getOrDefault(b, 0L);
            cumulative += countInBucket;
            int upper = bucketUpperBounds.get(i);
            if (p50Val == null && cumulative >= p50Target) p50Val = upper;
            if (p75Val == null && cumulative >= p75Target) p75Val = upper;
            if (p90Val == null && cumulative >= p90Target) { p90Val = upper; break; }
        }

        if (p50Val == null) p50Val = bucketUpperBounds.get(bucketUpperBounds.size() - 1);
        if (p75Val == null) p75Val = bucketUpperBounds.get(bucketUpperBounds.size() - 1);
        if (p90Val == null) p90Val = bucketUpperBounds.get(bucketUpperBounds.size() - 1);
        return new PercentilesResult(p50Val, p75Val, p90Val);
    }

    private static class PercentilesResult {
        final int p50;
        final int p75;
        final int p90;
        PercentilesResult(int p50, int p75, int p90) { this.p50 = p50; this.p75 = p75; this.p90 = p90; }
    }
}
