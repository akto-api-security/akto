package com.akto.service.collections;

import com.akto.dto.ApiCollectionStats;
import com.akto.dto.traffic.CollectionTags;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Turns a CollectionsPageRequest into the filter and sort of one find on api_collection_stats. */
public class CollectionsPageQueryBuilder {

    public static final String TAB_ALL = "ALL";

    /** The table's sort options: the key it sends, the stats field that orders by it, and how ties break. */
    @Getter
    @RequiredArgsConstructor
    enum SortKey {
        ENDPOINTS("urlsCount", ApiCollectionStats.ENDPOINTS_COUNT, false),
        RISK_SCORE("riskScore", ApiCollectionStats.RISK_SCORE, false),
        DISCOVERED("startTs", ApiCollectionStats.START_TS, false),
        LAST_SEEN("detectedTimestamp", ApiCollectionStats.LAST_SEEN, false),
        NAME("customGroupsSort", ApiCollectionStats.DISPLAY_NAME, true);

        private final String key;
        private final String field;
        /** names are indexed {displayName: 1, _id: 1}; the others {field: -1, _id: 1} */
        private final boolean tieBreakFollowsOrder;

        static SortKey fromKey(String key) {
            return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst().orElse(null);
        }
    }

    /** The request's own conditions; which collections the user may see is the dao's to add (ApiCollectionStatsDao). */
    public static Bson buildFilter(CollectionsPageRequest request) {
        List<Bson> filters = new ArrayList<>();

        if (request.getTab() != null && !request.getTab().isEmpty() && !TAB_ALL.equalsIgnoreCase(request.getTab())) {
            filters.add(Filters.eq(ApiCollectionStats.TAB, request.getTab().toUpperCase()));
        }
        if (request.getQueryValue() != null && !request.getQueryValue().trim().isEmpty()) {
            // quoted: the search box is text, not a regex
            filters.add(Filters.regex(ApiCollectionStats.DISPLAY_NAME, Pattern.quote(request.getQueryValue().trim()), "i"));
        }

        List<String> outOfScope = request.getFilters().get(ApiCollectionStats.IS_OUT_OF_TESTING_SCOPE);
        if (outOfScope != null && !outOfScope.isEmpty()) {
            List<Boolean> values = new ArrayList<>();
            for (String v : outOfScope) values.add(Boolean.parseBoolean(v));
            filters.add(Filters.in(ApiCollectionStats.IS_OUT_OF_TESTING_SCOPE, values));
        }
        List<String> accessType = request.getFilters().get(ApiCollectionStats.ACCESS_TYPE);
        if (accessType != null && !accessType.isEmpty()) {
            filters.add(Filters.in(ApiCollectionStats.ACCESS_TYPE, accessType));
        }

        for (Map.Entry<String, List<String>> tag : request.getTagFilters().entrySet()) {
            if (tag.getValue() == null || tag.getValue().isEmpty()) continue;
            filters.add(Filters.elemMatch(ApiCollectionStats.TAGS_LIST, Filters.and(
                    Filters.eq(CollectionTags.KEY_NAME, tag.getKey()), Filters.in(CollectionTags.VALUE, tag.getValue()))));
        }

        return filters.isEmpty() ? Filters.empty() : Filters.and(filters);
    }

    /**
     * _id always breaks ties so skip/limit pages never repeat or drop a row. Its direction is the
     * one the indexes were built with: {field: -1, _id: 1} serves (-1, 1) and its mirror (1, -1);
     * names are {displayName: 1, _id: 1}, serving (1, 1) and (-1, -1).
     */
    public static Bson buildSort(String sortKey, int sortOrder) {
        SortKey sort = SortKey.fromKey(sortKey);
        if (sort == null) {
            // no (or an unknown) sort: most endpoints first
            sort = SortKey.ENDPOINTS;
            sortOrder = -1;
        }
        int tieBreak = sort.isTieBreakFollowsOrder() ? sortOrder : -sortOrder;
        return Sorts.orderBy(
                sortOrder > 0 ? Sorts.ascending(sort.getField()) : Sorts.descending(sort.getField()),
                tieBreak > 0 ? Sorts.ascending(ApiCollectionStats.ID) : Sorts.descending(ApiCollectionStats.ID));
    }
}
