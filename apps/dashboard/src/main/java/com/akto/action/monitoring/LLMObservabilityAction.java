package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.akto.utils.elasticsearch.ElasticSearchClient.SearchResult;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LLMObservabilityAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(LLMObservabilityAction.class, LogDb.DASHBOARD);

    // ── Aggregation key names (label agg buckets in the request/response) ─────
    private static final String AGG_GROUPS              = "groups";
    private static final String AGG_LATEST_TS           = "latestTimestamp";
    private static final String AGG_FIRST_TS            = "firstTimestamp";
    private static final String AGG_IN_TOKENS           = "inTokens";
    private static final String AGG_OUT_TOKENS          = "outTokens";
    private static final String AGG_MSG_COUNT           = "messageCount";
    private static final String AGG_FIRST_HIT           = "firstHit";
    private static final String AGG_SPAN_COUNT          = "spanCount";
    private static final String AGG_TOTAL_SESSIONS      = "totalSessions";
    private static final String AGG_TOTAL_INPUT_TOKENS  = "totalInputTokens";
    private static final String AGG_TOTAL_OUTPUT_TOKENS = "totalOutputTokens";
    private static final String AGG_TOP_USERS           = "topUsersByTokens";
    private static final String AGG_USER_BREAKDOWN      = "userBreakdown";
    private static final String AGG_TOTAL_SPANS         = "totalSpans";
    private static final String AGG_TOP_APPS            = "topApps";
    private static final String AGG_TOP_TRACES          = "topTraces";
    private static final String AGG_TRACE_SPARK          = "traceSpark";
    private static final String AGG_SESSION_SPARK        = "sessionSpark";
    // Nested agg: terms on topic.keyword → sub-agg terms on subTopic.keyword.
    // Preserves domain→subDomain link so the frontend can show the hierarchy correctly.
    private static final String AGG_TOPIC_HIERARCHY      = "topicHierarchyAgg";

    // ── Synthetic result-row keys (computed, not native ES doc fields) ─────────
    private static final String KEY_TOTAL_TOKENS  = "totalTokens";
    private static final String KEY_DURATION_MS   = "durationMs";
    private static final String KEY_LABEL         = "label";
    private static final String KEY_COUNT         = "count";

    @Setter private int          startTime;
    @Setter private int          endTime;
    @Setter private String       searchString;
    @Setter private int          skip        = 0;
    @Setter private int          limit       = 20;
    @Setter private String       sortKey     = AgentQueryRecord.F_TIMESTAMP;
    @Setter private int          sortOrder   = 1;
    @Setter private String       traceId;
    @Setter private String       searchAfterJson;
    @Setter private String       sessionsAfterKey;
    @Setter private int          sessionsLimit    = 20;

    @Getter private String       nextAfterKey;
    @Getter private long         totalSessions    = 0;

    // Single-value fields kept for backward-compat (session drill-down in SessionsView)
    @Setter private String       sessionId;
    @Setter private String       userName;
    @Setter private String       deviceId;
    @Setter private String       serviceId;

    // Multi-value filter lists (used by MessagesView / PromptsView ag-grid set filters)
    @Setter private List<String> userNames      = new ArrayList<>();
    @Setter private List<String> serviceIds     = new ArrayList<>();
    @Setter private List<String> sessionIds     = new ArrayList<>();
    @Setter private List<String> topicFilters    = new ArrayList<>();
    @Setter private List<String> subTopicFilters = new ArrayList<>();

    @Getter private List<Map<String, Object>>  sessions      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  messages      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  prompts       = new ArrayList<>();
    @Getter private List<Map<String, Object>>  spans         = new ArrayList<>();
    @Getter private Map<String, List<String>>  filterChoices = new HashMap<>();
    @Getter private long                       total         = 0;

    // Aggregated stats (fetchSessionAggStats)
    @Getter private long                       aggTotalSessions   = 0;
    @Getter private long                       aggInputTokens     = 0;
    @Getter private long                       aggOutputTokens    = 0;
    @Getter private List<Map<String, Object>>  aggTopUsers        = new ArrayList<>();
    @Getter private List<Map<String, Object>>  aggUserBreakdown   = new ArrayList<>();
    @Getter private List<Long>                 aggSessionSpark      = new ArrayList<>();
    @Getter private List<Long>                 aggSessionSparkTs    = new ArrayList<>();
    @Getter private List<Long>                 aggSessionTokenSpark = new ArrayList<>();

    // Argus aggregated stats (fetchArgusStats)
    @Getter private long                       aggTotalSpans      = 0;
    @Getter private List<Map<String, Object>>  aggTopApps         = new ArrayList<>();
    @Getter private List<Map<String, Object>>  aggAppBreakdown    = new ArrayList<>();
    @Getter private List<Map<String, Object>>  aggTopTraces       = new ArrayList<>();
    @Getter private List<Long>                 aggTraceSpark      = new ArrayList<>();
    @Getter private List<Long>                 aggTokenSpark      = new ArrayList<>();
    @Getter private List<Long>                 aggTraceSparkTs    = new ArrayList<>();

    private long startMs() { return (long) startTime * 1000L; }
    private long endMs()   { return (long) endTime   * 1000L; }

    // ── Per-session view ──────────────────────────────────────────────────────

    /**
     * Dual-mode session fetch on the same route:
     *  - sessionsLimit == 0 (default): terms agg, top-500 by latest activity — used by summary cards.
     *  - sessionsLimit  > 0           : composite agg with cursor pagination — used by the sessions table.
     * Both modes always return { sessions, nextAfterKey, totalSessions }.
     */
    public String fetchSessions() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Map<String, List<String>> extraFilters = buildMultiFilters(true);
            JSONObject baseQ = es.buildBaseQueryMulti(accountId, startMs(), endMs(), extraFilters.isEmpty() ? null : extraFilters, this.searchString);
            JSONArray mustArr = baseQ.getJSONObject("bool").getJSONArray("must");
            mustArr.put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER)));
            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject().put("must", mustArr));

            JSONObject subAggs = new JSONObject()
                .put(AGG_LATEST_TS,   new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_FIRST_TS,    new JSONObject().put("min", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_IN_TOKENS,   new JSONObject().put("sum", new JSONObject().put("field", AgentQueryRecord.F_INPUT_TOKENS)))
                .put(AGG_OUT_TOKENS,  new JSONObject().put("sum", new JSONObject().put("field", AgentQueryRecord.F_OUTPUT_TOKENS)))
                .put(AGG_MSG_COUNT,   new JSONObject().put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_TRACE_ID_KW)))
                .put(AGG_TOPIC_HIERARCHY, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_TOPIC_KW).put("size", 5))
                    .put("aggs", new JSONObject()
                        .put("subTopics", new JSONObject()
                            .put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 5)))))
                .put(AGG_FIRST_HIT, new JSONObject().put("top_hits", new JSONObject()
                    .put("size", 1)
                    .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))))
                    .put("_source", new JSONArray()
                        .put(AgentQueryRecord.F_QUERY_PAYLOAD)
                        .put(AgentQueryRecord.F_RESPONSE_PAYLOAD)
                        .put(AgentQueryRecord.F_SERVICE_ID)
                        .put(AgentQueryRecord.F_USER_NAME)
                        .put(AgentQueryRecord.F_DEVICE_ID)
                        .put(AgentQueryRecord.F_SESSION_IDENTIFIER))));

            if (sessionsLimit > 0) {
                // ── Paginated path: terms agg sorted by latest activity globally ──────
                // Composite agg can only page by its source key (sessionId), not by a
                // sub-agg metric, so cross-page sort is broken. Terms agg with
                // order:{latestTimestamp:"desc"} gives a globally correct sort; we fetch
                // enough buckets to cover the requested page and slice in application code.
                int pageSize  = Math.min(sessionsLimit, 100);
                int pageOffset = 0;
                if (sessionsAfterKey != null && !sessionsAfterKey.trim().isEmpty()) {
                    try { pageOffset = Integer.parseInt(sessionsAfterKey.trim()); }
                    catch (NumberFormatException ignored) {}
                }
                int fetchSize = Math.min(pageOffset + pageSize, 10_000);

                JSONObject aggs = new JSONObject()
                    .put(AGG_GROUPS, new JSONObject()
                        .put("terms", new JSONObject()
                            .put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)
                            .put("size", fetchSize)
                            .put("order", new JSONObject().put(AGG_LATEST_TS, "desc")))
                        .put("aggs", subAggs))
                    .put(AGG_TOTAL_SESSIONS, new JSONObject()
                        .put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)));

                JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
                List<Map<String, Object>> allSessions = parseBuckets(aggsResult, AgentQueryRecord.F_SESSION_IDENTIFIER);

                int fromIdx = Math.min(pageOffset, allSessions.size());
                int toIdx   = Math.min(pageOffset + pageSize, allSessions.size());
                sessions = new ArrayList<>(allSessions.subList(fromIdx, toIdx));
                nextAfterKey = toIdx < allSessions.size() ? String.valueOf(toIdx) : null;

                if (aggsResult != null) {
                    JSONObject totalAgg = aggsResult.optJSONObject(AGG_TOTAL_SESSIONS);
                    if (totalAgg != null) totalSessions = (long) totalAgg.optDouble("value", 0);
                }
            } else {
                // ── Summary path: terms aggregation, top-500 by latest activity ──────
                JSONObject aggs = new JSONObject().put(AGG_GROUPS, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW).put("size", 500)
                        .put("order", new JSONObject().put(AGG_LATEST_TS, "desc")))
                    .put("aggs", subAggs));
                JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
                sessions = parseBuckets(aggsResult, AgentQueryRecord.F_SESSION_IDENTIFIER);
                totalSessions = sessions.size();
            }
        } catch (Exception e) {
            logger.error("fetchSessions error: " + e.getMessage());
            sessions = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchMessages() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Map<String, List<String>> extraFilters = buildMultiFilters(true);
            JSONObject baseQ = es.buildBaseQueryMulti(accountId, startMs(), endMs(), extraFilters.isEmpty() ? null : extraFilters, null);
            JSONArray mustArr = baseQ.getJSONObject("bool").getJSONArray("must");
            mustArr.put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_TRACE_ID)));

            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject().put("must", mustArr));

            JSONObject subAggs = new JSONObject()
                .put(AGG_LATEST_TS,   new JSONObject().put("max",        new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_FIRST_TS,    new JSONObject().put("min",        new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_IN_TOKENS,   new JSONObject().put("sum",        new JSONObject().put("field", AgentQueryRecord.F_INPUT_TOKENS)))
                .put(AGG_OUT_TOKENS,  new JSONObject().put("sum",        new JSONObject().put("field", AgentQueryRecord.F_OUTPUT_TOKENS)))
                .put(AGG_SPAN_COUNT,  new JSONObject().put("value_count", new JSONObject().put("field", AgentQueryRecord.F_SPAN_ID_KW)))
                .put(AGG_TOPIC_HIERARCHY, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_TOPIC_KW).put("size", 5))
                    .put("aggs", new JSONObject()
                        .put("subTopics", new JSONObject()
                            .put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 5)))))
                .put(AGG_FIRST_HIT, new JSONObject().put("top_hits", new JSONObject()
                    .put("size", 1)
                    .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))))
                    .put("_source", new JSONArray()
                        .put(AgentQueryRecord.F_QUERY_PAYLOAD)
                        .put(AgentQueryRecord.F_RESPONSE_PAYLOAD)
                        .put(AgentQueryRecord.F_SERVICE_ID)
                        .put(AgentQueryRecord.F_USER_NAME)
                        .put(AgentQueryRecord.F_DEVICE_ID)
                        .put(AgentQueryRecord.F_SESSION_IDENTIFIER)
                        .put(AgentQueryRecord.F_TRACE_ID))));

            JSONObject aggs = new JSONObject().put(AGG_GROUPS, new JSONObject()
                .put("terms", new JSONObject().put("field", AgentQueryRecord.F_TRACE_ID_KW).put("size", 500)
                    .put("order", new JSONObject().put(AGG_LATEST_TS, "desc")))
                .put("aggs", subAggs));

            JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
            messages = parseBuckets(aggsResult, AgentQueryRecord.F_TRACE_ID);
        } catch (Exception e) {
            logger.error("fetchMessages error: " + e.getMessage());
            messages = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Session-level aggregated stats (accurate cardinality + token sums) ──────

    public String fetchSessionAggStats() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Map<String, List<String>> extraFilters = buildMultiFilters(true);
            JSONObject baseQ = es.buildBaseQueryMulti(accountId, startMs(), endMs(), extraFilters.isEmpty() ? null : extraFilters, null);
            JSONArray mustArr = baseQ.getJSONObject("bool").getJSONArray("must");
            mustArr.put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER)));
            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject().put("must", mustArr));

            long sparkEndMs = Math.min(endMs(), System.currentTimeMillis());

            // Phase 1: get actual data time range so the histogram uses the right granularity.
            // Without this, "all time" (startMs=0) always produces monthly buckets even when
            // all data is from the last few weeks.
            long dataMinMs = sparkEndMs;
            long dataMaxMs = sparkEndMs;
            JSONObject rangeResult = es.aggregate(filteredQuery, new JSONObject()
                .put("dataMin", new JSONObject().put("min", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put("dataMax", new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP))));
            if (rangeResult != null) {
                JSONObject minAgg = rangeResult.optJSONObject("dataMin");
                JSONObject maxAgg = rangeResult.optJSONObject("dataMax");
                if (minAgg != null && !minAgg.isNull("value"))
                    dataMinMs = (long) minAgg.optDouble("value", (double) sparkEndMs);
                if (maxAgg != null && !maxAgg.isNull("value"))
                    dataMaxMs = Math.min((long) maxAgg.optDouble("value", (double) sparkEndMs), sparkEndMs);
            }

            // Phase 2: build histogram with data-driven granularity.
            long intervalMs      = sparklineIntervalMs(dataMinMs, dataMaxMs);
            long histStart       = dataMaxMs - 12L * intervalMs;
            String fixedInterval = intervalMs + "ms";

            JSONObject aggs = new JSONObject()
                .put(AGG_TOTAL_SESSIONS,      new JSONObject().put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)))
                .put(AGG_TOTAL_INPUT_TOKENS,  sumAgg(AgentQueryRecord.F_INPUT_TOKENS))
                .put(AGG_TOTAL_OUTPUT_TOKENS, sumAgg(AgentQueryRecord.F_OUTPUT_TOKENS))
                .put(AGG_TOP_USERS, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_USER_NAME_KW).put("size", 10))
                    .put("aggs", tokenSubAggs()))
                .put(AGG_USER_BREAKDOWN, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_USER_NAME_KW).put("size", 3)))
                .put(AGG_SESSION_SPARK, new JSONObject()
                    .put("date_histogram", new JSONObject()
                        .put("field", AgentQueryRecord.F_TIMESTAMP)
                        .put("fixed_interval", fixedInterval)
                        .put("min_doc_count", 0)
                        .put("extended_bounds", new JSONObject()
                            .put("min", histStart)
                            .put("max", dataMaxMs)))
                    // Cardinality per bucket counts unique sessions; doc_count would count messages.
                    .put("aggs", new JSONObject()
                        .put(AGG_TOTAL_SESSIONS, new JSONObject()
                            .put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)))
                        .put(AGG_IN_TOKENS,  sumAgg(AgentQueryRecord.F_INPUT_TOKENS))
                        .put(AGG_OUT_TOKENS, sumAgg(AgentQueryRecord.F_OUTPUT_TOKENS))));

            JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
            if (aggsResult == null) return Action.SUCCESS.toUpperCase();

            aggTotalSessions = subAggLong(aggsResult, AGG_TOTAL_SESSIONS);
            aggInputTokens   = subAggLong(aggsResult, AGG_TOTAL_INPUT_TOKENS);
            aggOutputTokens  = subAggLong(aggsResult, AGG_TOTAL_OUTPUT_TOKENS);

            for (Map<String, Object> row : parseTermsBuckets(aggsResult, AGG_TOP_USERS, AgentQueryRecord.F_USER_NAME)) {
                long in  = ((Number) row.get(AgentQueryRecord.F_INPUT_TOKENS)).longValue();
                long out = ((Number) row.get(AgentQueryRecord.F_OUTPUT_TOKENS)).longValue();
                row.put(KEY_TOTAL_TOKENS, in + out);
                aggTopUsers.add(row);
            }
            aggUserBreakdown.addAll(parseBreakdown(aggsResult, AGG_USER_BREAKDOWN, 3));

            JSONObject sessionSparkAgg = aggsResult.optJSONObject(AGG_SESSION_SPARK);
            if (sessionSparkAgg != null) {
                JSONArray buckets = sessionSparkAgg.optJSONArray("buckets");
                if (buckets != null) {
                    int n = buckets.length();
                    int start = Math.max(0, n - 12);
                    for (int i = start; i < n; i++) {
                        JSONObject b = buckets.optJSONObject(i);
                        if (b == null) continue;
                        aggSessionSpark.add(subAggLong(b, AGG_TOTAL_SESSIONS));
                        aggSessionSparkTs.add(b.optLong("key") / 1000L);
                        aggSessionTokenSpark.add(subAggLong(b, AGG_IN_TOKENS) + subAggLong(b, AGG_OUT_TOKENS));
                    }
                }
            }
            if (aggSessionSpark.isEmpty()) {
                aggSessionSpark.add(0L);
                aggSessionSparkTs.add(0L);
                aggSessionTokenSpark.add(0L);
            }
        } catch (Exception e) {
            logger.error("fetchSessionAggStats error: " + e.getMessage());
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Argus aggregated stats (total spans + token sums + top apps/traces + sparklines) ──

    public String fetchArgusStats() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            // Base time-range + account query, then exclude Atlas traffic so the
            // total here matches what the Argus paginated table reports.
            JSONObject baseQ = es.buildBaseQuery(accountId, startMs(), endMs(), null, null);
            JSONArray mustArr = baseQ.getJSONObject("bool").getJSONArray("must");
            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject()
                .put("must", mustArr)
                .put("must_not", new JSONArray()
                    .put(new JSONObject().put("term", new JSONObject()
                        .put(AgentQueryRecord.F_IS_ATLAS_TRAFFIC, true)))));

            long argusSparkEndMs = Math.min(endMs(), System.currentTimeMillis());

            // Phase 1: actual data extent for granularity
            long argusDataMinMs = argusSparkEndMs;
            long argusDataMaxMs = argusSparkEndMs;
            JSONObject argusRangeResult = es.aggregate(filteredQuery, new JSONObject()
                .put("dataMin", new JSONObject().put("min", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put("dataMax", new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP))));
            if (argusRangeResult != null) {
                JSONObject minAgg = argusRangeResult.optJSONObject("dataMin");
                JSONObject maxAgg = argusRangeResult.optJSONObject("dataMax");
                if (minAgg != null && !minAgg.isNull("value"))
                    argusDataMinMs = (long) minAgg.optDouble("value", (double) argusSparkEndMs);
                if (maxAgg != null && !maxAgg.isNull("value"))
                    argusDataMaxMs = Math.min((long) maxAgg.optDouble("value", (double) argusSparkEndMs), argusSparkEndMs);
            }

            long argusIntervalMs = sparklineIntervalMs(argusDataMinMs, argusDataMaxMs);
            long argusHistStart  = argusDataMaxMs - 12L * argusIntervalMs;
            String fixedInterval = argusIntervalMs + "ms";

            JSONObject aggs = new JSONObject()
                .put(AGG_TOTAL_SPANS,         new JSONObject().put("value_count", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_TOTAL_INPUT_TOKENS,  sumAgg(AgentQueryRecord.F_INPUT_TOKENS))
                .put(AGG_TOTAL_OUTPUT_TOKENS, sumAgg(AgentQueryRecord.F_OUTPUT_TOKENS))
                .put(AGG_TOP_APPS, new JSONObject()
                    .put("terms", new JSONObject()
                        .put("field", AgentQueryRecord.F_SERVICE_ID_KW)
                        .put("size", 10)
                        .put("order", new JSONObject().put(AGG_IN_TOKENS, "desc")))
                    .put("aggs", tokenSubAggs()))
                .put(AGG_TOP_TRACES, new JSONObject()
                    .put("terms", new JSONObject()
                        .put("field", AgentQueryRecord.F_TRACE_ID_KW)
                        .put("size", 10)
                        .put("order", new JSONObject().put(AGG_IN_TOKENS, "desc")))
                    .put("aggs", tokenSubAggs()
                        .put(AGG_FIRST_HIT, new JSONObject().put("top_hits", new JSONObject()
                            .put("size", 1)
                            .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))))
                            .put("_source", new JSONArray()
                                .put(AgentQueryRecord.F_QUERY_PAYLOAD)
                                .put(AgentQueryRecord.F_RESPONSE_PAYLOAD)
                                .put(AgentQueryRecord.F_SERVICE_ID)
                                .put(AgentQueryRecord.F_TRACE_ID))))))
                .put(AGG_TRACE_SPARK, new JSONObject()
                    .put("date_histogram", new JSONObject()
                        .put("field", AgentQueryRecord.F_TIMESTAMP)
                        .put("fixed_interval", fixedInterval)
                        .put("min_doc_count", 0)
                        .put("extended_bounds", new JSONObject()
                            .put("min", argusHistStart)
                            .put("max", argusDataMaxMs)))
                    .put("aggs", tokenSubAggs()));

            JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
            if (aggsResult == null) return Action.SUCCESS.toUpperCase();

            aggTotalSpans   = subAggLong(aggsResult, AGG_TOTAL_SPANS);
            aggInputTokens  = subAggLong(aggsResult, AGG_TOTAL_INPUT_TOKENS);
            aggOutputTokens = subAggLong(aggsResult, AGG_TOTAL_OUTPUT_TOKENS);

            aggTopApps.addAll(parseTermsBuckets(aggsResult, AGG_TOP_APPS, AgentQueryRecord.F_SERVICE_ID));

            // First 3 apps (already sorted by input tokens desc) form the breakdown
            for (int i = 0; i < Math.min(3, aggTopApps.size()); i++) {
                Map<String, Object> app = aggTopApps.get(i);
                Map<String, Object> entry = new HashMap<>();
                entry.put(KEY_LABEL, app.get(AgentQueryRecord.F_SERVICE_ID));
                entry.put(KEY_COUNT, ((Number) app.get(KEY_COUNT)).longValue());
                aggAppBreakdown.add(entry);
            }

            // Top traces
            JSONObject topTracesAgg = aggsResult.optJSONObject(AGG_TOP_TRACES);
            if (topTracesAgg != null) {
                JSONArray buckets = topTracesAgg.optJSONArray("buckets");
                if (buckets != null) {
                    for (int i = 0; i < buckets.length(); i++) {
                        JSONObject b = buckets.optJSONObject(i);
                        if (b == null) continue;
                        String tid = b.optString("key", "");
                        if (tid.isEmpty()) continue;
                        long in  = subAggLong(b, AGG_IN_TOKENS);
                        long out = subAggLong(b, AGG_OUT_TOKENS);
                        Map<String, Object> row = new HashMap<>();
                        row.put(AgentQueryRecord.F_TRACE_ID,      tid);
                        row.put(AgentQueryRecord.F_INPUT_TOKENS,  in);
                        row.put(AgentQueryRecord.F_OUTPUT_TOKENS, out);
                        JSONObject firstHitAgg = b.optJSONObject(AGG_FIRST_HIT);
                        if (firstHitAgg != null) {
                            JSONArray topHits = firstHitAgg.optJSONObject("hits") != null
                                ? firstHitAgg.getJSONObject("hits").optJSONArray("hits") : null;
                            if (topHits != null && topHits.length() > 0) {
                                JSONObject src = topHits.getJSONObject(0).optJSONObject("_source");
                                if (src != null) {
                                    row.put(AgentQueryRecord.F_QUERY_PAYLOAD,    src.optString(AgentQueryRecord.F_QUERY_PAYLOAD,    ""));
                                    row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD, src.optString(AgentQueryRecord.F_RESPONSE_PAYLOAD, ""));
                                    row.put(AgentQueryRecord.F_SERVICE_ID,       src.optString(AgentQueryRecord.F_SERVICE_ID,       ""));
                                }
                            }
                        }
                        aggTopTraces.add(row);
                    }
                }
            }

            // Sparklines — last 12 buckets so the current period is always the rightmost bar
            JSONObject sparkAgg = aggsResult.optJSONObject(AGG_TRACE_SPARK);
            if (sparkAgg != null) {
                JSONArray buckets = sparkAgg.optJSONArray("buckets");
                if (buckets != null) {
                    int n = buckets.length();
                    int start = Math.max(0, n - 12);
                    for (int i = start; i < n; i++) {
                        JSONObject b = buckets.optJSONObject(i);
                        if (b == null) continue;
                        aggTraceSpark.add(b.optLong("doc_count", 0));
                        aggTokenSpark.add(subAggLong(b, AGG_IN_TOKENS) + subAggLong(b, AGG_OUT_TOKENS));
                        aggTraceSparkTs.add(b.optLong("key") / 1000L);
                    }
                }
            }
            if (aggTraceSpark.isEmpty()) { aggTraceSpark.add(0L); aggTokenSpark.add(0L); aggTraceSparkTs.add(0L); }
        } catch (Exception e) {
            logger.error("fetchArgusStats error: " + e.getMessage());
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Spans for a single message/trace ──────────────────────────────────────

    public String fetchTraceDetail() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured() || traceId == null || traceId.trim().isEmpty())
                return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Map<String, String> filters = new HashMap<>();
            filters.put(AgentQueryRecord.F_TRACE_ID_KW, traceId.trim());
            if (CONTEXT_SOURCE.ENDPOINT.equals(Context.contextSource.get()))
                filters.put(AgentQueryRecord.F_IS_ATLAS_TRAFFIC, "true");
            JSONObject query = es.buildBaseQuery(accountId, 0L, Long.MAX_VALUE, filters, null);

            JSONObject body = new JSONObject()
                .put("query", query)
                .put("size", 500)
                .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))));

            JSONObject response = es.httpPost(
                trimTrailingSlash(System.getenv("ES_HOST")) + "/" + es.getIndex() + "/_search",
                body.toString());
            if (response == null) return Action.SUCCESS.toUpperCase();

            JSONArray hits = es.extractHits(response);
            if (hits == null) return Action.SUCCESS.toUpperCase();
            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.optJSONObject(i);
                if (hit == null) continue;
                JSONObject source = hit.optJSONObject("_source");
                if (source == null) continue;
                Map<String, Object> row = ElasticSearchClient.jsonObjectToMap(source);
                row.put("id", hit.optString("_id", ""));
                spans.add(row);
            }
        } catch (Exception e) {
            spans = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Filter choices (distinct values for column filters) ───────────────────

    public String fetchPromptFilters() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            JSONObject query = es.buildBaseQuery(accountId, startMs(), endMs(), null, null);
            JSONObject aggs = new JSONObject()
                .put(AgentQueryRecord.F_USER_NAME,  new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_USER_NAME_KW).put("size", 500)))
                .put(AgentQueryRecord.F_DEVICE_ID,  new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_DEVICE_ID_KW).put("size", 500)))
                .put(AgentQueryRecord.F_SERVICE_ID, new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_SERVICE_ID_KW).put("size", 500)))
                .put(AgentQueryRecord.F_TOPIC,    new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_TOPIC_KW).put("size", 100)))
                .put(AgentQueryRecord.F_SUB_TOPIC, new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 200)));

            JSONObject aggsResult = es.aggregate(query, aggs);
            filterChoices = new HashMap<>();
            filterChoices.put(AgentQueryRecord.F_USER_NAME,  extractBucketKeys(aggsResult, AgentQueryRecord.F_USER_NAME));
            filterChoices.put(AgentQueryRecord.F_DEVICE_ID,  extractBucketKeys(aggsResult, AgentQueryRecord.F_DEVICE_ID));
            filterChoices.put(AgentQueryRecord.F_SERVICE_ID, extractBucketKeys(aggsResult, AgentQueryRecord.F_SERVICE_ID));
            filterChoices.put("topic",    extractBucketKeys(aggsResult, "topic"));
            filterChoices.put("subTopic", extractBucketKeys(aggsResult, "subTopic"));
        } catch (Exception e) {
            filterChoices = new HashMap<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Paginated flat prompt search ───────────────────────────────────────────

    public String searchPrompts() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Map<String, List<String>> filters = buildMultiFilters(true);

            JSONArray searchAfter = null;
            if (searchAfterJson != null && !searchAfterJson.trim().isEmpty()) {
                try { searchAfter = new JSONArray(searchAfterJson); } catch (Exception ignored) {}
            }

            SearchResult result = es.searchMulti(
                accountId, startMs(), endMs(),
                skip, Math.min(limit, 100),
                toEsField(sortKey), sortOrder == -1,
                searchAfter, filters, searchString);

            prompts = result.hits;
            total   = result.total;
        } catch (Exception e) {
            prompts = new ArrayList<>();
            total   = 0;
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Mirrors GroupByTimeRange.groupByAllRange logic for Elasticsearch fixed_interval.
     * ≤ 12 days → fine-grained (actual range / 12, min 1 h)
     * ≤ 84 days → weekly (7 d)
     * > 84 days → monthly (30 d)
     * Returns interval in milliseconds; caller appends "ms" and computes histStart = endMs - 12*interval.
     */
    static long sparklineIntervalMs(long startMs, long endMs) {
        long daysBetween = (endMs - startMs) / (24 * 3600 * 1000L);
        if (daysBetween <= 12) {
            return Math.max(3_600_000L, (endMs - startMs) / 12);
        } else if (daysBetween <= 84) {
            return 7L * 24 * 3600 * 1000;   // 1 week
        } else {
            return 30L * 24 * 3600 * 1000;  // ~1 month
        }
    }

    /** {"sum":{"field":field}} — building block for all token aggregations. */
    private static JSONObject sumAgg(String field) throws JSONException {
        return new JSONObject().put("sum", new JSONObject().put("field", field));
    }

    /** Shared {inTokens, outTokens} sub-aggregation used under every terms/date_histogram. */
    private static JSONObject tokenSubAggs() throws JSONException {
        return new JSONObject()
            .put(AGG_IN_TOKENS,  sumAgg(AgentQueryRecord.F_INPUT_TOKENS))
            .put(AGG_OUT_TOKENS, sumAgg(AgentQueryRecord.F_OUTPUT_TOKENS));
    }

    /**
     * Iterates a named terms aggregation and returns one Map per bucket with:
     * keyField, F_INPUT_TOKENS, F_OUTPUT_TOKENS, KEY_COUNT (doc_count).
     * Buckets with an empty key are skipped.
     */
    private static List<Map<String, Object>> parseTermsBuckets(
            JSONObject aggsResult, String aggName, String keyField) throws JSONException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (aggsResult == null) return out;
        JSONObject agg = aggsResult.optJSONObject(aggName);
        if (agg == null) return out;
        JSONArray buckets = agg.optJSONArray("buckets");
        if (buckets == null) return out;
        for (int i = 0; i < buckets.length(); i++) {
            JSONObject b = buckets.optJSONObject(i);
            if (b == null) continue;
            String key = b.optString("key", "");
            if (key.isEmpty()) continue;
            Map<String, Object> row = new HashMap<>();
            row.put(keyField,                         key);
            row.put(AgentQueryRecord.F_INPUT_TOKENS,  subAggLong(b, AGG_IN_TOKENS));
            row.put(AgentQueryRecord.F_OUTPUT_TOKENS, subAggLong(b, AGG_OUT_TOKENS));
            row.put(KEY_COUNT,                        b.optLong("doc_count", 0));
            out.add(row);
        }
        return out;
    }

    /**
     * Extracts up to maxItems {label, count} entries from a plain terms aggregation (no sub-aggs).
     */
    private static List<Map<String, Object>> parseBreakdown(
            JSONObject aggsResult, String aggName, int maxItems) throws JSONException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (aggsResult == null) return out;
        JSONObject agg = aggsResult.optJSONObject(aggName);
        if (agg == null) return out;
        JSONArray buckets = agg.optJSONArray("buckets");
        if (buckets == null) return out;
        for (int i = 0; i < Math.min(maxItems, buckets.length()); i++) {
            JSONObject b = buckets.optJSONObject(i);
            if (b == null) continue;
            String key = b.optString("key", "");
            if (key.isEmpty()) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put(KEY_LABEL, key);
            entry.put(KEY_COUNT, b.optLong("doc_count", 0));
            out.add(entry);
        }
        return out;
    }

    private List<Map<String, Object>> parseBuckets(JSONObject aggsResult, String keyField) throws JSONException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (aggsResult == null) return result;
        JSONObject groups = aggsResult.optJSONObject(AGG_GROUPS);
        if (groups == null) return result;
        JSONArray buckets = groups.optJSONArray("buckets");
        if (buckets == null) return result;

        for (int i = 0; i < buckets.length(); i++) {
            JSONObject bucket = buckets.optJSONObject(i);
            if (bucket == null) continue;
            // Terms agg: key is a plain string.
            // Composite agg: key is a JSON object { keyField: value }.
            Object rawKey  = bucket.opt("key");
            String keyValue = (rawKey instanceof JSONObject)
                ? ((JSONObject) rawKey).optString(keyField, "")
                : (rawKey != null ? rawKey.toString() : "");
            if (keyValue.isEmpty()) continue;

            Map<String, Object> row = new HashMap<>();
            long inTokens  = subAggLong(bucket, AGG_IN_TOKENS);
            long outTokens = subAggLong(bucket, AGG_OUT_TOKENS);
            long latest    = subAggLong(bucket, AGG_LATEST_TS);
            long first     = subAggLong(bucket, AGG_FIRST_TS);
            row.put(keyField,                       keyValue);
            row.put(AGG_SPAN_COUNT,                 bucket.optLong("doc_count", 0));
            row.put(AGG_LATEST_TS,                  latest);
            row.put(AGG_FIRST_TS,                   first);
            row.put(KEY_DURATION_MS,                latest > first ? latest - first : 0);
            row.put(AgentQueryRecord.F_INPUT_TOKENS,  inTokens);
            row.put(AgentQueryRecord.F_OUTPUT_TOKENS, outTokens);
            row.put(KEY_TOTAL_TOKENS,                inTokens + outTokens);
            row.put(AGG_MSG_COUNT,                   subAggLong(bucket, AGG_MSG_COUNT));

            JSONObject firstHitAgg = bucket.optJSONObject(AGG_FIRST_HIT);
            if (firstHitAgg != null) {
                JSONArray topHits = firstHitAgg.optJSONObject("hits") != null
                    ? firstHitAgg.getJSONObject("hits").optJSONArray("hits") : null;
                if (topHits != null && topHits.length() > 0) {
                    JSONObject src = topHits.getJSONObject(0).optJSONObject("_source");
                    if (src != null) {
                        row.put(AgentQueryRecord.F_QUERY_PAYLOAD,       src.optString(AgentQueryRecord.F_QUERY_PAYLOAD,       ""));
                        row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD,    src.optString(AgentQueryRecord.F_RESPONSE_PAYLOAD,    ""));
                        row.put(AgentQueryRecord.F_SERVICE_ID,          src.optString(AgentQueryRecord.F_SERVICE_ID,          ""));
                        row.put(AgentQueryRecord.F_USER_NAME,           src.optString(AgentQueryRecord.F_USER_NAME,           ""));
                        row.put(AgentQueryRecord.F_DEVICE_ID,           src.optString(AgentQueryRecord.F_DEVICE_ID,           ""));
                        row.put(AgentQueryRecord.F_SESSION_IDENTIFIER,  src.optString(AgentQueryRecord.F_SESSION_IDENTIFIER,  ""));
                        row.put(AgentQueryRecord.F_TRACE_ID,            src.optString(AgentQueryRecord.F_TRACE_ID,            ""));
                    }
                }
            }

            // Extract topic hierarchy: domain → [subDomain1, subDomain2, ...].
            // Preserves the domain→subDomain link; frontend reads row.topicHierarchy.
            JSONObject topicHierarchyAgg = bucket.optJSONObject(AGG_TOPIC_HIERARCHY);
            if (topicHierarchyAgg != null) {
                JSONArray topicBuckets = topicHierarchyAgg.optJSONArray("buckets");
                Map<String, Object> hierarchy = new LinkedHashMap<>();
                if (topicBuckets != null) {
                    for (int j = 0; j < topicBuckets.length(); j++) {
                        JSONObject tb = topicBuckets.optJSONObject(j);
                        if (tb == null) continue;
                        String domainKey = tb.optString("key", "");
                        if (domainKey.isEmpty()) continue;
                        List<String> subTopics = extractBucketKeyList(tb.optJSONObject("subTopics"));
                        hierarchy.put(domainKey, subTopics);
                    }
                }
                if (!hierarchy.isEmpty()) row.put("topicHierarchy", hierarchy);
            }

            result.add(row);
        }
        return result;
    }

    private static long subAggLong(JSONObject bucket, String name) {
        JSONObject o = bucket.optJSONObject(name);
        return o != null ? (long) o.optDouble("value", 0) : 0;
    }

    private static List<String> extractBucketKeys(JSONObject aggsResult, String field) {
        if (aggsResult == null) return new ArrayList<>();
        return extractBucketKeyList(aggsResult.optJSONObject(field));
    }

    /** Extracts ordered bucket key strings from an already-resolved terms-agg object. */
    private static List<String> extractBucketKeyList(JSONObject agg) {
        List<String> out = new ArrayList<>();
        if (agg == null) return out;
        JSONArray buckets = agg.optJSONArray("buckets");
        if (buckets == null) return out;
        for (int i = 0; i < buckets.length(); i++) {
            try {
                String key = buckets.getJSONObject(i).optString("key", "");
                if (!key.isEmpty()) out.add(key);
            } catch (JSONException ignored) {}
        }
        return out;
    }

    private static String toEsField(String frontendKey) {
        if (frontendKey == null) return AgentQueryRecord.F_TIMESTAMP;
        switch (frontendKey) {
            case "timeStampMs":
            case "timestamp":  return AgentQueryRecord.F_TIMESTAMP;
            case "userName":   return AgentQueryRecord.F_USER_NAME_KW;
            case "serviceId":  return AgentQueryRecord.F_SERVICE_ID_KW;
            default:           return AgentQueryRecord.F_TIMESTAMP;
        }
    }

    /** Merges single-value fields and multi-value lists into one Map for buildBaseQueryMulti. */
    private Map<String, List<String>> buildMultiFilters(boolean includeSession) {
        Map<String, List<String>> f = new HashMap<>();
        // Session filter: prefer multi-value list, fall back to single field
        if (includeSession) {
            List<String> sessionList = nonEmpty(sessionIds);
            if (sessionList.isEmpty() && sessionId != null && !sessionId.trim().isEmpty())
                sessionList = java.util.Collections.singletonList(sessionId.trim());
            if (!sessionList.isEmpty()) f.put(AgentQueryRecord.F_SESSION_IDENTIFIER_KW, sessionList);
        }
        // userName
        List<String> users = nonEmpty(userNames);
        if (users.isEmpty() && userName != null && !userName.trim().isEmpty())
            users = java.util.Collections.singletonList(userName.trim());
        if (!users.isEmpty()) f.put(AgentQueryRecord.F_USER_NAME_KW, users);
        // serviceId
        List<String> services = nonEmpty(serviceIds);
        if (services.isEmpty() && serviceId != null && !serviceId.trim().isEmpty())
            services = java.util.Collections.singletonList(serviceId.trim());
        if (!services.isEmpty()) f.put(AgentQueryRecord.F_SERVICE_ID_KW, services);
        // deviceId (single-value only; no ag-grid filter for this field)
        if (deviceId != null && !deviceId.trim().isEmpty())
            f.put(AgentQueryRecord.F_DEVICE_ID_KW, java.util.Collections.singletonList(deviceId.trim()));
        // traceId — used when scoping Messages tab to a specific trace
        if (traceId != null && !traceId.trim().isEmpty())
            f.put(AgentQueryRecord.F_TRACE_ID_KW, java.util.Collections.singletonList(traceId.trim()));
        // topic / subTopic filters
        List<String> topics = nonEmpty(topicFilters);
        if (!topics.isEmpty()) f.put(AgentQueryRecord.F_TOPIC_KW, topics);
        List<String> subTopics = nonEmpty(subTopicFilters);
        if (!subTopics.isEmpty()) f.put(AgentQueryRecord.F_SUB_TOPIC_KW, subTopics);
        // Atlas traffic filter: ENDPOINT context only shows Atlas-sourced records
        if (CONTEXT_SOURCE.ENDPOINT.equals(Context.contextSource.get()))
            f.put(AgentQueryRecord.F_IS_ATLAS_TRAFFIC, java.util.Collections.singletonList("true"));
        return f;
    }

    private static List<String> nonEmpty(List<String> list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String s : list) { if (s != null && !s.trim().isEmpty()) out.add(s.trim()); }
        return out;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
