package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiStats;
import com.akto.service.TimedService;
import com.akto.usage.UsageMetricCalculator;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * The api numbers of accounts without endpoint_info_views: a sum over the per collection counts of
 * api_collection_stats (kept current by {@link PosturePartialsRefresh}), so a request never reads
 * api_info for the whole account. The stats rows come through the dao, which scopes them to what the
 * user may see.
 *
 * The start of a range is the end numbers less the apis discovered after it, which is an index range
 * on api_info's discoveredTimestamp: a few days of new apis, not the collection.
 */
class PartialsPostureSource extends TimedService implements PostureSource {

    // the most apis discovered after the start of the range that are counted to work the start out
    static final int WINDOW_DOC_CAP = 200_000;
    static final int WINDOW_MAX_TIME_SECONDS = 20;
    // an end earlier than this is not "now": the apis discovered since are taken out of the end numbers
    static final int END_IS_NOW_SECONDS = 3600;

    private static final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    @Override
    public PostureData load(int startTimestamp, int endTimestamp) {
        int now = Context.now();
        Set<Integer> excluded = UsageMetricCalculator.getDemosAndDeactivated();

        PostureCounts all = timed("sum the posture of the collections", () -> sumCollections(excluded));
        PostureCounts end = all;
        if (endTimestamp < now - END_IS_NOW_SECONDS) {
            PostureCounts later = timed("apis discovered after the end of the range", () -> discoveredAfter(endTimestamp, excluded, now));
            if (later != null) end = all.minus(later);
        }
        PostureCounts afterStart = timed("apis discovered after the start of the range", () -> discoveredAfter(startTimestamp, excluded, now));
        boolean startUnavailable = afterStart == null;

        return new PostureData(startUnavailable ? new ApiStats(startTimestamp) : all.minus(afterStart).toApiStats(startTimestamp),
                end.toApiStats(endTimestamp), startUnavailable, end,
                end.get(PostureCounts.ENDPOINTS_COUNT) - end.get(PostureMetric.TOTAL),
                all.get(PostureCounts.COUNTED_ROWS) < all.get(PostureCounts.ROWS));
    }

    int windowDocCap() {
        return WINDOW_DOC_CAP;
    }

    private PostureCounts sumCollections(Set<Integer> excluded) {
        List<BsonField> sums = new ArrayList<>();
        for (PostureMetric metric : PostureMetric.ALL) {
            sums.add(Accumulators.sum(metric.getField(), field(ApiCollectionStats.POSTURE + "." + metric.getField())));
        }
        // sensitivity is single_type_info's: the sensitive refresh's count per collection, and the recount's sensitive and unauthenticated ones
        sums.add(Accumulators.sum(PostureMetric.SENSITIVE, field(ApiCollectionStats.SENSITIVE_APIS_COUNT)));
        sums.add(Accumulators.sum(PostureMetric.SENSITIVE_UNAUTHENTICATED,
                field(ApiCollectionStats.POSTURE + "." + PostureMetric.SENSITIVE_UNAUTHENTICATED)));
        // only collections in testing scope count towards the tested apis
        sums.add(Accumulators.sum(PostureCounts.IN_SCOPE_TOTAL, inScopeOnly(PostureMetric.TOTAL)));
        sums.add(Accumulators.sum(PostureCounts.IN_SCOPE_TESTED, inScopeOnly(PostureMetric.TESTED_IN_LOOKBACK)));
        sums.add(Accumulators.sum(PostureCounts.ENDPOINTS_COUNT, field(ApiCollectionStats.ENDPOINTS_COUNT)));
        sums.add(Accumulators.sum(PostureCounts.ROWS, 1));
        sums.add(Accumulators.sum(PostureCounts.COUNTED_ROWS, cond(gt(ifNull(field(ApiCollectionStats.POSTURE_AT), 0), 0), 1, 0)));

        List<Bson> pipeline = Arrays.asList(
                // groups have no api_info of their own: their apis are counted in the collections that hold them
                Aggregates.match(Filters.and(
                        Filters.in(ApiCollectionStats.TAB, ApiCollectionStats.Tab.HOSTNAME.name(), ApiCollectionStats.Tab.CUSTOM.name()),
                        Filters.nin(ApiCollectionStats.ID, excluded))),
                Aggregates.group(null, sums));
        BasicDBObject row = statsDao.aggregateWithRbac(pipeline).first();
        return PostureCounts.of(row);
    }

    /** The collection's count of the metric, or 0 when the collection is out of testing scope. */
    private Object inScopeOnly(String metricField) {
        return cond(eq(field(ApiCollectionStats.IS_OUT_OF_TESTING_SCOPE), true), 0,
                ifNull(field(ApiCollectionStats.POSTURE + "." + metricField), 0));
    }

    /**
     * The sums of the apis discovered after the timestamp, or null when there are too many of them to
     * count cheaply (the caller then reports the number as unavailable instead of scanning).
     */
    private PostureCounts discoveredAfter(int timestamp, Set<Integer> excluded, int now) {
        List<Integer> outOfScope = new ArrayList<>();
        for (ApiCollectionStats stats : statsDao.findAllNoRbacFilter(
                Filters.eq(ApiCollectionStats.IS_OUT_OF_TESTING_SCOPE, true), Projections.include(ApiCollectionStats.ID))) {
            outOfScope.add(stats.getId());
        }

        List<BsonField> sums = new ArrayList<>();
        for (PostureMetric metric : PostureMetric.ALL) {
            sums.add(Accumulators.sum(metric.getField(), metric.getSumExpression().apply(now)));
        }
        Object outOfScopeCollection = in(field(ApiInfo.ID_API_COLLECTION_ID), outOfScope);
        sums.add(Accumulators.sum(PostureCounts.IN_SCOPE_TOTAL, cond(outOfScopeCollection, 0, 1)));
        sums.add(Accumulators.sum(PostureCounts.IN_SCOPE_TESTED,
                cond(outOfScopeCollection, 0, PostureMetric.of(PostureMetric.TESTED_IN_LOOKBACK).getSumExpression().apply(now))));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                        Filters.gt(ApiInfo.DISCOVERED_TIMESTAMP, timestamp),
                        Filters.nin(ApiInfo.ID_API_COLLECTION_ID, excluded))),
                // one more than the cap, to tell "exactly the cap" from "more than the cap"
                Aggregates.limit(windowDocCap() + 1),
                Aggregates.group(null, sums));
        BasicDBObject row = ApiInfoDao.instance.aggregateWithRbac(pipeline).maxTime(WINDOW_MAX_TIME_SECONDS, TimeUnit.SECONDS).first();
        PostureCounts counts = PostureCounts.of(row);
        return counts.get(PostureMetric.TOTAL) > windowDocCap() ? null : counts;
    }
}
