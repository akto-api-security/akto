package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.EndpointInfoViewDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.EndpointInfoView;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.field;

/**
 * Accounts with endpoint_info_views: one pass over the view instead of single_type_info.
 *
 * The view holds no api groups (EndpointInfoViewCron excludes them), so groups keep using the
 * single_type_info method, restricted to the groups, which is cheap.
 */
class ViewCollectionStatsSource extends AbstractCollectionStatsSource {

    private static final String SUB_TYPES = "subTypes";

    // same host semantics as EndpointInfoViewDao#buildApiStatsForEndpointCount
    private static final Bson ENDPOINT_FILTER = Filters.or(
            Filters.eq(EndpointInfoView.IS_HOST_COLLECTION, false),
            Filters.eq(EndpointInfoView.HAS_HOST_HEADER, true));

    private static final Bson SENSITIVE_FILTER = Filters.and(
            Filters.exists(EndpointInfoView.SENSITIVE_SUB_TYPES, true),
            Filters.ne(EndpointInfoView.SENSITIVE_SUB_TYPES, Collections.emptyList()));

    @Override
    protected Map<Integer, Integer> buildCountMap(List<ApiCollection> collections) {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(ENDPOINT_FILTER),
                Aggregates.group(field(EndpointInfoView.API_COLLECTION_ID), Accumulators.sum(ApiCollectionStatsDao.COUNT, 1)));
        Map<Integer, Integer> counts = EndpointInfoViewDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(row -> row.getInt(ApiCollectionStatsDao.ID_FIELD), row -> row.getInt(ApiCollectionStatsDao.COUNT)));

        List<ApiCollection> groups = collections.stream()
                .filter(c -> ApiCollection.Type.API_GROUP.equals(c.getType())).collect(Collectors.toList());
        if (!groups.isEmpty()) {
            counts.putAll(ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMapOptimized(deactivatedFilter(), groups));
        }
        return counts;
    }

    @Override
    protected Map<Integer, ? extends Iterable<String>> buildSensitiveMap() {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(SENSITIVE_FILTER),
                Aggregates.unwind(field(EndpointInfoView.SENSITIVE_SUB_TYPES)),
                Aggregates.group(field(EndpointInfoView.API_COLLECTION_ID),
                        Accumulators.addToSet(SUB_TYPES, field(EndpointInfoView.SENSITIVE_SUB_TYPES))));
        return EndpointInfoViewDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).allowDiskUse(true).into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(row -> row.getInt(ApiCollectionStatsDao.ID_FIELD),
                        row -> ((List<?>) row.get(SUB_TYPES)).stream().map(String::valueOf).collect(Collectors.toList())));
    }

    @Override
    protected int countSensitiveEndpoints() {
        return (int) EndpointInfoViewDao.instance.getMCollection().countDocuments(SENSITIVE_FILTER);
    }
}
