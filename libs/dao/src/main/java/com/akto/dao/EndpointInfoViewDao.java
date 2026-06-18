package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiStats;
import com.akto.dto.EndpointInfoView;
import com.akto.dto.rbac.UsersCollectionsList;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class EndpointInfoViewDao extends AccountsContextDao<EndpointInfoView> {

    public static final EndpointInfoViewDao instance = new EndpointInfoViewDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.DISCOVERED_TIMESTAMP}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.ascending(EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.URL, EndpointInfoView.METHOD),
                new IndexOptions().name("merge_key_unique").unique(true));
    }

    @Override
    public String getCollName() {
        return "endpoint_info_views";
    }

    @Override
    public Class<EndpointInfoView> getClassT() {
        return EndpointInfoView.class;
    }

    public ApiStats buildApiStats(Bson baseFilter, int startTimestamp, int endTimestamp) {
        Bson filter = appendRbacFilter(appendTimeFilter(baseFilter, endTimestamp));
        return aggregateWithFacet(filter);
    }

    public ApiStats buildApiStatsForEndpointCount(Bson baseFilter, int endTimestamp) {
        Bson hostFilter = Filters.or(
                Filters.eq(EndpointInfoView.IS_HOST_COLLECTION, false),
                Filters.eq(EndpointInfoView.HAS_HOST_HEADER, true));
        Bson filter = appendRbacFilter(appendTimeFilter(
                Filters.and(baseFilter, hostFilter), endTimestamp));
        return aggregateWithFacet(filter);
    }

    private Bson appendTimeFilter(Bson baseFilter, int endTimestamp) {
        Bson timeFilter = Filters.lte(EndpointInfoView.DISCOVERED_TIMESTAMP, endTimestamp);
        return Filters.and(baseFilter, timeFilter);
    }

    private Bson appendRbacFilter(Bson filter) {
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(
                Context.userId.get(), Context.accountId.get());
            if (collectionIds != null) {
                return Filters.and(filter, Filters.in(EndpointInfoView.API_COLLECTION_ID, collectionIds));
            }
        } catch (Exception ignored) {
        }
        return filter;
    }

    private static final List<String> STANDARD_AUTH_TYPES = Arrays.asList(
            "UNAUTHENTICATED", "BASIC", "AUTHORIZATION_HEADER", "JWT",
            "API_TOKEN", "BEARER", "API_KEY", "MTLS", "SESSION_TOKEN");

    private ApiStats aggregateWithFacet(Bson filter) {
        int lookBack = Context.now() - 30 * 24 * 60 * 60;
        Bson inScopeFilter = Filters.eq(EndpointInfoView.IS_OUT_OF_TESTING_SCOPE, false);
        Bson testedFilter = Filters.and(inScopeFilter, Filters.gt(EndpointInfoView.LAST_TESTED, lookBack));

        // Pick auth type: UNAUTHENTICATED always wins, then first standard type, else CUSTOM
        Document stdFilter = new Document("$filter", new Document()
                .append("input", "$$auth")
                .append("cond", new Document("$in", Arrays.asList("$$this", STANDARD_AUTH_TYPES))));
        Document authTypeExpr = new Document("$let", new Document()
                .append("vars", new Document()
                        .append("auth", "$" + EndpointInfoView.ACTUAL_AUTH_TYPE))
                .append("in", new Document("$cond", Arrays.asList(
                        new Document("$or", Arrays.asList(
                                new Document("$eq", Arrays.asList("$$auth", null)),
                                new Document("$eq", Arrays.asList(new Document("$size", "$$auth"), 0)))),
                        null,
                        new Document("$cond", Arrays.asList(
                                new Document("$in", Arrays.asList("UNAUTHENTICATED", "$$auth")),
                                "UNAUTHENTICATED",
                                new Document("$let", new Document()
                                        .append("vars", new Document("std", stdFilter))
                                        .append("in", new Document("$cond", Arrays.asList(
                                                new Document("$gt", Arrays.asList(new Document("$size", "$$std"), 0)),
                                                new Document("$arrayElemAt", Arrays.asList("$$std", 0)),
                                                "CUSTOM"))))))))));

        // Priority-pick from actualAccessType array
        Document accessTypeExpr = new Document("$let", new Document()
                .append("vars", new Document("acc", "$" + EndpointInfoView.ACTUAL_ACCESS_TYPE))
                .append("in", new Document("$cond", Arrays.asList(
                        new Document("$or", Arrays.asList(
                                new Document("$eq", Arrays.asList("$$acc", null)),
                                new Document("$eq", Arrays.asList(new Document("$size", "$$acc"), 0)))),
                        null,
                        new Document("$cond", Arrays.asList(
                                new Document("$in", Arrays.asList("PUBLIC", "$$acc")), "PUBLIC",
                                new Document("$cond", Arrays.asList(
                                        new Document("$in", Arrays.asList("PARTNER", "$$acc")), "PARTNER",
                                        new Document("$cond", Arrays.asList(
                                                new Document("$in", Arrays.asList("THIRD_PARTY", "$$acc")), "THIRD_PARTY",
                                                new Document("$arrayElemAt", Arrays.asList("$$acc", 0))))))))))));

        List<Bson> pipeline = Arrays.asList(
            Aggregates.match(filter),
            Aggregates.facet(
                new Facet("totals", Aggregates.group(null,
                    Accumulators.sum("count", 1),
                    Accumulators.sum("totalRisk", "$" + EndpointInfoView.RISK_SCORE))),
                new Facet("inScope", Aggregates.match(inScopeFilter),
                    Aggregates.group(null, Accumulators.sum("count", 1))),
                new Facet("tested", Aggregates.match(testedFilter),
                    Aggregates.group(null, Accumulators.sum("count", 1))),
                new Facet("byAuthType",
                    Aggregates.addFields(new Field<>("_authType", authTypeExpr)),
                    Aggregates.group("$_authType", Accumulators.sum("count", 1))),
                new Facet("byAccessType",
                    Aggregates.addFields(new Field<>("_accessType", accessTypeExpr)),
                    Aggregates.group("$_accessType", Accumulators.sum("count", 1))),
                new Facet("byApiType",
                    Aggregates.group("$" + EndpointInfoView.API_TYPE, Accumulators.sum("count", 1))),
                new Facet("byRiskBucket",
                    Aggregates.group(new Document("$round", "$" + EndpointInfoView.RISK_SCORE),
                        Accumulators.sum("count", 1))),
                new Facet("bySeverity",
                    Aggregates.match(Filters.ne(EndpointInfoView.SEVERITY, null)),
                    Aggregates.group("$" + EndpointInfoView.SEVERITY, Accumulators.sum("count", 1)))
            )
        );

        Document result = instance.getMCollection()
            .aggregate(pipeline, Document.class).allowDiskUse(true).first();
        return result != null ? parseResult(result) : new ApiStats(0);
    }

    private ApiStats parseResult(Document result) {
        ApiStats stats = new ApiStats(0);
        parseTotals(result, stats);
        parseStringMap(result, "byAuthType", stats.getAuthTypeMap());
        parseAccessTypeMap(result, stats);
        parseApiTypeMap(result, stats);
        parseIntMap(result, "byRiskBucket", stats.getRiskScoreMap());
        parseStringMap(result, "bySeverity", stats.getCriticalMap());
        return stats;
    }

    private void parseTotals(Document result, ApiStats stats) {
        Document totals = firstFromFacet(result, "totals");
        Document inScope = firstFromFacet(result, "inScope");
        Document tested = firstFromFacet(result, "tested");
        stats.setTotalAPIs(totals != null ? totals.getInteger("count", 0) : 0);
        stats.setTotalRiskScore(totals != null ? ((Number) totals.get("totalRisk")).floatValue() : 0);
        stats.setTotalInScopeForTestingApis(inScope != null ? inScope.getInteger("count", 0) : 0);
        stats.setApisTestedInLookBackPeriod(tested != null ? tested.getInteger("count", 0) : 0);
    }

    @SuppressWarnings("unchecked")
    private Document firstFromFacet(Document result, String facetName) {
        List<Document> list = (List<Document>) result.get(facetName);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    @SuppressWarnings("unchecked")
    private void parseStringMap(Document result, String facetName, Map<String, Integer> map) {
        List<Document> list = (List<Document>) result.get(facetName);
        if (list == null) return;
        for (Document doc : list) {
            String key = doc.getString("_id");
            if (key != null) map.put(key, doc.getInteger("count", 0));
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAccessTypeMap(Document result, ApiStats stats) {
        List<Document> list = (List<Document>) result.get("byAccessType");
        if (list == null) return;
        for (Document doc : list) {
            String key = doc.getString("_id");
            if (key == null) continue;
            try {
                stats.getAccessTypeMap().put(ApiInfo.ApiAccessType.valueOf(key), doc.getInteger("count", 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseApiTypeMap(Document result, ApiStats stats) {
        List<Document> list = (List<Document>) result.get("byApiType");
        if (list == null) return;
        for (Document doc : list) {
            String key = doc.getString("_id");
            if (key == null) continue;
            try {
                stats.getApiTypeMap().put(ApiInfo.ApiType.valueOf(key), doc.getInteger("count", 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseIntMap(Document result, String facetName, Map<Integer, Integer> map) {
        List<Document> list = (List<Document>) result.get(facetName);
        if (list == null) return;
        for (Document doc : list) {
            Object id = doc.get("_id");
            if (id != null) map.put(((Number) id).intValue(), doc.getInteger("count", 0));
        }
    }

    public Map<String, Integer> countMissingFields(int endTimestamp) {
        Bson filter = appendRbacFilter(appendTimeFilter(Filters.empty(), endTimestamp));
        long authMissing = instance.getMCollection().countDocuments(
                Filters.and(filter, Filters.eq(EndpointInfoView.ACTUAL_AUTH_TYPE, Collections.emptyList())));
        long accessMissing = instance.getMCollection().countDocuments(
                Filters.and(filter, Filters.eq(EndpointInfoView.ACTUAL_ACCESS_TYPE, Collections.emptyList())));
        long apiTypeMissing = instance.getMCollection().countDocuments(
                Filters.and(filter, Filters.eq(EndpointInfoView.API_TYPE, null)));

        Map<String, Integer> counts = new HashMap<>();
        counts.put("authNotCalculated", (int) authMissing);
        counts.put("accessTypeNotCalculated", (int) accessMissing);
        counts.put("apiTypeMissing", (int) apiTypeMissing);
        return counts;
    }
}
