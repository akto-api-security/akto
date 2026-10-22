package com.akto.service.collections;

import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.EndpointInfoViewDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.EndpointInfoView;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.TimedService;
import com.akto.service.collections.McpPostureResponses.Alert;
import com.akto.service.collections.McpPostureResponses.Counts;
import com.akto.service.collections.McpPostureResponses.Data;
import com.akto.service.collections.McpPostureResponses.Point;
import com.akto.service.collections.McpPostureResponses.Series;
import com.akto.service.collections.McpPostureResponses.TopApplication;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.*;

/**
 * The MCP and guardrail widgets of the home dashboard. The collections come from the ids of
 * api_collection_stats (tags are indexed there, and the dao scopes them to the user), never from
 * the collection documents; the counts are one pass each over just those collections' apis (indexed
 * on the collection id) and one group over the audit info; the lists are paged.
 */
public class McpPostureService extends TimedService {

    /** The collection tags the widgets are about. */
    @Getter
    @RequiredArgsConstructor
    public enum Tag {
        MCP(Constants.AKTO_MCP_SERVER_TAG), GUARDRAIL(Constants.AKTO_GUARD_RAIL_TAG);

        private final String keyName;
    }

    public enum Section {
        RECENT_ALERTS, TOP_APPLICATIONS
    }

    static final int NEW_APIS_WINDOW_SECONDS = 7 * 24 * 60 * 60;
    static final int CRITICAL_RISK_SCORE = 4;
    static final int MAX_PAGE_SIZE = 100;
    // the series gets coarser with the range, like the chart that draws it
    static final int HOUR_SECONDS = 3600;
    static final int DAY_SECONDS = 24 * HOUR_SECONDS;

    private static final String COUNT = "count";
    private static final String HITS = "hits";
    private static final String TOTAL = "total";
    private static final String ROWS = "rows";
    private static final String NEW = "newApis";
    private static final String THIRD_PARTY = "thirdParty";
    private static final String CRITICAL = "critical";
    private static final String HIT_COUNT_COLLECTION = "apiCollectionId";
    private static final String HIT_COUNT_TS = "ts";
    private static final String HIT_COUNT_COUNT = "count";

    private static final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    public Counts counts() {
        List<ApiCollectionStats> mcp = taggedCollections(Tag.MCP);
        List<ApiCollectionStats> guardrail = taggedCollections(Tag.GUARDRAIL);

        BasicDBObject apis = timed("api counts of the mcp collections", () -> countApis(ids(mcp)));
        Map<String, Long> auditCounts = timed("mcp audit info by type", this::countAuditInfoByType);
        long guardrailApis = guardrail.stream().mapToLong(ApiCollectionStats::getEndpointsCount).sum();

        return new Counts(number(apis, TOTAL), number(apis, NEW), number(apis, THIRD_PARTY), number(apis, CRITICAL),
                auditCounts.getOrDefault(Constants.AKTO_MCP_TOOL, 0L), auditCounts.getOrDefault(Constants.AKTO_MCP_PROMPT, 0L),
                auditCounts.getOrDefault(Constants.AKTO_MCP_RESOURCE, 0L), auditCounts.getOrDefault(Constants.AKTO_MCP_SERVER, 0L),
                guardrailApis);
    }

    public Data data(Section section, int skip, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        int offset = Math.max(skip, 0);
        return section == Section.RECENT_ALERTS ? recentAlerts(offset, pageSize) : topApplications(offset, pageSize);
    }

    /** Api calls of the tagged collections between two timestamps, summed per time bucket inside mongo. */
    public Series apiCallSeries(Tag tag, int startTimestamp, int endTimestamp) {
        List<Integer> ids = ids(taggedCollections(tag));
        if (ids.isEmpty()) return new Series(new ArrayList<>());

        int bucketMinutes = bucketMinutes(endTimestamp - startTimestamp);
        // the bucket a minute falls in, named by its first minute. Buckets count from the start of the range,
        // as the chart's intervals do, so none straddles two of them and the first is not cut short
        int startMinute = startTimestamp / 60;
        Object bucketStart = add(startMinute, multiply(floor(divide(add(field(HIT_COUNT_TS), -startMinute), bucketMinutes)), bucketMinutes));
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(Filters.in(HIT_COUNT_COLLECTION, ids),
                        Filters.gte(HIT_COUNT_TS, startMinute), Filters.lte(HIT_COUNT_TS, endTimestamp / 60))),
                Aggregates.group(bucketStart, Accumulators.sum(COUNT, field(HIT_COUNT_COUNT))),
                Aggregates.sort(Sorts.ascending(Constants.ID)));
        List<Point> points = timed("api calls of the tagged collections", () -> ApiHitCountInfoDao.instance.getMCollection()
                .withDocumentClass(BasicDBObject.class).aggregate(pipeline).allowDiskUse(true).into(new ArrayList<>()).stream()
                .map(row -> new Point(((Number) row.get(Constants.ID)).longValue(), ((Number) row.get(COUNT)).longValue()))
                .collect(Collectors.toList()), List::size);
        return new Series(points);
    }

    /** The chart's intervals (HomeDashboard generateTimeSeriesWithGaps): keep the two in step. */
    static int bucketMinutes(int rangeSeconds) {
        if (rangeSeconds <= HOUR_SECONDS) return 1;
        if (rangeSeconds <= DAY_SECONDS) return 5;
        if (rangeSeconds <= 7 * DAY_SECONDS) return 60;
        if (rangeSeconds <= 30 * DAY_SECONDS) return 6 * 60;
        if (rangeSeconds <= 90 * DAY_SECONDS) return 24 * 60;
        return 7 * 24 * 60;
    }

    /** The user's collections carrying the tag, less demo and deactivated ones. */
    private List<ApiCollectionStats> taggedCollections(Tag tag) {
        Set<Integer> excluded = UsageMetricCalculator.getDemosAndDeactivated();
        return statsDao.findAll(Filters.and(
                        Filters.elemMatch(ApiCollectionStats.TAGS_LIST, Filters.eq(CollectionTags.KEY_NAME, tag.getKeyName())),
                        Filters.nin(ApiCollectionStats.ID, excluded)),
                Projections.include(ApiCollectionStats.ID, ApiCollectionStats.NAME, ApiCollectionStats.ENDPOINTS_COUNT));
    }

    private static List<Integer> ids(List<ApiCollectionStats> collections) {
        return collections.stream().map(ApiCollectionStats::getId).collect(Collectors.toList());
    }

    /** One pass over the apis of these collections (from the view where the account has it): total, new, third party, critical. */
    private BasicDBObject countApis(List<Integer> collectionIds) {
        if (collectionIds.isEmpty()) return new BasicDBObject();
        boolean fromView = PostureSource.viewHasRows();
        String collectionIdPath = fromView ? EndpointInfoView.API_COLLECTION_ID : ApiInfo.ID_API_COLLECTION_ID;

        List<BsonField> sums = Arrays.asList(
                Accumulators.sum(TOTAL, 1),
                Accumulators.sum(NEW, cond(gte(ifNull(field(ApiInfo.DISCOVERED_TIMESTAMP), 0), Context.now() - NEW_APIS_WINDOW_SECONDS), 1, 0)),
                Accumulators.sum(THIRD_PARTY, cond(in(ApiAccessType.THIRD_PARTY.name(), ifNull(field(ApiInfo.API_ACCESS_TYPES), new ArrayList<>())), 1, 0)),
                Accumulators.sum(CRITICAL, cond(gte(ifNull(field(ApiInfo.RISK_SCORE), 0), CRITICAL_RISK_SCORE), 1, 0)));
        List<Bson> pipeline = Arrays.asList(Aggregates.match(Filters.in(collectionIdPath, collectionIds)), Aggregates.group(null, sums));
        BasicDBObject row = (fromView ? EndpointInfoViewDao.instance.getMCollection() : ApiInfoDao.instance.getMCollection())
                .withDocumentClass(BasicDBObject.class).aggregate(pipeline).first();
        return row == null ? new BasicDBObject() : row;
    }

    private Map<String, Long> countAuditInfoByType() {
        Map<String, Long> counts = new HashMap<>();
        McpAuditInfoDao.instance.getMCollection().withDocumentClass(BasicDBObject.class).aggregate(Arrays.asList(
                Aggregates.match(Filters.ne(McpAuditInfo.REMARKS, McpAuditInfo.REMARKS_REJECTED)),
                Aggregates.group(field(McpAuditInfo.TYPE), Accumulators.sum(COUNT, 1)))).forEach(row ->
                counts.put(row.getString(Constants.ID), ((Number) row.get(COUNT)).longValue()));
        return counts;
    }

    private Data recentAlerts(int skip, int limit) {
        // alerts nobody has marked yet; browser LLM sites and AI agents aren't MCP components
        Bson open = Filters.and(
                Filters.nin(McpAuditInfo.TYPE, Arrays.asList(Constants.AKTO_BROWSER_LLM_TAG, Constants.AKTO_AI_AGENT_TAG)),
                Filters.or(Filters.eq(McpAuditInfo.MARKED_BY, ""), Filters.eq(McpAuditInfo.MARKED_BY, null)));
        List<Alert> alerts = timed("recent mcp alerts", () -> McpAuditInfoDao.instance.findAll(open, skip, limit,
                Sorts.descending(McpAuditInfo.LAST_DETECTED),
                Projections.include(McpAuditInfo.TYPE, McpAuditInfo.LAST_DETECTED, McpAuditInfo.RESOURCE_NAME)).stream()
                .map(a -> new Alert(a.getType(), a.getLastDetected(), a.getResourceName())).collect(Collectors.toList()), List::size);
        return new Data(alerts, new ArrayList<>(), McpAuditInfoDao.instance.count(open));
    }

    private Data topApplications(int skip, int limit) {
        List<ApiCollectionStats> mcp = taggedCollections(Tag.MCP);
        if (mcp.isEmpty()) return new Data(new ArrayList<>(), new ArrayList<>(), 0);

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.in(HIT_COUNT_COLLECTION, ids(mcp))),
                Aggregates.group(field(HIT_COUNT_COLLECTION), Accumulators.sum(HITS, field(HIT_COUNT_COUNT))),
                Aggregates.facet(
                        // the id breaks ties, so pages never repeat or drop an application
                        new Facet(ROWS, Aggregates.sort(Sorts.orderBy(Sorts.descending(HITS), Sorts.ascending(Constants.ID))),
                                Aggregates.skip(skip), Aggregates.limit(limit)),
                        new Facet(TOTAL, Aggregates.count(COUNT))));
        Map<Integer, String> names = mcp.stream().collect(Collectors.toMap(ApiCollectionStats::getId, ApiCollectionStats::getName, (a, b) -> a));
        BasicDBObject result = timed("top mcp applications by traffic", () -> ApiHitCountInfoDao.instance.getMCollection()
                .withDocumentClass(BasicDBObject.class).aggregate(pipeline).first());

        List<TopApplication> top = new ArrayList<>();
        for (Object entry : list(result, ROWS)) {
            BasicDBObject row = (BasicDBObject) entry;
            top.add(new TopApplication(names.get(row.getInt(Constants.ID)), row.getInt(Constants.ID), ((Number) row.get(HITS)).longValue()));
        }
        // only the collections with traffic can be listed, so only they count towards the pages
        List<?> totals = list(result, TOTAL);
        long total = totals.isEmpty() ? 0 : ((Number) ((BasicDBObject) totals.get(0)).get(COUNT)).longValue();
        return new Data(new ArrayList<>(), top, total);
    }

    private static List<?> list(BasicDBObject row, String branch) {
        Object value = row == null ? null : row.get(branch);
        return value instanceof List ? (List<?>) value : new ArrayList<>();
    }

    private static long number(BasicDBObject row, String field) {
        Object value = row.get(field);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }
}
