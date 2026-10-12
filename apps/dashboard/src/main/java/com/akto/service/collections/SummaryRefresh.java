package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.TimedService;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.field;

/**
 * The account wide numbers of the summary card plus the tag filter choices. Derived from the stats
 * rows (and two cheap indexed api_info counts), so the page header is one meta read.
 *
 * Account wide by design: it runs in the background, over every collection.
 */
class SummaryRefresh extends TimedService {

    private static final int RISK_SCORE_CRITICAL_THRESHOLD = 4;
    private static final int MAX_TAG_VALUES = 200;
    private static final String TOTAL = "total";

    private static final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    void refresh() {
        // same exclusions as transform.getSummaryData: no groups, no deactivated
        // the Total APIs tile is not here: the page loads it separately from fetchEndpointsCount
        Bson countable = Filters.in(ApiCollectionStats.TAB,
                ApiCollectionStats.Tab.HOSTNAME.name(), ApiCollectionStats.Tab.CUSTOM.name());
        Bson inTestingScope = Filters.and(countable, Filters.ne(ApiCollectionStats.IS_OUT_OF_TESTING_SCOPE, true));

        int totalAllowedForTesting = timed("sum of endpoints in testing scope", () -> sumEndpoints(inTestingScope));
        int tested = timed("tested api_info count", () -> countTested(inTestingScope));
        int critical = timed("critical api_info count", () -> (int) ApiInfoDao.instance.getMCollection()
                .countDocuments(Filters.gte(ApiInfo.RISK_SCORE, RISK_SCORE_CRITICAL_THRESHOLD)));
        Map<String, List<String>> tagChoices = timed("tag choices", this::buildTagChoices, Map::size);

        CollectionStatsRefresher.saveSummary(Updates.combine(
                Updates.set(ApiCollectionStatsMeta.TOTAL_ALLOWED_FOR_TESTING, totalAllowedForTesting),
                Updates.set(ApiCollectionStatsMeta.TOTAL_TESTED_ENDPOINTS, tested),
                Updates.set(ApiCollectionStatsMeta.TOTAL_CRITICAL_ENDPOINTS, critical),
                Updates.set(ApiCollectionStatsMeta.TAG_CHOICES, tagChoices)));
    }

    /** api_info tested in the last month, of the collections that are in testing scope. */
    private int countTested(Bson inTestingScope) {
        Set<Integer> notTestable = new HashSet<>();
        for (ApiCollectionStats stats : statsDao.findAllNoRbacFilter(
                Filters.nor(inTestingScope), Projections.include(ApiCollectionStats.ID))) {
            notTestable.add(stats.getId());
        }
        return (int) ApiInfoDao.instance.getMCollection().countDocuments(Filters.and(
                Filters.gte(ApiInfo.LAST_TESTED, Context.now() - Constants.ONE_MONTH_TIMESTAMP),
                Filters.nin(ApiInfo.ID_API_COLLECTION_ID, notTestable)));
    }

    private int sumEndpoints(Bson filter) {
        BasicDBObject sum = statsDao.getMCollection().aggregate(Arrays.asList(
                Aggregates.match(filter),
                Aggregates.group(null, Accumulators.sum(TOTAL, field(ApiCollectionStats.ENDPOINTS_COUNT)))),
                BasicDBObject.class).first();
        return sum == null ? 0 : sum.getInt(TOTAL);
    }

    private Map<String, List<String>> buildTagChoices() {
        String keyName = ApiCollectionStats.TAGS_LIST + "." + CollectionTags.KEY_NAME;
        String value = ApiCollectionStats.TAGS_LIST + "." + CollectionTags.VALUE;
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.exists(ApiCollectionStats.TAGS_LIST + ".0")),
                Aggregates.unwind(field(ApiCollectionStats.TAGS_LIST)),
                Aggregates.group(Projections.fields(
                        Projections.computed(CollectionTags.KEY_NAME, field(keyName)),
                        Projections.computed(CollectionTags.VALUE, field(value)))));

        Map<String, TreeSet<String>> valuesByKey = statsDao.getMCollection().aggregate(pipeline, BasicDBObject.class)
                .allowDiskUse(true).into(new ArrayList<>()).stream()
                .map(row -> (BasicDBObject) row.get(ApiCollectionStatsDao.ID_FIELD))
                .filter(tag -> tag.getString(CollectionTags.KEY_NAME) != null && tag.getString(CollectionTags.VALUE) != null)
                .collect(Collectors.groupingBy(tag -> tag.getString(CollectionTags.KEY_NAME), LinkedHashMap::new,
                        Collectors.mapping(tag -> tag.getString(CollectionTags.VALUE), Collectors.toCollection(TreeSet::new))));

        return valuesByKey.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().stream().limit(MAX_TAG_VALUES).collect(Collectors.toList()),
                (a, b) -> a, LinkedHashMap::new));
    }
}
