package com.akto.dao;

import com.akto.dto.ApiCollectionStats;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

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
        // oldest posture first, for the refresh that recomputes the least recently computed collections
        createIndex(Indexes.ascending(ApiCollectionStats.POSTURE_AT), "posture_at_1");
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

    @Override
    public String getCollName() {
        return "api_collection_stats";
    }

    @Override
    public Class<ApiCollectionStats> getClassT() {
        return ApiCollectionStats.class;
    }
}
