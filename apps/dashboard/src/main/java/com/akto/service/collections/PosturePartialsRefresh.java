package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.service.TimedService;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.MergeOptions;
import com.mongodb.client.model.MergeOptions.WhenMatched;
import com.mongodb.client.model.MergeOptions.WhenNotMatched;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.BsonField;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * Keeps the per collection api_info counts of the home dashboard (the "posture" of
 * api_collection_stats) current without ever reading all of api_info.
 *
 * Only collections whose numbers can have moved are recounted: new traffic (lastSeen), a new risk
 * score, never counted, or not counted for a while. A recount of a batch of collections is one
 * aggregation over {_id.apiCollectionId: {$in: batch}}, served by the existing api_info index, so
 * mongo reads just those collections' documents and the counts are $merge'd back inside mongo.
 * Batches are bounded by the number of api_info documents they cover, the run by time, with a short
 * pause between batches so a first fill of a large account does not hold the database busy.
 */
class PosturePartialsRefresh extends TimedService {

    // documents a batch may read: the sum of the batch collections' endpoint counts
    static final int DOC_BUDGET_PER_BATCH = 50_000;
    // collections considered per batch pick; the batch is cut at the doc budget
    static final int CANDIDATE_LIMIT = 2_000;
    // one refresh run stops picking batches after this long
    static final int RUN_BUDGET_SECONDS = 60;
    static final int PAUSE_BETWEEN_BATCHES_MS = 100;
    // collections not recounted for this long are recounted even without a sign of change
    static final int SAFETY_SWEEP_SECONDS = 1800;

    private static final String COUNT = "count";

    private static final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    /** Whether some collection that is counted has never been (a first fill is still going on). */
    static boolean hasUncountedCollections() {
        return !statsDao.findRowsToRefresh(Filters.and(
                        Filters.in(ApiCollectionStats.TAB, ApiCollectionStats.Tab.HOSTNAME.name(), ApiCollectionStats.Tab.CUSTOM.name()),
                        Filters.exists(ApiCollectionStats.POSTURE_AT, false)),
                Sorts.ascending(ApiCollectionStats.ID), 1, Projections.include(ApiCollectionStats.ID)).isEmpty();
    }

    void refresh() {
        // the view already holds these numbers: nothing is counted for an account that reads them from it
        if (PostureSource.viewHasRows()) return;
        int deadline = Context.now() + RUN_BUDGET_SECONDS;
        while (Context.now() < deadline) {
            List<ApiCollectionStats> batch = timed("pick collections to recount", () -> pickBatch(Context.now()), List::size);
            if (batch.isEmpty()) return;
            List<Integer> ids = batch.stream().map(ApiCollectionStats::getId).collect(Collectors.toList());
            timed("recount " + ids.size() + " collections", () -> recount(ids));
            pause();
        }
    }

    /** Collections whose counts are out of date, least recently counted first, up to the document budget. */
    List<ApiCollectionStats> pickBatch(int now) {
        Bson filter = Filters.and(
                Filters.in(ApiCollectionStats.TAB, ApiCollectionStats.Tab.HOSTNAME.name(), ApiCollectionStats.Tab.CUSTOM.name()),
                Filters.or(
                        Filters.exists(ApiCollectionStats.POSTURE_AT, false),
                        Filters.expr(gt(field(ApiCollectionStats.LAST_SEEN), field(ApiCollectionStats.POSTURE_AT))),
                        // the collection's highest risk score moved (riskScoreAt moves on every risk refresh, whether or not a score did)
                        Filters.expr(ne(riskScoreNow(), ifNull(field(ApiCollectionStats.POSTURE + "." + PostureMetric.RISK_SEEN), -1))),
                        // the sensitive refresh found another number of sensitive apis: the sensitive and unauthenticated ones may have moved
                        Filters.expr(ne(ifNull(field(ApiCollectionStats.SENSITIVE_APIS_COUNT), 0),
                                ifNull(field(ApiCollectionStats.POSTURE + "." + PostureMetric.SENSITIVE_APIS_SEEN), -1))),
                        Filters.lt(ApiCollectionStats.POSTURE_AT, now - SAFETY_SWEEP_SECONDS)));

        List<ApiCollectionStats> candidates = statsDao.findRowsToRefresh(filter,
                Sorts.ascending(ApiCollectionStats.POSTURE_AT), CANDIDATE_LIMIT,
                Projections.include(ApiCollectionStats.ID, ApiCollectionStats.ENDPOINTS_COUNT));

        List<ApiCollectionStats> batch = new ArrayList<>();
        long docs = 0;
        for (ApiCollectionStats candidate : candidates) {
            long cost = Math.max(candidate.getEndpointsCount(), 1);
            // always take at least one collection, even one larger than the budget
            if (!batch.isEmpty() && docs + cost > DOC_BUDGET_PER_BATCH) break;
            batch.add(candidate);
            docs += cost;
        }
        return batch;
    }

    void recount(List<Integer> collectionIds) {
        int startedAt = Context.now();
        // first, so that a failure here leaves the collections due for another recount
        timed("count sensitive and unauthenticated apis", () -> writeSensitiveUnauthenticated(collectionIds));
        mergeCounts(collectionIds, startedAt);
        // collections without a single api_info document got nothing from the merge: their counts are all zero
        statsDao.getMCollection().updateMany(
                Filters.and(Filters.in(ApiCollectionStats.ID, collectionIds), Filters.or(
                        Filters.lt(ApiCollectionStats.POSTURE_AT, startedAt), Filters.exists(ApiCollectionStats.POSTURE_AT, false))),
                Collections.singletonList(Aggregates.addFields(new Field<>(ApiCollectionStats.POSTURE, object(emptyPosture(startedAt))))));
    }

    /**
     * The unauthenticated apis that are sensitive, per collection: the batch's unauthenticated api_info
     * documents (a small part of them) each looked up in single_type_info, see {@link SensitiveApiStages}.
     * Written with the other counts' merge keeping it, so a collection never shows a gap.
     */
    private void writeSensitiveUnauthenticated(List<Integer> collectionIds) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds),
                PostureMetric.onlyUnauthenticatedFilter())));
        pipeline.addAll(SensitiveApiStages.keepSensitive());
        pipeline.add(Aggregates.group(field(ApiInfo.ID_API_COLLECTION_ID), Accumulators.sum(COUNT, 1)));

        Map<Integer, Integer> counts = new HashMap<>();
        for (BasicDBObject row : ApiInfoDao.instance.getMCollection().withDocumentClass(BasicDBObject.class)
                .aggregate(pipeline).allowDiskUse(true).maxTime(RUN_BUDGET_SECONDS, TimeUnit.SECONDS)) {
            counts.put(row.getInt(Constants.ID), row.getInt(COUNT));
        }
        List<WriteModel<ApiCollectionStats>> writes = new ArrayList<>();
        for (int id : collectionIds) {
            writes.add(new UpdateOneModel<>(Filters.eq(ApiCollectionStats.ID, id),
                    Updates.set(ApiCollectionStats.POSTURE + "." + PostureMetric.SENSITIVE_UNAUTHENTICATED, counts.getOrDefault(id, 0))));
        }
        statsDao.bulkWrite(writes);
    }

    private void mergeCounts(List<Integer> collectionIds, int startedAt) {
        List<BsonField> counts = PostureMetric.ALL.stream()
                .map(metric -> Accumulators.sum(metric.getField(), metric.getSumExpression().apply(startedAt)))
                .collect(Collectors.toList());

        Map<String, Object> posture = new LinkedHashMap<>();
        posture.put(ApiCollectionStats.POSTURE_AT_KEY, startedAt);
        for (PostureMetric metric : PostureMetric.ALL) {
            posture.put(metric.getField(), ifNull(merged(metric.getField()), 0));
        }
        posture.putAll(sensitivePosture());

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds)),
                Aggregates.group(field(ApiInfo.ID_API_COLLECTION_ID), counts),
                Aggregates.merge(statsDao.getCollName(), new MergeOptions()
                        .uniqueIdentifier(ApiCollectionStats.ID)
                        .whenMatched(WhenMatched.PIPELINE)
                        .whenMatchedPipeline(Arrays.asList(Aggregates.addFields(new Field<>(ApiCollectionStats.POSTURE, object(posture)))))
                        .whenNotMatched(WhenNotMatched.DISCARD)));
        ApiInfoDao.instance.getMCollection().aggregate(pipeline).allowDiskUse(true).toCollection();
    }

    /** The stats row's highest risk score of the collection, as the integer a posture keeps it as. */
    private static Object riskScoreNow() {
        return round(multiply(ifNull(field(ApiCollectionStats.RISK_SCORE), 0), PostureMetric.RISK_SCORE_SCALE));
    }

    /**
     * What a new posture keeps from the stats row: the sensitive and unauthenticated count (written by its own
     * pass) and the signals it was counted at, which a recount is due when they differ from the row's now.
     */
    private Map<String, Object> sensitivePosture() {
        Map<String, Object> kept = new LinkedHashMap<>();
        kept.put(PostureMetric.RISK_SEEN, riskScoreNow());
        kept.put(PostureMetric.SENSITIVE_UNAUTHENTICATED,
                ifNull(field(ApiCollectionStats.POSTURE + "." + PostureMetric.SENSITIVE_UNAUTHENTICATED), 0));
        kept.put(PostureMetric.SENSITIVE_APIS_SEEN, ifNull(field(ApiCollectionStats.SENSITIVE_APIS_COUNT), 0));
        return kept;
    }

    private Map<String, Object> emptyPosture(int at) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put(ApiCollectionStats.POSTURE_AT_KEY, at);
        PostureMetric.ALL.forEach(metric -> empty.put(metric.getField(), 0));
        empty.putAll(sensitivePosture());
        // no api_info document: none is sensitive and unauthenticated, whatever single_type_info says
        empty.put(PostureMetric.SENSITIVE_UNAUTHENTICATED, 0);
        return empty;
    }

    private void pause() {
        try {
            Thread.sleep(PAUSE_BETWEEN_BATCHES_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
