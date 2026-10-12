package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.EndpointInfoViewDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.dto.EndpointInfoView;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.TimedService;
import com.akto.service.collections.PostureActionItemsResponse.Endpoint;
import com.akto.service.collections.PostureActionItemsResponse.Row;
import com.akto.service.collections.PostureActionItemsResponse.SubCategory;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.*;
import static com.akto.util.Constants.AKTO_DISCOVERED_APIS_COLLECTION;

/**
 * One page of the list behind a home dashboard action item, fetched when its flyout is opened (the
 * counts on the cards come with the snapshot, never the lists).
 *
 * - Api level items read the view where the account has one. Otherwise they read api_info for the
 *   collections the per collection counts say contain a match (indexed on the collection id), with
 *   the very condition that the count of the item sums up (PostureMetric), so list and count agree.
 * - Issue level items group testing_run_issues (a small collection) and fetch the api_info of just
 *   that page's apis.
 * Every page is bounded by {@link #MAX_PAGE_SIZE} and ordered by id, so pages never repeat or drop a row.
 */
public class PostureActionItemsService extends TimedService {

    /** The action items; the metric is the PostureMetric whose sum is the item's count. */
    @Getter
    @RequiredArgsConstructor
    public enum Type {
        HIGH_RISK(PostureMetric.HIGH_RISK), SENSITIVE(PostureMetric.SENSITIVE), THIRD_PARTY(PostureMetric.THIRD_PARTY),
        HIGH_RISK_THIRD_PARTY(PostureMetric.HIGH_RISK_THIRD_PARTY), SENSITIVE_UNAUTHENTICATED(PostureMetric.SENSITIVE_UNAUTHENTICATED),
        UNAUTHENTICATED(PostureMetric.UNAUTHENTICATED), NOT_TESTED(PostureMetric.NOT_TESTED), ONLY_ONCE_TESTED(PostureMetric.ONLY_ONCE_TESTED),
        SHADOW(null),
        MISCONFIGURED(null), VULNERABLE(null), BROKEN_AUTH(null), MULTIPLE_ISSUES(null), VEM(null), MHH(null),
        URLS_BY_ISSUE(null);

        private final String metric;
    }

    static final int MAX_PAGE_SIZE = 100;
    // the "third party apis" item is about apis seen this recently
    static final int THIRD_PARTY_SEEN_WITHIN_SECONDS = 7 * 24 * 60 * 60;
    static final int ENDPOINTS_PER_SUB_CATEGORY = 50;
    static final int QUERY_MAX_TIME_SECONDS = 20;

    private static final String COUNT = "count";
    private static final String ISSUE_NAMES = "issueNames";
    private static final String ROWS = "rows";
    private static final String TOTAL = "total";
    private static final String ENDPOINTS = "endpoints";
    private static final String SUB_CATEGORY_PATH = Constants.ID + "." + TestingIssuesId.TEST_SUB_CATEGORY;

    // api_info's {_id.apiCollectionId, _id.url} index: hands the documents over already in key order
    private static final Bson KEY_ORDER_INDEX = Indexes.ascending(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL);

    private static final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    public PostureActionItemsResponse fetch(Type type, int skip, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = Math.max(skip, 0);
        String label = "page of " + type;
        switch (type) {
            case MISCONFIGURED:
            case VULNERABLE:
            case BROKEN_AUTH:
            case MULTIPLE_ISSUES:
            case VEM:
            case MHH:
                return timed(label, () -> issueLevel(type, offset, pageSize));
            case URLS_BY_ISSUE:
                return timed(label, () -> urlsByIssue(offset, pageSize));
            default:
                return timed(label, () -> PostureSource.viewHasRows() ? fromView(type, offset, pageSize) : fromApiInfo(type, offset, pageSize));
        }
    }

    // ---- api level items ----

    private PostureActionItemsResponse fromView(Type type, int skip, int limit) {
        Object condition = type == Type.SHADOW ? null : ViewPostureSource.conditions().get(type.getMetric());
        if (type != Type.SHADOW && condition == null) {
            // the view cannot tell this one (see ViewPostureSource)
            return PostureActionItemsResponse.ofRows(new ArrayList<>(), PostureCounts.UNAVAILABLE);
        }
        List<Bson> filters = new ArrayList<>(Collections.singletonList(ViewPostureSource.visibleCollections()));
        if (type == Type.SHADOW) {
            ApiCollection shadow = ApiCollectionsDao.instance.findByName(AKTO_DISCOVERED_APIS_COLLECTION);
            if (shadow == null) return PostureActionItemsResponse.ofRows(new ArrayList<>(), 0);
            filters.add(Filters.eq(EndpointInfoView.API_COLLECTION_ID, shadow.getId()));
        } else {
            filters.add(Filters.expr(condition));
        }
        if (type == Type.THIRD_PARTY) filters.add(Filters.gte(EndpointInfoView.LAST_SEEN, Context.now() - THIRD_PARTY_SEEN_WITHIN_SECONDS));
        Bson filter = Filters.and(filters);

        List<Row> rows = EndpointInfoViewDao.instance.getMCollection().find(filter)
                .sort(Sorts.ascending(EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.URL, EndpointInfoView.METHOD))
                .skip(skip).limit(limit).into(new ArrayList<>()).stream().map(PostureActionItemsService::rowOf).collect(Collectors.toList());
        long total = EndpointInfoViewDao.instance.getMCollection().countDocuments(filter);
        return PostureActionItemsResponse.ofRows(rows, total);
    }

    private static Row rowOf(EndpointInfoView view) {
        Set<String> access = view.getApiAccessTypes() == null ? Collections.emptySet()
                : view.getApiAccessTypes().stream().map(Enum::name).collect(Collectors.toSet());
        return new Row(view.getApiCollectionId(), view.getUrl(), view.getMethod().name(), view.getRiskScore(), access,
                null, view.getActualAuthType(), view.getLastSeen(), view.getDiscoveredTimestamp(), view.getLastTested(), null);
    }

    private PostureActionItemsResponse fromApiInfo(Type type, int skip, int limit) {
        List<Integer> collections;
        long total;
        Bson match;
        if (type == Type.SHADOW) {
            ApiCollection shadow = ApiCollectionsDao.instance.findByName(AKTO_DISCOVERED_APIS_COLLECTION);
            if (shadow == null) return PostureActionItemsResponse.ofRows(new ArrayList<>(), 0);
            collections = Collections.singletonList(shadow.getId());
            match = Filters.empty();
            total = ApiInfoDao.instance.count(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, shadow.getId()));
        } else if (isSensitiveItem(type)) {
            List<ApiCollectionStats> withMatches = collectionsWithMatches(type);
            collections = withMatches.stream().map(ApiCollectionStats::getId).collect(Collectors.toList());
            total = withMatches.stream().mapToLong(stats -> countOf(stats, type.getMetric())).sum();
            if (collections.isEmpty()) return PostureActionItemsResponse.ofRows(new ArrayList<>(), 0);
            return PostureActionItemsResponse.ofRows(sensitiveRows(type, collections, skip, limit), total);
        } else {
            // the collections the counts say have a match, and how many matches they add up to
            String metric = type.getMetric();
            List<ApiCollectionStats> withMatches = collectionsWithMatches(type);
            collections = withMatches.stream().map(ApiCollectionStats::getId).collect(Collectors.toList());
            total = withMatches.stream().mapToLong(stats -> countOf(stats, metric)).sum();
            match = PostureMetric.of(metric).filter(Context.now());
        }
        if (collections.isEmpty()) return PostureActionItemsResponse.ofRows(new ArrayList<>(), 0);

        List<Bson> filters = new ArrayList<>(Arrays.asList(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collections), match));
        if (type == Type.THIRD_PARTY) filters.add(Filters.gte(ApiInfo.LAST_SEEN, Context.now() - THIRD_PARTY_SEEN_WITHIN_SECONDS));
        List<Row> rows = ApiInfoDao.instance.findAll(Filters.and(filters), skip, limit, Sorts.ascending(Constants.ID), null)
                .stream().map(info -> rowOf(info, null)).collect(Collectors.toList());
        return PostureActionItemsResponse.ofRows(rows, total);
    }

    /** Sensitive is single_type_info's: counted per collection by the sensitive refresh and the recount, not a condition of api_info. */
    private static boolean isSensitiveItem(Type type) {
        return type == Type.SENSITIVE || type == Type.SENSITIVE_UNAUTHENTICATED;
    }

    /** The visible collections whose count of the item is above 0, with that count. */
    private List<ApiCollectionStats> collectionsWithMatches(Type type) {
        String countPath = countPath(type.getMetric());
        return statsDao.findAll(Filters.and(
                        Filters.in(ApiCollectionStats.TAB, ApiCollectionStats.Tab.HOSTNAME.name(), ApiCollectionStats.Tab.CUSTOM.name()),
                        Filters.nin(ApiCollectionStats.ID, UsageMetricCalculator.getDemosAndDeactivated()),
                        Filters.gt(countPath, 0)),
                Projections.include(ApiCollectionStats.ID, countPath));
    }

    private static String countPath(String metric) {
        return PostureMetric.SENSITIVE.equals(metric) ? ApiCollectionStats.SENSITIVE_APIS_COUNT : ApiCollectionStats.POSTURE + "." + metric;
    }

    private static long countOf(ApiCollectionStats stats, String metric) {
        return PostureMetric.SENSITIVE.equals(metric) ? stats.getSensitiveApisCount() : stats.getPosture().getOrDefault(metric, 0L);
    }

    /**
     * A page of the sensitive items, in key order (collection, then url). The api_info documents of the
     * collections that hold some come in index order, so the pipeline streams: each one is looked up in
     * single_type_info (see SensitiveApiStages) until the page is full, never the whole collection.
     */
    private List<Row> sensitiveRows(Type type, List<Integer> collections, int skip, int limit) {
        List<Bson> filters = new ArrayList<>(Collections.singletonList(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collections)));
        if (type == Type.SENSITIVE_UNAUTHENTICATED) filters.add(PostureMetric.onlyUnauthenticatedFilter());

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(filters)));
        pipeline.add(Aggregates.sort(Sorts.ascending(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL)));
        pipeline.addAll(SensitiveApiStages.keepSensitive());
        pipeline.add(SensitiveApiStages.dropLookup());
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(limit));
        return ApiInfoDao.instance.getMCollection().aggregate(pipeline).hint(KEY_ORDER_INDEX)
                .maxTime(QUERY_MAX_TIME_SECONDS, TimeUnit.SECONDS).into(new ArrayList<>())
                .stream().map(info -> rowOf(info, null)).collect(Collectors.toList());
    }

    private static Row rowOf(ApiInfo info, List<String> issueNames) {
        Set<String> access = info.getApiAccessTypes() == null ? Collections.emptySet()
                : info.getApiAccessTypes().stream().map(Enum::name).collect(Collectors.toSet());
        return new Row(info.getId().getApiCollectionId(), info.getId().getUrl(), info.getId().getMethod().name(), info.getRiskScore(),
                access, info.getAllAuthTypesFound(), null, info.getLastSeen(), info.getDiscoveredTimestamp(), info.getLastTested(), issueNames);
    }

    // ---- issue level items ----

    private PostureActionItemsResponse issueLevel(Type type, int skip, int limit) {
        Set<Integer> excluded = UsageMetricCalculator.getDemosAndDeactivated();
        boolean fromResults = type == Type.MISCONFIGURED;
        String apiKeyPath = fromResults ? TestingRunResult.API_INFO_KEY : Constants.ID + "." + TestingIssuesId.API_KEY_INFO;

        List<BsonField> perApi = new ArrayList<>(Collections.singletonList(Accumulators.sum(COUNT, 1)));
        if (type == Type.MULTIPLE_ISSUES) perApi.add(Accumulators.addToSet(ISSUE_NAMES, field(SUB_CATEGORY_PATH)));
        List<Bson> perApiStages = new ArrayList<>(Arrays.asList(
                Aggregates.match(fromResults ? IssuesSnapshotService.misConfiguredFilter(excluded) : issueFilter(type, excluded)),
                Aggregates.group(field(apiKeyPath), perApi)));
        if (type == Type.MULTIPLE_ISSUES) perApiStages.add(Aggregates.match(Filters.gte(COUNT, IssuesSnapshotService.HIGH_VALUE_MIN_ISSUES)));

        List<Bson> pipeline = new ArrayList<>(perApiStages);
        pipeline.add(Aggregates.facet(
                new Facet(ROWS, Aggregates.sort(Sorts.ascending(Constants.ID)), Aggregates.skip(skip), Aggregates.limit(limit)),
                new Facet(TOTAL, Aggregates.count(COUNT))));
        BasicDBObject result = (fromResults ? TestingRunResultDao.instance.getMCollection() : TestingRunIssuesDao.instance.getMCollection())
                .withDocumentClass(BasicDBObject.class).aggregate(pipeline).maxTime(QUERY_MAX_TIME_SECONDS, TimeUnit.SECONDS).first();

        List<BasicDBObject> keys = result == null ? new ArrayList<>() : (List<BasicDBObject>) result.get(ROWS);
        List<?> totals = result == null ? new ArrayList<>() : (List<?>) result.get(TOTAL);
        long total = totals.isEmpty() ? 0 : ((Number) ((BasicDBObject) totals.get(0)).get(COUNT)).longValue();

        // the apis of just this page
        Map<String, ApiInfo> apiInfoByKey = new HashMap<>();
        for (ApiInfo info : ApiInfoDao.getApiInfosFromList(keys, -1)) apiInfoByKey.put(keyOf(info.getId()), info);
        List<Row> rows = new ArrayList<>();
        for (BasicDBObject key : keys) {
            BasicDBObject id = (BasicDBObject) key.get(Constants.ID);
            ApiInfo info = apiInfoByKey.get(id.getInt(SingleTypeInfo._API_COLLECTION_ID) + " " + id.getString(SingleTypeInfo._METHOD) + " " + id.getString(SingleTypeInfo._URL));
            if (info == null) continue;
            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) key.get(ISSUE_NAMES);
            rows.add(rowOf(info, names));
        }
        return PostureActionItemsResponse.ofRows(rows, total);
    }

    private static String keyOf(ApiInfo.ApiInfoKey key) {
        return key.getApiCollectionId() + " " + key.getMethod().name() + " " + key.getUrl();
    }

    /** The issues an issue level item is about: open, of the collections the user may see, less demo and deactivated ones. */
    private Bson issueFilter(Type type, Set<Integer> excluded) {
        Bson visible = TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(
                Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, excluded));
        List<Bson> filters = new ArrayList<>(Arrays.asList(visible,
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN)));
        if (type != Type.MULTIPLE_ISSUES) filters.add(Filters.ne(IssuesSnapshotService.TEST_ERROR_SOURCE, IssuesSnapshotService.TEST_EDITOR));
        if (type == Type.VULNERABLE) filters.add(Filters.eq(TestingRunIssues.KEY_SEVERITY, GlobalEnums.Severity.CRITICAL.name()));
        if (type == Type.VEM || type == Type.MHH) {
            List<String> tests = IssuesSnapshotService.testIdsOfCategory(type == Type.VEM ? IssuesSnapshotService.VEM_CATEGORY : IssuesSnapshotService.MHH_CATEGORY);
            filters.add(tests.isEmpty() ? Filters.expr(false) : Filters.in(SUB_CATEGORY_PATH, tests));
        }
        return Filters.and(filters);
    }

    /** Test sub categories more than one api has an open issue for, with the first of those apis. */
    private PostureActionItemsResponse urlsByIssue(int skip, int limit) {
        Set<Integer> excluded = UsageMetricCalculator.getDemosAndDeactivated();
        Bson open = Filters.and(TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(
                        Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, excluded)),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN));
        String subCategory = "subCategory";
        String api = "api";

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(open),
                // one entry per (sub category, api), then the apis of each sub category
                Aggregates.group(Projections.fields(Projections.computed(subCategory, field(SUB_CATEGORY_PATH)),
                        Projections.computed(api, field(Constants.ID + "." + TestingIssuesId.API_KEY_INFO)))),
                Aggregates.group(field(Constants.ID + "." + subCategory), Accumulators.push(ENDPOINTS, field(Constants.ID + "." + api)),
                        Accumulators.sum(COUNT, 1)),
                Aggregates.match(Filters.gt(COUNT, IssuesSnapshotService.URLS_PER_PLAYBOOK_THRESHOLD)),
                Aggregates.facet(
                        new Facet(ROWS, Aggregates.sort(Sorts.ascending(Constants.ID)), Aggregates.skip(skip), Aggregates.limit(limit),
                                Aggregates.project(Projections.fields(Projections.include(COUNT),
                                        Projections.computed(ENDPOINTS, slice(field(ENDPOINTS), ENDPOINTS_PER_SUB_CATEGORY))))),
                        new Facet(TOTAL, Aggregates.count(COUNT))));
        BasicDBObject result = TestingRunIssuesDao.instance.getMCollection().withDocumentClass(BasicDBObject.class)
                .aggregate(pipeline).maxTime(QUERY_MAX_TIME_SECONDS, TimeUnit.SECONDS).first();

        List<SubCategory> subCategories = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<BasicDBObject> rows = result == null ? new ArrayList<>() : (List<BasicDBObject>) result.get(ROWS);
        for (BasicDBObject row : rows) {
            @SuppressWarnings("unchecked")
            List<BasicDBObject> endpoints = (List<BasicDBObject>) row.get(ENDPOINTS);
            subCategories.add(new SubCategory(row.getString(Constants.ID), ((Number) row.get(COUNT)).longValue(), endpoints.stream()
                    .map(e -> new Endpoint(e.getInt(ApiInfo.ApiInfoKey.API_COLLECTION_ID), e.getString(ApiInfo.ApiInfoKey.URL), e.getString(ApiInfo.ApiInfoKey.METHOD)))
                    .collect(Collectors.toList())));
        }
        List<?> totals = result == null ? new ArrayList<>() : (List<?>) result.get(TOTAL);
        long total = totals.isEmpty() ? 0 : ((Number) ((BasicDBObject) totals.get(0)).get(COUNT)).longValue();
        return new PostureActionItemsResponse(new ArrayList<>(), subCategories, total);
    }
}
