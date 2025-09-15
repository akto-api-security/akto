package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.dao.ApiInfoDao;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.UpdateOptions;
import com.akto.utils.ThreatApiDistributionUtils;

public class PercentilesCron {

    private static final LoggerMaker logger = new LoggerMaker(PercentilesCron.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final MongoClient mongoClient;
    private static final int DEFAULT_BASELINE_DAYS = 2;

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

            // Fetch last baseline days of distribution data
            List<ApiDistributionDataModel> distributionData = fetchDistributionDocs(DEFAULT_BASELINE_DAYS, accountId, apiCollectionId, url, method);
            PercentilesResult r = calculatePercentiles(distributionData, DEFAULT_BASELINE_DAYS);

            updateApiInfo(r, apiCollectionId, url, method);
        }
    }

    /**
     * Updates ApiInfo collection with the given percentiles.
     */
    public void updateApiInfo(PercentilesResult r, int apiCollectionId, String url, String method) {
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

    /**
     * Fetches distribution documents for the given API over the past baseLinePeriod days.
     */
    public List<ApiDistributionDataModel> fetchDistributionDocs(int baseLinePeriod, String accountId, int apiCollectionId, String url, String method) {
        MongoCollection<ApiDistributionDataModel> coll = this.mongoClient
                .getDatabase(accountId)
                .getCollection("api_distribution_data", ApiDistributionDataModel.class);

        // We store windowStart as epoch/60 (minutes since epoch). Filter using this within baseline period.
        long currentMinutesSinceEpoch = Instant.now().getEpochSecond() / 60;
        long baselineMinutes = (long) baseLinePeriod * 24L * 60L;
        long lowerBoundWindowStart = currentMinutesSinceEpoch - baselineMinutes;

        Bson filter = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("url", url),
                Filters.eq("method", method),
                Filters.gte("windowStart", (int) lowerBoundWindowStart)
        );

        List<ApiDistributionDataModel> docs = new ArrayList<>();
        try (MongoCursor<ApiDistributionDataModel> cursor = coll.find(filter).iterator()) {
            while (cursor.hasNext()) {
                docs.add(cursor.next());
            }
        }
        return docs;
    }

    /**
     * Calculate percentiles from a list of distribution docs.
     */
    public PercentilesResult calculatePercentiles(List<ApiDistributionDataModel> distributionData, int baselinePeriodDays) {
        Map<String, Long> bucketToCount = new HashMap<>();

        for (ApiDistributionDataModel doc : distributionData) {
            if (doc.distribution == null) continue;
            for (Map.Entry<String, Integer> e : doc.distribution.entrySet()) {
                bucketToCount.merge(e.getKey(), e.getValue() == null ? 0L : e.getValue().longValue(), Long::sum);
            }
        }

        List<String> bucketOrder = ThreatApiDistributionUtils.getBucketOrder();
        List<Integer> bucketLowerBounds = ThreatApiDistributionUtils.getBucketLowerBounds();

        long totalUsers = 0;
        for (String b : bucketOrder) totalUsers += bucketToCount.getOrDefault(b, 0L);
        if (totalUsers <= 0) return new PercentilesResult(-1, -1, -1);

        double p50Target = totalUsers * 0.5d;
        double p75Target = totalUsers * 0.75d;
        double p90Target = totalUsers * 0.9d;

        long cumulative = 0;
        Integer p50Val = null, p75Val = null, p90Val = null;
        for (int i = 0; i < bucketOrder.size(); i++) {
            String b = bucketOrder.get(i);
            long countInBucket = bucketToCount.getOrDefault(b, 0L);
            cumulative += countInBucket;
            int lower = bucketLowerBounds.get(i);
            if (p50Val == null && cumulative >= p50Target) p50Val = lower;
            if (p75Val == null && cumulative >= p75Target) p75Val = lower;
            if (p90Val == null && cumulative >= p90Target) { p90Val = lower; break; }
        }

        if (p50Val == null) p50Val = bucketLowerBounds.get(bucketLowerBounds.size() - 1);
        if (p75Val == null) p75Val = bucketLowerBounds.get(bucketLowerBounds.size() - 1);
        if (p90Val == null) p90Val = bucketLowerBounds.get(bucketLowerBounds.size() - 1);
        return new PercentilesResult(p50Val, p75Val, p90Val);
    }

    public static class PercentilesResult {
        final int p50;
        final int p75;
        final int p90;
        public PercentilesResult(int p50, int p75, int p90) { this.p50 = p50; this.p75 = p75; this.p90 = p90; }
    }
}
