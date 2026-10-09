package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.billing.UningestedApiOverageDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.service.TimedService;
import com.akto.service.collections.CollectionsPageResponse.Details;
import com.akto.service.collections.CollectionsPageResponse.Meta;
import com.akto.service.collections.CollectionsPageResponse.Page;
import com.akto.service.collections.CollectionsPageResponse.Summary;
import com.akto.service.collections.CollectionsPageResponse.TabCounts;
import com.akto.service.collections.CollectionsPageResponse.UntrackedApi;
import com.akto.service.collections.CollectionsPageResponse.UntrackedRow;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.IconUtils;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.field;

/**
 * Serves the API collections table: one page = one indexed find on api_collection_stats, then the
 * documents and per row numbers of just that page's collections.
 */
public class CollectionsPageService extends TimedService {

    public static final String TAB_UNTRACKED = ApiCollectionStats.Tab.UNTRACKED.name();

    private static final int MAX_UNTRACKED_URLS_PER_COLLECTION = 200;
    private static final String TOTAL = "total";

    private static final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    private static final int DETAILS_TIMEOUT_SECONDS = 8;
    // the queries on other collections (issues, coverage) run beside the page's own work; a few requests at a time
    private static final ExecutorService DETAILS_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "collections-page-details");
        thread.setDaemon(true);
        return thread;
    });

    private final int detailsTimeoutSeconds;

    public CollectionsPageService() {
        this(DETAILS_TIMEOUT_SECONDS);
    }

    CollectionsPageService(int detailsTimeoutSeconds) {
        this.detailsTimeoutSeconds = detailsTimeoutSeconds;
    }

    private static final Bson UNINGESTED_FILTER = Filters.ne(UningestedApiOverage.METHOD, URLMethods.Method.OPTIONS);

    public Page fetchPage(CollectionsPageRequest request) {
        CollectionStatsRefresher.Freshness freshness = CollectionStatsRefresher.ensureFresh(request.isForce());
        Page page = new Page();
        page.setStatsUpdatedAt(freshness.getRefreshedAt());
        page.setStatsPending(freshness.isPending());

        if (TAB_UNTRACKED.equalsIgnoreCase(request.getTab())) {
            fillUntracked(request, page);
            return page;
        }

        Bson filter = CollectionsPageQueryBuilder.buildFilter(request);
        List<ApiCollectionStats> rows = timed("find page of stats rows", () -> statsDao.findAll(filter,
                request.getSkip(), request.getLimit(),
                CollectionsPageQueryBuilder.buildSort(request.getSortKey(), request.getSortOrder()),
                Projections.include(ApiCollectionStats.ID, ApiCollectionStats.ENDPOINTS_COUNT, ApiCollectionStats.RISK_SCORE,
                        ApiCollectionStats.LAST_SEEN, ApiCollectionStats.SENSITIVE_SUB_TYPES)), List::size);
        page.setTotal(timed("count matching stats rows", () -> statsDao.count(filter)));

        List<Integer> ids = rows.stream().map(ApiCollectionStats::getId).collect(Collectors.toList());
        Map<Integer, Integer> endpointCounts = rows.stream()
                .collect(Collectors.toMap(ApiCollectionStats::getId, ApiCollectionStats::getEndpointsCount));
        page.setRiskScoreMap(rows.stream().collect(Collectors.toMap(ApiCollectionStats::getId, ApiCollectionStats::getRiskScore)));
        page.setLastSeenMap(rows.stream().collect(Collectors.toMap(ApiCollectionStats::getId, ApiCollectionStats::getLastSeen)));
        page.setSensitiveInfoMap(rows.stream().collect(Collectors.toMap(ApiCollectionStats::getId,
                row -> row.getSensitiveSubTypes() == null ? new ArrayList<String>() : row.getSensitiveSubTypes())));

        // open issues are queried while the collections' documents load, and given a time limit of their own
        Future<Map<Integer, Map<String, Integer>>> issues = submitLimited(
                "issues of the page's collections", () -> loadSeverityInfo(ids), Map::size);
        page.setApiCollections(timed("load the page's collections", () -> loadCollectionsInOrder(ids, endpointCounts), List::size));
        Map<Integer, Map<String, Integer>> severityInfo = awaitOrNull(issues, "issues");
        page.setIssuesUnavailable(severityInfo == null);
        if (severityInfo != null) page.setSeverityInfoMap(severityInfo);
        return page;
    }

    /**
     * Coverage of these collections (a page's ids), time limited: when the query fails or runs out
     * of time it is reported unavailable, and the page that already showed is unaffected.
     */
    public Details fetchDetails(List<Integer> collectionIds) {
        List<Integer> ids = collectionIds == null ? new ArrayList<>()
                : collectionIds.stream().distinct().limit(CollectionsPageRequest.MAX_LIMIT).collect(Collectors.toList());
        return new Details(awaitOrNull(submitLimited("coverage of the page's collections", () -> loadCoverage(ids), Map::size), "coverage"));
    }

    /** Runs a query on the shared executor under the caller's account and role scoping. */
    private <T> Future<T> submitLimited(String label, Supplier<T> query, ToIntFunction<T> rowCount) {
        int accountId = Context.accountId.get();
        Integer userId = Context.userId.get();
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        return DETAILS_EXECUTOR.submit(Context.withContext(accountId, userId, contextSource, () -> timed(label, query, rowCount)));
    }

    private <T> T awaitOrNull(Future<T> future, String what) {
        try {
            return future.get(detailsTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            logger.errorAndAddToDb("collections page " + what + " failed or timed out after " + detailsTimeoutSeconds + "s: " + e);
            return null;
        }
    }

    protected Map<Integer, Integer> loadCoverage(List<Integer> ids) {
        return ApiInfoDao.instance.getCoverageCountForCollections(ids, detailsTimeoutSeconds);
    }

    /** Header numbers: tab counts, summary card, tag filter choices. */
    public Meta fetchMeta() {
        CollectionStatsRefresher.Freshness freshness = CollectionStatsRefresher.ensureFresh(false);
        TabCounts tabCounts = fetchTabCounts(new CollectionsPageRequest(0, 0, null, -1, CollectionsPageQueryBuilder.TAB_ALL, null, null, null, false));
        ApiCollectionStatsMeta summary = CollectionStatsRefresher.loadSummary();
        boolean hasUsageEndpoints = timed("has usage endpoints", () -> !statsDao.findAll(Filters.and(
                        Filters.gt(ApiCollectionStats.ENDPOINTS_COUNT, 0),
                        Filters.nin(ApiCollectionStats.ID, UsageMetricCalculator.getDemos())),
                0, 1, null, Projections.include(ApiCollectionStats.ID)).isEmpty());

        Map<String, List<String>> tagChoices = summary.getTagChoices() == null ? new HashMap<>() : summary.getTagChoices();
        return new Meta(tabCounts, new Summary(summary), tagChoices, hasUsageEndpoints,
                freshness.getRefreshedAt(), freshness.isPending());
    }

    /**
     * How many collections each tab holds under the request's search and filters (its own tab and
     * paging are ignored), so the tab badges follow what the table is narrowed to. The untracked
     * tab has no such filters and always counts all of its collections.
     */
    public TabCounts fetchTabCounts(CollectionsPageRequest request) {
        return timed("tab counts", () -> new TabCounts(countByTab(request), countUntracked(visibleCollectionIds())));
    }

    private Map<ApiCollectionStats.Tab, Long> countByTab(CollectionsPageRequest request) {
        Bson filter = CollectionsPageQueryBuilder.buildFilter(new CollectionsPageRequest(0, 0, null, -1,
                CollectionsPageQueryBuilder.TAB_ALL, request.getQueryValue(), request.getFilters(), request.getTagFilters(), false));
        return statsDao.aggregateWithRbac(Arrays.asList(
                        Aggregates.match(filter),
                        Aggregates.group(field(ApiCollectionStats.TAB), Accumulators.sum(ApiCollectionStatsDao.COUNT, 1))))
                .into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(
                        row -> ApiCollectionStats.Tab.valueOf(row.getString(ApiCollectionStatsDao.ID_FIELD)),
                        row -> row.getLong(ApiCollectionStatsDao.COUNT)));
    }

    private List<ApiCollection> loadCollectionsInOrder(List<Integer> ids, Map<Integer, Integer> endpointCounts) {
        Map<Integer, ApiCollection> byId = ids.isEmpty() ? new HashMap<>() : ApiCollectionsDao.instance
                .findAll(Filters.in(ApiCollection.ID, ids), 0, ids.size(), null, ApiCollectionsDao.LIST_PROJECTION).stream()
                .collect(Collectors.toMap(ApiCollection::getId, c -> c));
        // a stats row can outlive its collection until the next attr sync
        List<ApiCollection> ordered = ids.stream().map(byId::get).filter(c -> c != null).collect(Collectors.toList());
        for (ApiCollection c : ordered) {
            c.setUrlsCount(endpointCounts.getOrDefault(c.getId(), 0));
            if (c.getTagsList() != null) {
                for (CollectionTags tag : c.getTagsList()) {
                    tag.setLastUpdatedTs(0);
                    tag.setSource(null);
                }
            }
        }
        IconUtils.processIconsForCollections(ordered);
        return ordered;
    }

    protected Map<Integer, Map<String, Integer>> loadSeverityInfo(List<Integer> ids) {
        if (ids.isEmpty()) return new HashMap<>();
        Map<Integer, Map<String, Integer>> all = TestingRunIssuesDao.instance
                .getSeveritiesMapForCollections(Filters.in(SingleTypeInfo._COLLECTION_IDS, ids));
        // unwinding an issue's collectionIds also yields the other collections it belongs to
        return all.entrySet().stream().filter(e -> ids.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The collections the user may see, or null when that is all of them. Only the untracked tab
     * needs it: its dao (UningestedApiOverageDao) has no role scoping of its own.
     */
    private List<Integer> visibleCollectionIds() {
        try {
            List<Integer> ids = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            return ids == null || ids.size() >= statsDao.estimatedDocumentCount() ? null : ids;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- untracked tab: collections that have apis seen but not ingested ----

    private Bson uningestedFilter(List<Integer> accessibleIds) {
        return accessibleIds == null ? UNINGESTED_FILTER
                : Filters.and(UNINGESTED_FILTER, Filters.in(UningestedApiOverage.API_COLLECTION_ID, accessibleIds));
    }

    private long countUntracked(List<Integer> accessibleIds) {
        BasicDBObject count = UningestedApiOverageDao.instance.getMCollection().aggregate(Arrays.asList(
                Aggregates.match(uningestedFilter(accessibleIds)),
                Aggregates.group(field(UningestedApiOverage.API_COLLECTION_ID)),
                Aggregates.count(TOTAL)), BasicDBObject.class).first();
        return count == null ? 0 : count.getLong(TOTAL);
    }

    private void fillUntracked(CollectionsPageRequest request, Page page) {
        List<Integer> accessibleIds = visibleCollectionIds();
        page.setTotal(timed("count untracked collections", () -> countUntracked(accessibleIds)));

        List<BasicDBObject> groups = timed("page of untracked collections", () -> UningestedApiOverageDao.instance.getMCollection().aggregate(Arrays.asList(
                Aggregates.match(uningestedFilter(accessibleIds)),
                Aggregates.group(field(UningestedApiOverage.API_COLLECTION_ID), Accumulators.sum(ApiCollectionStatsDao.COUNT, 1)),
                Aggregates.sort(Sorts.orderBy(Sorts.descending(ApiCollectionStatsDao.COUNT), Sorts.ascending(ApiCollectionStatsDao.ID_FIELD))),
                Aggregates.skip(request.getSkip()), Aggregates.limit(request.getLimit())), BasicDBObject.class)
                .into(new ArrayList<>()), List::size);

        List<Integer> ids = groups.stream().map(g -> g.getInt(ApiCollectionStatsDao.ID_FIELD)).collect(Collectors.toList());
        Map<Integer, ApiCollectionStats> collectionById = statsDao.findAll(Filters.in(ApiCollectionStats.ID, ids), 0, ids.size(), null,
                Projections.include(ApiCollectionStats.ID, ApiCollectionStats.DISPLAY_NAME, ApiCollectionStats.START_TS)).stream()
                .collect(Collectors.toMap(ApiCollectionStats::getId, c -> c));

        page.setUntrackedRows(timed("apis of the untracked collections", () -> groups.stream()
                .filter(g -> collectionById.containsKey(g.getInt(ApiCollectionStatsDao.ID_FIELD)))
                .map(g -> {
                    int id = g.getInt(ApiCollectionStatsDao.ID_FIELD);
                    ApiCollectionStats collection = collectionById.get(id);
                    return new UntrackedRow(id, collection.getDisplayName(), collection.getStartTs(),
                            g.getInt(ApiCollectionStatsDao.COUNT), uningestedApis(id));
                }).collect(Collectors.toList()), List::size));
    }

    private List<UntrackedApi> uningestedApis(int apiCollectionId) {
        return UningestedApiOverageDao.instance.getMCollection()
                .find(Filters.and(UNINGESTED_FILTER, Filters.eq(UningestedApiOverage.API_COLLECTION_ID, apiCollectionId)))
                .limit(MAX_UNTRACKED_URLS_PER_COLLECTION)
                .into(new ArrayList<>()).stream().map(UntrackedApi::new).collect(Collectors.toList());
    }
}
