package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.TimedService;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.MergeOptions;
import com.mongodb.client.model.MergeOptions.WhenMatched;
import com.mongodb.client.model.MergeOptions.WhenNotMatched;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * Per collection max riskScore / lastSeen, straight from api_info for every account.
 *
 * Normal collections: "$sort by (collection, metric desc) then $group $first" is served by
 * {_id.apiCollectionId, metric} as a DISTINCT_SCAN, i.e. one index key per collection, and the
 * result is $merge'd back inside mongo.
 * Api groups only exist in the collectionIds array, so they get one indexed seek each.
 *
 * Runs in the background over every collection, so it reads the raw collections and the dao's
 * NoRbacFilter variants: what a user may see is decided when a page is read (ApiCollectionStatsDao).
 */
class ApiInfoMetricsRefresh extends TimedService {

    private static final String MAX_VALUE = "max";

    void refreshRiskScore() {
        refresh(ApiCollectionStats.RISK_SCORE, ApiInfo.RISK_SCORE, true, ApiInfo::getRiskScore);
    }

    void refreshLastSeen() {
        // the existing {_id.apiCollectionId: -1, lastSeen: -1} index runs descending on both keys
        refresh(ApiCollectionStats.LAST_SEEN, ApiInfo.LAST_SEEN, false, ApiInfo::getLastSeen);
    }

    private void refresh(String metric, String apiInfoField, boolean collectionAscending, Function<ApiInfo, Number> valueOf) {
        int startedAt = Context.now();
        timed(metric + ": $merge max per collection from api_info",
                () -> mergeForCollections(metric, apiInfoField, collectionAscending, startedAt));
        timed(metric + ": seek per api group and reset collections without api_info", () -> {
            try (ApiCollectionStatsMetricWriter writer = new ApiCollectionStatsMetricWriter(metric, startedAt)) {
                seekForGroups(apiInfoField, valueOf, writer);
                // collections with no api_info at all got nothing from the merge
                writer.resetUnwritten(0);
            }
        });
    }

    private void mergeForCollections(String metric, String apiInfoField, boolean collectionAscending, int startedAt) {
        // the key order of whichever index serves this metric, so the sort needs no in-memory step
        Bson collectionSort = collectionAscending ? Sorts.ascending(ApiInfo.ID_API_COLLECTION_ID) : Sorts.descending(ApiInfo.ID_API_COLLECTION_ID);
        List<Bson> pipeline = Arrays.asList(
                Aggregates.sort(Sorts.orderBy(collectionSort, Sorts.descending(apiInfoField))),
                Aggregates.group(field(ApiInfo.ID_API_COLLECTION_ID), Accumulators.first(MAX_VALUE, field(apiInfoField))),
                Aggregates.merge(ApiCollectionStatsDao.instance.getCollName(), new MergeOptions()
                        .uniqueIdentifier(ApiCollectionStats.ID)
                        .whenMatched(WhenMatched.PIPELINE)
                        .whenMatchedPipeline(Arrays.asList(Aggregates.addFields(
                                new Field<>(metric, ifNull(merged(MAX_VALUE), 0)),
                                new Field<>(metric + ApiCollectionStats.AT_SUFFIX, startedAt))))
                        .whenNotMatched(WhenNotMatched.DISCARD)));
        ApiInfoDao.instance.getMCollection().aggregate(pipeline).allowDiskUse(true).toCollection();
    }

    private void seekForGroups(String apiInfoField, Function<ApiInfo, Number> valueOf, ApiCollectionStatsMetricWriter writer) {
        for (ApiCollectionStats group : ApiCollectionStatsDao.instance.findAllNoRbacFilter(
                Filters.eq(ApiCollectionStats.TAB, ApiCollectionStats.Tab.GROUP.name()),
                Projections.include(ApiCollectionStats.ID))) {
            ApiInfo top = ApiInfoDao.instance.getMCollection()
                    .find(Filters.eq(SingleTypeInfo._COLLECTION_IDS, group.getId()))
                    .sort(Sorts.descending(apiInfoField)).limit(1)
                    .projection(Projections.include(apiInfoField)).first();
            writer.set(group.getId(), top == null ? 0 : valueOf.apply(top));
        }
    }
}
