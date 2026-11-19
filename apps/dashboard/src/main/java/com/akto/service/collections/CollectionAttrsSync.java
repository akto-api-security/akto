package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.TimedService;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.MergeOptions;
import com.mongodb.client.model.MergeOptions.WhenMatched;
import com.mongodb.client.model.MergeOptions.WhenNotMatched;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * Keeps the filterable attributes of api_collection_stats equal to api_collections, entirely inside
 * mongo ($project -> $merge), so no collection document travels through the jvm.
 *
 * Three entry points: a full pass (also drops rows whose collection is gone), a delta pass for
 * collections created by traffic, which never go through a dashboard action, and a by-id pass
 * the mutating actions call so their change shows up on the very next page load.
 */
public class CollectionAttrsSync extends TimedService {

    private static final int DELTA_LOOKBACK_SECONDS = 120;

    private static final String TAG_VARIABLE = "tag";

    public void syncAll() {
        timed("syncAll", () -> {
            long start = System.currentTimeMillis();
            merge(null, start);
            ApiCollectionStatsDao.instance.getMCollection()
                    .deleteMany(Filters.lt(ApiCollectionStats.ATTRS_SYNCED_AT, start));
        });
    }

    public void syncRecent() {
        timed("syncRecent", () ->
                merge(Filters.gte(ApiCollection.START_TS, Context.now() - DELTA_LOOKBACK_SECONDS), System.currentTimeMillis()));
    }

    /** Creates / refreshes the rows of these ids and removes the rows whose collection no longer exists. */
    public void syncIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        timed("syncIds " + ids.size() + " collections", () -> {
            long start = System.currentTimeMillis();
            merge(Filters.in(ApiCollection.ID, ids), start);
            ApiCollectionStatsDao.instance.getMCollection().deleteMany(Filters.and(
                    Filters.in(ApiCollectionStats.ID, ids),
                    Filters.lt(ApiCollectionStats.ATTRS_SYNCED_AT, start)));
        });
    }

    /** For callers whose own work must not fail because the stats could not be kept in step. */
    public void syncIdsAndRecentQuietly(Collection<Integer> ids) {
        try {
            syncIds(ids);
            syncRecent();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error syncing api collection stats attrs: " + e.getMessage());
        }
    }

    private void merge(Bson match, long syncedAtMs) {
        List<Bson> pipeline = new ArrayList<>();
        if (match != null) pipeline.add(Aggregates.match(match));
        pipeline.add(Aggregates.project(attrsProjection(syncedAtMs)));
        // "merge": keep the metric fields that are already on the row
        pipeline.add(Aggregates.merge(ApiCollectionStatsDao.instance.getCollName(), new MergeOptions()
                .uniqueIdentifier(ApiCollectionStats.ID)
                .whenMatched(WhenMatched.MERGE)
                .whenNotMatched(WhenNotMatched.INSERT)));
        ApiCollectionsDao.instance.getMCollection().aggregate(pipeline).allowDiskUse(true).toCollection();
    }

    private Bson attrsProjection(long syncedAtMs) {
        String hostName = field(ApiCollection.HOST_NAME);
        String name = field(ApiCollection.NAME);
        Document nameOrEmpty = ifNull(name, "");
        Document hostNameSet = ne(ifNull(hostName, null), null);
        Document nameSet = ne(ifNull(name, null), null);
        Document serviceTagSet = ne(ifNull(field(ApiCollection.SERVICE_TAG), ""), "");

        // same as ApiCollection#getDisplayName
        Document displayName = cond(serviceTagSet, nameOrEmpty,
                cond(hostNameSet, cond(nameSet, concat(hostName, " - ", name), hostName), nameOrEmpty));

        // same split as categorizeCollections in ApiCollections.jsx
        Document tab = cond(eq(ifNull(field(ApiCollection._DEACTIVATED), false), true),
                ApiCollectionStats.Tab.DEACTIVATED.name(),
                cond(hostNameSet, ApiCollectionStats.Tab.HOSTNAME.name(),
                        cond(eq(field(ApiCollection._TYPE), ApiCollection.Type.API_GROUP.name()),
                                ApiCollectionStats.Tab.GROUP.name(), ApiCollectionStats.Tab.CUSTOM.name())));

        // only what filtering needs of a tag
        String tagField = "$$" + TAG_VARIABLE + ".";
        Document tags = map(ifNull(field(ApiCollection.TAGS_STRING), Collections.emptyList()), TAG_VARIABLE,
                Projections.fields(
                        Projections.computed(CollectionTags.KEY_NAME, tagField + CollectionTags.KEY_NAME),
                        Projections.computed(CollectionTags.VALUE, tagField + CollectionTags.VALUE)));

        String urls = field(ApiCollection.URLS_STRING);

        return Projections.fields(
                Projections.include(ApiCollection.NAME, ApiCollection.HOST_NAME, ApiCollection._TYPE,
                        ApiCollection.DESCRIPTION, ApiCollection.ACCESS_TYPE),
                Projections.computed(ApiCollectionStats.DISPLAY_NAME, displayName),
                Projections.computed(ApiCollectionStats.DEACTIVATED, ifNull(field(ApiCollection._DEACTIVATED), false)),
                Projections.computed(ApiCollectionStats.START_TS, ifNull(field(ApiCollection.START_TS), 0)),
                Projections.computed(ApiCollectionStats.IS_OUT_OF_TESTING_SCOPE,
                        ifNull(field(ApiCollection.IS_OUT_OF_TESTING_SCOPE), false)),
                Projections.computed(ApiCollectionStats.TAB, tab),
                Projections.computed(ApiCollectionStats.TAGS_LIST, tags),
                Projections.computed(ApiCollectionStats.URLS_FALLBACK_COUNT,
                        size(cond(isArray(urls), urls, Collections.emptyList()))),
                Projections.computed(ApiCollectionStats.ATTRS_SYNCED_AT, literal(syncedAtMs)));
    }
}
