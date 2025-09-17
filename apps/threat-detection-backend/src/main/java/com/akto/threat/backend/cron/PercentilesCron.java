package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BucketStats;
import com.akto.dao.ApiInfoDao;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.akto.threat.backend.service.ApiDistributionDataService;
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
import com.akto.dao.context.Context;

public class PercentilesCron {

    private static final LoggerMaker logger = new LoggerMaker(PercentilesCron.class, LogDb.THREAT_DETECTION);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final MongoClient mongoClient;
    private static final int DEFAULT_BASELINE_DAYS = 2;
    private static final int MIN_INITIAL_AGE_DAYS = 2;

    public PercentilesCron(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public void cron(String accountId) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    try {
                        int accId = Integer.parseInt(accountId);
                        Context.accountId.set(accId);
                    } catch (Exception ignore) {
                        // keep context unset if accountId isn't a number
                    }
                    runOnce(accountId);
                } catch (Exception e) {
                    logger.errorAndAddToDb("error in PercentilesCron: accountId " + accountId + " " + e.getMessage());
                } finally {
                    Context.resetContextThreadLocals();
                }
            }
        }, 0, 2, TimeUnit.DAYS);
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

            for (int windowSize : Arrays.asList(5, 15, 30)) {
                // Ensure there exists at least one record that is MIN_INITIAL_AGE_DAYS old for this window size
                if (!hasMinimumInitialAge(accountId, apiCollectionId, url, method, MIN_INITIAL_AGE_DAYS, windowSize)) {
                    logger.infoAndAddToDb("Skipping rateLimits update due to insufficient data age for apiCollectionId " + apiCollectionId +
                            " url " + url + " method " + method + " windowSize " + windowSize);
                    continue;
                }

                // Fetch last baseline days of distribution data for this window size
                List<BucketStats> distributionData = fetchBucketStats(DEFAULT_BASELINE_DAYS, accountId, apiCollectionId, url, method, windowSize);
                PercentilesResult r = calculatePercentiles(distributionData);

                updateApiInfo(r, apiCollectionId, url, method, windowSize);
            }
        }
    }

    /**
     * Updates ApiInfo collection with the given percentiles.
     */
    public void updateApiInfo(PercentilesResult r, int apiCollectionId, String url, String method, int windowSize) {
        try {
            ApiInfoDao.instance.getMCollection().updateOne(
                    ApiInfoDao.getFilter(url, method, apiCollectionId),
                    Updates.combine(
                            Updates.set("rateLimits." + windowSize + ".p50", r.p50),
                            Updates.set("rateLimits." + windowSize + ".p75", r.p75),
                            Updates.set("rateLimits." + windowSize + ".p90", r.p90),
                            Updates.set("rateLimits." + windowSize + ".max_requests", r.maxRequests)
                    ),
                    new UpdateOptions().upsert(false)
            );
            logger.infoAndAddToDb("Updated rateLimits for apiCollectionId " + apiCollectionId + " url " + url + " method " + method + " windowSize " + windowSize,
                    LoggerMaker.LogDb.RUNTIME);
        } catch (Exception e) {
            logger.errorAndAddToDb("Failed updating rateLimits for apiCollectionId " + apiCollectionId + " url " + url + " method " + method + " windowSize " + windowSize + ": " + e.getMessage(),
                    LoggerMaker.LogDb.RUNTIME);
        }
    }


    private long getWindowStartForBaselinePeriod(int baselinePeriodDays) {
        // We store windowStart as epoch/60 (minutes since epoch). 
        long currentMinutesSinceEpoch = Context.now() / 60;
        long baselineMinutes = (long) baselinePeriodDays * 24L * 60L;
        long lowerBoundWindowStart = currentMinutesSinceEpoch - baselineMinutes; 
 
        return lowerBoundWindowStart;
    }

    /**
     * Fetches distribution documents for the given API over the past baseLinePeriod days.
     */
    public List<BucketStats> fetchBucketStats(int baseLinePeriod, String accountId, int apiCollectionId, String url, String method, int windowSize) {

        Bson filter = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("url", url),
                Filters.eq("method", method),
                Filters.eq("windowSize", windowSize),
                Filters.gte("windowStart", (int) getWindowStartForBaselinePeriod(baseLinePeriod))
        );

        return ApiDistributionDataService.fetchBucketStats(accountId, filter, mongoClient);
    }



    /**
     * Returns true if there exists at least one record with windowStart timestamp
     * that is at least minAgeDays old from now for the given API key.
     */
    public boolean hasMinimumInitialAge(String accountId, int apiCollectionId, String url, String method, int minAgeDays, int windowSize) {
        MongoCollection<ApiDistributionDataModel> coll = this.mongoClient
                .getDatabase(accountId)
                .getCollection("api_distribution_data", ApiDistributionDataModel.class);

        Bson filter = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("url", url),
                Filters.eq("method", method),
                Filters.eq("windowSize", windowSize),
                Filters.lte("windowStart", (int) getWindowStartForBaselinePeriod(minAgeDays))
        );

        try (MongoCursor<ApiDistributionDataModel> cursor = coll.find(filter).limit(1).iterator()) {
            return cursor.hasNext();
        }
    }

    /**
     * Calculate percentiles from a list of distribution docs.
     */
    public PercentilesResult calculatePercentiles(List<BucketStats> bucketStats) {

        long totalUsers = 0;

        /**
         * (288 windows in a day for every 5 minutes)
         *                                Time:5:00, 5:05, .. 7:00,  8:00,  9:00
         *  Example: B1(500-1000 Api Calls)-> [ 39,   20, ..  40,   100K,    5k]
         *
         *  TODO: What value should we pick for number of users from each bucket windows???
         *  Choosing p75 for now
         */
        for (BucketStats bstats : bucketStats) totalUsers += bstats.getP75();
        if (totalUsers <= 0) return new PercentilesResult(-1, -1, -1, -1);

        double p50Target = totalUsers * 0.5d;
        double p75Target = totalUsers * 0.75d;
        double p90Target = totalUsers * 0.9d;

        long cumulative = 0;
        Integer p50Val = null, p75Val = null, p90Val = null;

        bucketStats.sort(Comparator.comparingInt(b -> Integer.parseInt(b.getBucketLabel().substring(1))));

        for (BucketStats bstats: bucketStats) {
            long countInBucket = bstats.getP75();
            cumulative += countInBucket;
            if (p50Val == null && cumulative >= p50Target) p50Val = ThreatApiDistributionUtils.getBucketUpperBound(bstats.getBucketLabel());
            if (p75Val == null && cumulative >= p75Target) p75Val = ThreatApiDistributionUtils.getBucketUpperBound(bstats.getBucketLabel());
            if (p90Val == null && cumulative >= p90Target) { p90Val = ThreatApiDistributionUtils.getBucketUpperBound(bstats.getBucketLabel()); break; }
        }

        // If percentiles not found, use the last bucket's upper bound (max value)
        if (p50Val == null) p50Val = ThreatApiDistributionUtils.getBucketUpperBound("b14");
        if (p75Val == null) p75Val = ThreatApiDistributionUtils.getBucketUpperBound("b14");
        if (p90Val == null) p90Val = ThreatApiDistributionUtils.getBucketUpperBound("b14");

        return new PercentilesResult(p50Val, p75Val, p90Val, -1);
    }

    public static class PercentilesResult {
        final int p50;
        final int p75;
        final int p90;
        final int maxRequests;
        public PercentilesResult(int p50, int p75, int p90, int maxRequests) { this.p50 = p50; this.p75 = p75; this.p90 = p90; this.maxRequests = maxRequests; }
    }
}
