package com.akto.service.collections;

import com.akto.dao.EndpointInfoViewDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.ApiStats;
import com.akto.dto.EndpointInfoView;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.service.TimedService;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * The api numbers of accounts with endpoint_info_views: the view already holds each endpoint's actual
 * auth / access type, severity and sensitive types, so nothing is joined or derived per request.
 * The charts reuse EndpointInfoViewDao.buildApiStats; the action item counts are one more pass over the
 * view.
 *
 * The view carries no "times tested" and has no api_info to compare with, so those two numbers are
 * reported as unavailable / zero, as the old dashboard calls did for view accounts.
 */
class ViewPostureSource extends TimedService implements PostureSource {

    private static final EndpointInfoViewDao viewDao = EndpointInfoViewDao.instance;

    @Override
    public PostureData load(int startTimestamp, int endTimestamp) {
        ApiStats end = timed("view stats at the end of the range", () -> viewDao.buildApiStats(Filters.empty(), startTimestamp, endTimestamp));
        ApiStats start = timed("view stats at the start of the range", () -> viewDao.buildApiStats(Filters.empty(), 0, startTimestamp));
        PostureCounts counts = timed("view action item counts", () -> actionItemCounts(endTimestamp));
        return new PostureData(start, end, false, counts, 0, false, 0, 0, false);
    }

    /**
     * The conditions of the metrics the view can answer, on the view's own (already derived) fields.
     * Missing here: ONLY_ONCE_TESTED, the view carries no "times tested".
     */
    static Map<String, Object> conditions() {
        Object auth = ifNull(field(EndpointInfoView.ACTUAL_AUTH_TYPE), Collections.emptyList());
        Object access = ifNull(field(EndpointInfoView.API_ACCESS_TYPES), Collections.emptyList());
        Object risk = ifNull(field(EndpointInfoView.RISK_SCORE), 0);
        Object unauthenticated = in(AuthType.UNAUTHENTICATED, auth);
        Object sensitive = gt(size(ifNull(field(EndpointInfoView.SENSITIVE_SUB_TYPES), Collections.emptyList())), 0);

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put(PostureMetric.HIGH_RISK, gt(risk, 3));
        conditions.put(PostureMetric.HIGH_RISK_THIRD_PARTY, and(gte(risk, 4), in(ApiAccessType.THIRD_PARTY.name(), access)));
        conditions.put(PostureMetric.THIRD_PARTY, in(ApiAccessType.THIRD_PARTY.name(), access));
        conditions.put(PostureMetric.UNAUTHENTICATED, unauthenticated);
        conditions.put(PostureMetric.SENSITIVE, sensitive);
        conditions.put(PostureMetric.SENSITIVE_UNAUTHENTICATED, and(sensitive, unauthenticated));
        conditions.put(PostureMetric.NOT_TESTED, not(gt(ifNull(field(EndpointInfoView.LAST_TESTED), 0), 0)));
        conditions.put(PostureMetric.AUTH_NOT_CALCULATED, eq(size(auth), 0));
        conditions.put(PostureMetric.ACCESS_NOT_CALCULATED, eq(size(ifNull(field(EndpointInfoView.ACTUAL_ACCESS_TYPE), Collections.emptyList())), 0));
        conditions.put(PostureMetric.API_TYPE_MISSING, eq(ifNull(field(EndpointInfoView.API_TYPE), null), null));
        return conditions;
    }

    private PostureCounts actionItemCounts(int endTimestamp) {
        List<BsonField> sums = new ArrayList<>();
        conditions().forEach((metric, condition) -> sums.add(count(metric, condition)));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(visibleUpTo(endTimestamp)),
                Aggregates.group(null, sums));
        BasicDBObject row = viewDao.getMCollection().withDocumentClass(BasicDBObject.class).aggregate(pipeline).allowDiskUse(true).first();
        // how many times an api was tested is not in the view
        return PostureCounts.of(row).with(PostureMetric.ONLY_ONCE_TESTED, PostureCounts.UNAVAILABLE);
    }

    private static BsonField count(String field, Object condition) {
        return Accumulators.sum(field, cond(condition, 1, 0));
    }

    /** The endpoints discovered by the end of the range, of the collections the user may see. */
    private Bson visibleUpTo(int endTimestamp) {
        return Filters.and(Filters.lte(EndpointInfoView.DISCOVERED_TIMESTAMP, endTimestamp), visibleCollections());
    }

    /** The view's endpoints of the collections the user may see (the view is built without demo and deactivated ones). */
    static Bson visibleCollections() {
        Bson filter = Filters.empty();
        try {
            List<Integer> visible = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if (visible != null) return Filters.and(filter, Filters.in(EndpointInfoView.API_COLLECTION_ID, visible));
        } catch (Exception e) {
            // as EndpointInfoViewDao does: a failure to resolve the user's collections is unscoped
        }
        return filter;
    }
}
