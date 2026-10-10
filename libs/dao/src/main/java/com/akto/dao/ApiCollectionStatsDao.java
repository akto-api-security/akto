package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.List;

/**
 * Reads are scoped to the collections the user may see in the current context, like every other
 * collection dao (AccountsContextDaoWithRbac): use findAll / count / aggregateWithRbac, not
 * getMCollection(), for anything a user's request reads.
 *
 * Writes (the refreshes that keep the rows current) deliberately use the raw collection: they run
 * in the background for every collection, and the rows hold no user specific data.
 */
public class ApiCollectionStatsDao extends AccountsContextDaoWithRbac<ApiCollectionStats> {

    public static final ApiCollectionStatsDao instance = new ApiCollectionStatsDao();

    /** Name of the count field of the $group stages that count rows. */
    public static final String COUNT = SingleTypeInfoDao._COUNT;
    /** Name of the _id field of a $group stage's output rows. */
    public static final String ID_FIELD = Constants.ID;

    /** Every field a page can be sorted on; each gets a global and a per tab index. */
    public static final String[] SORTABLE_FIELDS = {
            ApiCollectionStats.ENDPOINTS_COUNT, ApiCollectionStats.RISK_SCORE,
            ApiCollectionStats.LAST_SEEN, ApiCollectionStats.START_TS
    };

    public void createIndicesIfAbsent() {
        for (String field : SORTABLE_FIELDS) {
            // _id is the tie break of every page query, so skip/limit pages stay stable
            createIndex(Indexes.compoundIndex(Indexes.ascending(ApiCollectionStats.TAB),
                    Indexes.descending(field), Indexes.ascending(ApiCollectionStats.ID)), "tab_1_" + field + "_-1__id_1");
            createIndex(Indexes.compoundIndex(Indexes.descending(field), Indexes.ascending(ApiCollectionStats.ID)),
                    field + "_-1__id_1");
        }
        createIndex(Indexes.compoundIndex(Indexes.ascending(ApiCollectionStats.TAB),
                Indexes.ascending(ApiCollectionStats.DISPLAY_NAME), Indexes.ascending(ApiCollectionStats.ID)),
                "tab_1_displayName_1__id_1");
        createIndex(Indexes.compoundIndex(Indexes.ascending(ApiCollectionStats.DISPLAY_NAME),
                Indexes.ascending(ApiCollectionStats.ID)), "displayName_1__id_1");
        createIndex(Indexes.ascending(ApiCollectionStats.TAGS_LIST + "." + CollectionTags.KEY_NAME, ApiCollectionStats.TAGS_LIST + "." + CollectionTags.VALUE),
                "tagsList_keyName_1_value_1");
    }

    private void createIndex(Bson keys, String name) {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), keys, new IndexOptions().name(name));
    }

    @Override
    public String getFilterKeyString() {
        return ApiCollectionStats.ID;
    }

    /**
     * Scopes as the other daos do, except when the user may see every row: an $in of every id
     * would only stop mongo from serving a page from the sort index, and filters nothing.
     */
    @Override
    protected Bson modifyFilters(Bson originalQuery, boolean ignoreGroupFilter, boolean ignoreSummaryIdFilter) {
        try {
            if (Context.accountId.get() != null && (Context.userId.get() != null || Context.contextSource.get() != null)) {
                List<Integer> visible = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (visible != null && visible.size() >= getMCollection().estimatedDocumentCount()) {
                    return originalQuery;
                }
            }
        } catch (Exception e) {
            // the parent treats a failure to resolve the user's collections as unscoped too
        }
        return super.modifyFilters(originalQuery, ignoreGroupFilter, ignoreSummaryIdFilter);
    }

    /** Sets a metric of one row and stamps the time it was computed at. */
    public UpdateOneModel<ApiCollectionStats> setMetric(int id, String field, Object value, int at) {
        return new UpdateOneModel<>(Filters.eq(ApiCollectionStats.ID, id),
                Updates.combine(Updates.set(field, value), Updates.set(field + ApiCollectionStats.AT_SUFFIX, at)));
    }

    public void bulkWrite(List<WriteModel<ApiCollectionStats>> writes) {
        if (writes.isEmpty()) return;
        getMCollection().bulkWrite(writes, new BulkWriteOptions().ordered(false));
        writes.clear();
    }

    /** Rows whose value for this metric predates the refresh that started at startedAt (or never had one). */
    public static Bson staleSince(String field, int startedAt) {
        String atField = field + ApiCollectionStats.AT_SUFFIX;
        return Filters.or(Filters.lt(atField, startedAt), Filters.exists(atField, false));
    }

    /** What a refresh did not reach has no value any more: set it to the metric's empty value. */
    public void resetStale(String field, Object emptyValue, int startedAt) {
        getMCollection().updateMany(staleSince(field, startedAt), Updates.combine(
                Updates.set(field, emptyValue), Updates.set(field + ApiCollectionStats.AT_SUFFIX, startedAt)));
    }

    @Override
    public String getCollName() {
        return "api_collection_stats";
    }

    @Override
    public Class<ApiCollectionStats> getClassT() {
        return ApiCollectionStats.class;
    }
}
