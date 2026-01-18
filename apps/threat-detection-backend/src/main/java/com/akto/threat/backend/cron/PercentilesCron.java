package com.akto.threat.backend.cron;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BucketStats;
import com.akto.dao.ApiInfoDao;
import com.akto.threat.backend.db.ApiDistributionDataModel;
import com.akto.threat.backend.db.ApiRateLimitBucketStatisticsModel;
import com.akto.threat.backend.service.ApiDistributionDataService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;

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
    public static final int DEFAULT_BASELINE_DAYS = 2;
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
        long startTime = System.currentTimeMillis();

        MongoCollection<Document> statsCollection = this.mongoClient
                .getDatabase(accountId)
                .getCollection("api_rate_limit_bucket_statistics");

        Set<String> keys = new HashSet<>();

        // Fetch only _id field (minimal data transfer)
        try (MongoCursor<Document> cursor = statsCollection.find()
                .projection(Projections.include("_id"))
                .iterator()) {

            while (cursor.hasNext()) {
                String docId = cursor.next().getString("_id");
                // docId format: "apiCollectionId_method_url_windowSize"
                // Example: "123_GET_/checkout_5"

                // Extract unique API key (without windowSize)
                String apiKey = extractApiKeyFromDocId(docId);
                if (apiKey != null) {
                    keys.add(apiKey);
                }
            }
        }

        long fetchKeysTime = System.currentTimeMillis() - startTime;
        logger.infoAndAddToDb("Fetched " + keys.size() + " unique API keys in " + fetchKeysTime + "ms");

        // Process each unique API
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

                // OPTIMIZED: Fetch pre-calculated statistics instead of querying raw distribution data
                List<BucketStats> distributionData = fetchBucketStatsFromStatistics(accountId, apiCollectionId, url, method, windowSize);

                if (distributionData.isEmpty()) {
                    logger.infoAndAddToDb("No bucket statistics available for apiCollectionId " + apiCollectionId +
                            " url " + url + " method " + method + " windowSize " + windowSize + ". Skipping.");
                    continue;
                }

                PercentilesResult r = calculatePercentiles(distributionData);

                updateApiInfo(r, apiCollectionId, url, method, windowSize);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        logger.infoAndAddToDb("PercentilesCron completed for " + keys.size() + " APIs in " + totalTime + "ms");
    }

    /**
     * Extracts unique API key from statistics document ID.
     *
     * Document ID format: "apiCollectionId_method_url_windowSize"
     * Examples:
     *   - "123_GET_/checkout_5" -> "123|/checkout|GET"
     *   - "456_POST_/api/users_15" -> "456|/api/users|POST"
     *
     * @param docId The document ID from api_rate_limit_bucket_statistics
     * @return API key in format "apiCollectionId|url|method", or null if parsing fails
     */
    private String extractApiKeyFromDocId(String docId) {
        if (docId == null || docId.isEmpty()) {
            return null;
        }

        try {
            // Remove windowSize suffix (_5, _15, or _30)
            int lastUnderscore = docId.lastIndexOf('_');
            if (lastUnderscore == -1) {
                return null;
            }

            String withoutWindowSize = docId.substring(0, lastUnderscore);
            // Now we have: "apiCollectionId_method_url"
            // Example: "123_GET_/checkout"

            // Find first underscore (after apiCollectionId)
            int firstUnderscore = withoutWindowSize.indexOf('_');
            if (firstUnderscore == -1) {
                return null;
            }

            String apiCollectionId = withoutWindowSize.substring(0, firstUnderscore);

            // Find second underscore (after method)
            int secondUnderscore = withoutWindowSize.indexOf('_', firstUnderscore + 1);
            if (secondUnderscore == -1) {
                return null;
            }

            String method = withoutWindowSize.substring(firstUnderscore + 1, secondUnderscore);
            String url = withoutWindowSize.substring(secondUnderscore + 1);

            // Return in expected format: "apiCollectionId|url|method"
            return apiCollectionId + "|" + url + "|" + method;

        } catch (Exception e) {
            logger.errorAndAddToDb("Failed to parse docId: " + docId + " - " + e.getMessage());
            return null;
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
     * OPTIMIZED: Fetches pre-calculated bucket statistics from api_rate_limit_bucket_statistics collection.
     * This is much faster than fetching raw distribution data and recalculating stats.
     *
     * Performance: Queries 1 document instead of ~576 documents, resulting in 10-50x speedup.
     *
     * @param accountId The account ID
     * @param apiCollectionId The API collection ID
     * @param url The API URL
     * @param method The HTTP method
     * @param windowSize The window size (5, 15, or 30 minutes)
     * @return List of BucketStats with pre-calculated p75 values
     */
    public List<BucketStats> fetchBucketStatsFromStatistics(String accountId, int apiCollectionId, String url, String method, int windowSize) {
        long startTime = System.currentTimeMillis();

        MongoCollection<ApiRateLimitBucketStatisticsModel> coll = mongoClient
                .getDatabase(accountId)
                .getCollection("api_rate_limit_bucket_statistics", ApiRateLimitBucketStatisticsModel.class);

        String docId = ApiRateLimitBucketStatisticsModel.getBucketStatsDocIdForApi(
            apiCollectionId, method, url, windowSize
        );

        ApiRateLimitBucketStatisticsModel doc = coll.find(Filters.eq("_id", docId)).first();

        if (doc == null || doc.getBuckets() == null) {
            logger.infoAndAddToDb("No statistics found for apiCollectionId " + apiCollectionId +
                    " url " + url + " method " + method + " windowSize " + windowSize);
            return Collections.emptyList();
        }

        List<BucketStats> result = new ArrayList<>();
        for (ApiRateLimitBucketStatisticsModel.Bucket bucket : doc.getBuckets()) {
            if (bucket.getStats() == null) {
                continue;
            }

            BucketStats stats = BucketStats.newBuilder()
                .setBucketLabel(bucket.getLabel())
                .setMin(bucket.getStats().getMin())
                .setMax(bucket.getStats().getMax())
                .setP25(bucket.getStats().getP25())
                .setP50(bucket.getStats().getP50())
                .setP75(bucket.getStats().getP75())
                .build();

            result.add(stats);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.infoAndAddToDb("Fetched bucket stats from statistics collection in " + duration + "ms for apiCollectionId " +
                apiCollectionId + " url " + url + " method " + method + " windowSize " + windowSize);

        return result;
    }

    /**
     * @deprecated Use fetchBucketStatsFromStatistics() instead for better performance.
     *
     * Fetches distribution documents for the given API over the past baseLinePeriod days.
     * This method queries raw distribution data and recalculates statistics, which is slow.
     */
    @Deprecated
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
