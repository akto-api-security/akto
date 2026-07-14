package com.akto.utils.elasticsearch;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.search.SearchClient;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Elasticsearch-backed implementation of {@link SearchClient}. Owns HTTP transport, query/
 * aggregation DSL construction, and response parsing for every semantic method on the contract.
 */
public class ElasticSearchClient extends SearchClient {

    private static final LoggerMaker logger = new LoggerMaker(ElasticSearchClient.class, LogDb.DASHBOARD);

    private static final String ES_HOST  = System.getenv("ES_HOST");
    private static final String ES_API_KEY = System.getenv("ES_API_KEY");
    private static final String ES_INDEX = System.getenv("ES_INDEX_AGENT_QUERY");

    private static final String SCROLL_KEEP_ALIVE = "2m";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

    // ── Aggregation key names (label agg buckets in the request/response) ─────
    // KEY_LATEST_TS/KEY_FIRST_TS/KEY_MSG_COUNT/KEY_SPAN_COUNT double as both the ES sub-agg
    // name AND the output row key, so they reuse the shared KEY_* constants from SearchClient
    // instead of redeclaring the same string locally. The rest here are ES-DSL-internal bucket
    // names with no corresponding output key, so they stay private to this class.
    private static final String AGG_GROUPS              = "groups";
    private static final String AGG_IN_TOKENS           = "inTokens";
    private static final String AGG_OUT_TOKENS          = "outTokens";
    private static final String AGG_FIRST_HIT           = "firstHit";
    private static final String AGG_TOTAL_SESSIONS      = "totalSessions";
    private static final String AGG_TOTAL_INPUT_TOKENS  = "totalInputTokens";
    private static final String AGG_TOTAL_OUTPUT_TOKENS = "totalOutputTokens";
    private static final String AGG_TOP_USERS           = "topUsersByTokens";
    private static final String AGG_USER_BREAKDOWN      = "userBreakdown";
    private static final String AGG_TOTAL_SPANS         = "totalSpans";
    private static final String AGG_TOP_APPS            = "topApps";
    private static final String AGG_TOP_TRACES          = "topTraces";
    private static final String AGG_TRACE_SPARK         = "traceSpark";
    private static final String AGG_SESSION_SPARK       = "sessionSpark";
    // Nested agg: terms on topic.keyword → sub-agg terms on subTopic.keyword.
    // Preserves domain→subDomain link so the frontend can show the hierarchy correctly.
    private static final String AGG_TOPIC_HIERARCHY     = "topicHierarchyAgg";

    private static final ElasticSearchClient INSTANCE = new ElasticSearchClient();
    public static ElasticSearchClient instance() { return INSTANCE; }

    private final OkHttpClient http;

    private ElasticSearchClient() {
        this.http = CoreHTTPClient.client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public boolean isConfigured() {
        return ES_HOST != null && !ES_HOST.isEmpty()
            && ES_INDEX != null && !ES_INDEX.isEmpty();
    }

    // ── Per-session view ──────────────────────────────────────────────────────

    @Override
    public SessionsResult fetchSessions(int accountId, long startMs, long endMs, String searchString,
                                         Map<String, List<String>> filters, Boolean atlasTrafficFilter,
                                         int sessionsLimit, String sessionsAfterKey) {
        List<Map<String, Object>> sessions = new ArrayList<>();
        String nextAfterKey = null;
        long totalSessions = 0;
        if (!isConfigured()) return new SessionsResult(sessions, null, 0);
        try {
            JSONObject filteredQuery = buildQuery(accountId, startMs, endMs, filters, searchString, atlasTrafficFilter);
            filteredQuery.getJSONObject("bool").getJSONArray("must")
                .put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER)));

            JSONObject subAggs = new JSONObject()
                .put(KEY_LATEST_TS,   new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(KEY_FIRST_TS,    new JSONObject().put("min", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_IN_TOKENS,   new JSONObject().put("sum", new JSONObject().put("field", AgentQueryRecord.F_INPUT_TOKENS)))
                .put(AGG_OUT_TOKENS,  new JSONObject().put("sum", new JSONObject().put("field", AgentQueryRecord.F_OUTPUT_TOKENS)))
                .put(KEY_MSG_COUNT,   new JSONObject().put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_TRACE_ID_KW)))
                .put(AGG_TOPIC_HIERARCHY, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_TOPIC_KW).put("size", 5))
                    .put("aggs", new JSONObject()
                        .put("subTopics", new JSONObject()
                            .put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 5)))))
                .put(AGG_FIRST_HIT, new JSONObject()
                    // Earliest LLM row: merged /v1/messages (has model) or legacy prompt-only (body, not tool/MCP).
                    .put("filter", new JSONObject().put("bool", new JSONObject()
                        .put("minimum_should_match", 1)
                        .put("should", new JSONArray()
                            .put(new JSONObject().put("match_phrase", new JSONObject()
                                .put(AgentQueryRecord.F_RESPONSE_PAYLOAD, "model")))
                            .put(new JSONObject().put("bool", new JSONObject()
                                .put("must", new JSONArray()
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "\"body\""))))
                                .put("must_not", new JSONArray()
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "toolName")))
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "tools/call")))))))))
                    .put("aggs", new JSONObject().put("hit", new JSONObject().put("top_hits", new JSONObject()
                        .put("size", 1)
                        .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))))
                        .put("_source", new JSONArray()
                            .put(AgentQueryRecord.F_QUERY_PAYLOAD)
                            .put(AgentQueryRecord.F_RESPONSE_PAYLOAD)
                            .put(AgentQueryRecord.F_SERVICE_ID)
                            .put(AgentQueryRecord.F_USER_NAME)
                            .put(AgentQueryRecord.F_DEVICE_ID)
                            .put(AgentQueryRecord.F_SESSION_IDENTIFIER))))));

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
                            .put("order", new JSONObject().put(KEY_LATEST_TS, "desc")))
                        .put("aggs", subAggs))
                    .put(AGG_TOTAL_SESSIONS, new JSONObject()
                        .put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)));

                JSONObject aggsResult = aggregate(filteredQuery, aggs);
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
                        .put("order", new JSONObject().put(KEY_LATEST_TS, "desc")))
                    .put("aggs", subAggs));
                JSONObject aggsResult = aggregate(filteredQuery, aggs);
                sessions = parseBuckets(aggsResult, AgentQueryRecord.F_SESSION_IDENTIFIER);
                totalSessions = sessions.size();
            }
        } catch (Exception e) {
            logger.error("fetchSessions error for accountId=" + accountId + ": " + e.getMessage());
            sessions = new ArrayList<>();
        }
        return new SessionsResult(sessions, nextAfterKey, totalSessions);
    }

    @Override
    public List<Map<String, Object>> fetchMessages(int accountId, long startMs, long endMs,
                                                     Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (!isConfigured()) return messages;
        try {
            JSONObject filteredQuery = buildQuery(accountId, startMs, endMs, filters, null, atlasTrafficFilter);
            filteredQuery.getJSONObject("bool").getJSONArray("must")
                .put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_TRACE_ID)));

            JSONObject subAggs = new JSONObject()
                .put(KEY_LATEST_TS,   new JSONObject().put("max",        new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(KEY_FIRST_TS,    new JSONObject().put("min",        new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_IN_TOKENS,   new JSONObject().put("sum",        new JSONObject().put("field", AgentQueryRecord.F_INPUT_TOKENS)))
                .put(AGG_OUT_TOKENS,  new JSONObject().put("sum",        new JSONObject().put("field", AgentQueryRecord.F_OUTPUT_TOKENS)))
                .put(KEY_SPAN_COUNT,  new JSONObject().put("value_count", new JSONObject().put("field", AgentQueryRecord.F_SPAN_ID_KW)))
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
                    .put("order", new JSONObject().put(KEY_LATEST_TS, "desc")))
                .put("aggs", subAggs));

            JSONObject aggsResult = aggregate(filteredQuery, aggs);
            messages = parseBuckets(aggsResult, AgentQueryRecord.F_TRACE_ID);
        } catch (Exception e) {
            logger.error("fetchMessages error for accountId=" + accountId + ": " + e.getMessage());
            messages = new ArrayList<>();
        }
        return messages;
    }

    // ── Session-level aggregated stats (accurate cardinality + token sums) ──────

    @Override
    public SessionAggStats fetchSessionAggStats(int accountId, long startMs, long endMs,
                                                 Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        long aggTotalSessions = 0, aggInputTokens = 0, aggOutputTokens = 0;
        List<Map<String, Object>> aggTopUsers = new ArrayList<>();
        List<Map<String, Object>> aggUserBreakdown = new ArrayList<>();
        List<Long> aggSessionSpark = new ArrayList<>();
        List<Long> aggSessionSparkTs = new ArrayList<>();
        List<Long> aggSessionTokenSpark = new ArrayList<>();

        if (!isConfigured()) {
            return new SessionAggStats(0, 0, 0, aggTopUsers, aggUserBreakdown,
                aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
        }
        try {
            JSONObject filteredQuery = buildQuery(accountId, startMs, endMs, filters, null, atlasTrafficFilter);
            filteredQuery.getJSONObject("bool").getJSONArray("must")
                .put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER)));

            long sparkEndMs = Math.min(endMs, System.currentTimeMillis());

            // Phase 1: get actual data time range so the histogram uses the right granularity.
            // Without this, "all time" (startMs=0) always produces monthly buckets even when
            // all data is from the last few weeks.
            long dataMinMs = sparkEndMs;
            long dataMaxMs = sparkEndMs;
            JSONObject rangeResult = aggregate(filteredQuery, new JSONObject()
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

            JSONObject aggsResult = aggregate(filteredQuery, aggs);
            if (aggsResult == null) {
                return new SessionAggStats(0, 0, 0, aggTopUsers, aggUserBreakdown,
                    aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
            }

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
            logger.error("fetchSessionAggStats error for accountId=" + accountId + ": " + e.getMessage());
        }
        return new SessionAggStats(aggTotalSessions, aggInputTokens, aggOutputTokens,
            aggTopUsers, aggUserBreakdown, aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
    }

    // ── Argus aggregated stats (total spans + token sums + top apps/traces + sparklines) ──

    @Override
    public ArgusStats fetchArgusStats(int accountId, long startMs, long endMs, Boolean atlasTrafficFilter) {
        long aggTotalSpans = 0, aggInputTokens = 0, aggOutputTokens = 0;
        List<Map<String, Object>> aggTopApps = new ArrayList<>();
        List<Map<String, Object>> aggAppBreakdown = new ArrayList<>();
        List<Map<String, Object>> aggTopTraces = new ArrayList<>();
        List<Long> aggTraceSpark = new ArrayList<>();
        List<Long> aggTokenSpark = new ArrayList<>();
        List<Long> aggTraceSparkTs = new ArrayList<>();

        if (!isConfigured()) {
            return new ArgusStats(0, 0, 0, aggTopApps, aggAppBreakdown, aggTopTraces,
                aggTraceSpark, aggTokenSpark, aggTraceSparkTs);
        }
        try {
            JSONObject filteredQuery = buildQuery(accountId, startMs, endMs, null, null, atlasTrafficFilter);

            long argusSparkEndMs = Math.min(endMs, System.currentTimeMillis());

            // Phase 1: actual data extent for granularity
            long argusDataMinMs = argusSparkEndMs;
            long argusDataMaxMs = argusSparkEndMs;
            JSONObject argusRangeResult = aggregate(filteredQuery, new JSONObject()
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

            JSONObject aggsResult = aggregate(filteredQuery, aggs);
            if (aggsResult == null) {
                return new ArgusStats(0, 0, 0, aggTopApps, aggAppBreakdown, aggTopTraces,
                    aggTraceSpark, aggTokenSpark, aggTraceSparkTs);
            }

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
            logger.error("fetchArgusStats error for accountId=" + accountId + ": " + e.getMessage());
        }
        return new ArgusStats(aggTotalSpans, aggInputTokens, aggOutputTokens,
            aggTopApps, aggAppBreakdown, aggTopTraces, aggTraceSpark, aggTokenSpark, aggTraceSparkTs);
    }

    // ── Spans for a single message/trace ──────────────────────────────────────

    @Override
    public List<Map<String, Object>> fetchTraceDetail(int accountId, String traceId, Boolean atlasTrafficFilter) {
        List<Map<String, Object>> spans = new ArrayList<>();
        if (!isConfigured() || traceId == null || traceId.trim().isEmpty()) return spans;
        try {
            Map<String, List<String>> filters = new HashMap<>();
            filters.put(AgentQueryRecord.F_TRACE_ID_KW, java.util.Collections.singletonList(traceId.trim()));
            JSONObject query = buildQuery(accountId, 0L, Long.MAX_VALUE, filters, null, atlasTrafficFilter);

            JSONObject body = new JSONObject()
                .put("query", query)
                .put("size", 500)
                .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))));

            JSONObject response = httpPost(trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search", body.toString());
            if (response == null) return spans;

            JSONArray hits = extractHits(response);
            if (hits == null) return spans;
            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.optJSONObject(i);
                if (hit == null) continue;
                JSONObject source = hit.optJSONObject("_source");
                if (source == null) continue;
                Map<String, Object> row = jsonObjectToMap(source);
                row.put("id", hit.optString("_id", ""));
                spans.add(row);
            }
        } catch (Exception e) {
            logger.error("fetchTraceDetail error for accountId=" + accountId + ": " + e.getMessage());
            return new ArrayList<>();
        }
        return spans;
    }

    // ── Filter choices (distinct values for column filters) ───────────────────

    @Override
    public Map<String, List<String>> fetchPromptFilters(int accountId, long startMs, long endMs) {
        Map<String, List<String>> filterChoices = new HashMap<>();
        if (!isConfigured()) return filterChoices;
        try {
            JSONObject query = buildQuery(accountId, startMs, endMs, null, null, null);
            JSONObject aggs = new JSONObject()
                .put(AgentQueryRecord.F_USER_NAME,  new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_USER_NAME_KW).put("size", 500)))
                .put(AgentQueryRecord.F_DEVICE_ID,  new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_DEVICE_ID_KW).put("size", 500)))
                .put(AgentQueryRecord.F_SERVICE_ID, new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_SERVICE_ID_KW).put("size", 500)))
                .put(AgentQueryRecord.F_TOPIC,    new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_TOPIC_KW).put("size", 100)))
                .put(AgentQueryRecord.F_SUB_TOPIC, new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 200)));

            JSONObject aggsResult = aggregate(query, aggs);
            filterChoices.put(AgentQueryRecord.F_USER_NAME,  extractBucketKeys(aggsResult, AgentQueryRecord.F_USER_NAME));
            filterChoices.put(AgentQueryRecord.F_DEVICE_ID,  extractBucketKeys(aggsResult, AgentQueryRecord.F_DEVICE_ID));
            filterChoices.put(AgentQueryRecord.F_SERVICE_ID, extractBucketKeys(aggsResult, AgentQueryRecord.F_SERVICE_ID));
            filterChoices.put("topic",    extractBucketKeys(aggsResult, "topic"));
            filterChoices.put("subTopic", extractBucketKeys(aggsResult, "subTopic"));
        } catch (Exception e) {
            return new HashMap<>();
        }
        return filterChoices;
    }

    // ── Paginated flat prompt search ───────────────────────────────────────────

    @Override
    public SearchResult searchPrompts(int accountId, long startMs, long endMs, int skip, int limit,
                                       String sortKey, boolean sortAsc, String searchAfterJson,
                                       Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString) {
        if (!isConfigured()) return new SearchResult(new ArrayList<>(), 0);
        try {
            JSONArray searchAfter = null;
            if (searchAfterJson != null && !searchAfterJson.trim().isEmpty()) {
                try { searchAfter = new JSONArray(searchAfterJson); } catch (Exception ignored) {}
            }
            JSONObject query = buildQuery(accountId, startMs, endMs, filters, searchString, atlasTrafficFilter);
            return executeSearch(query, skip, Math.min(limit, 100), toEsField(sortKey), sortAsc, searchAfter);
        } catch (Exception e) {
            logger.error("searchPrompts error for accountId=" + accountId + ": " + e.getMessage());
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    private SearchResult executeSearch(JSONObject query, int skip, int limit,
                                        String sortField, boolean sortAsc, JSONArray searchAfter) throws JSONException {
        String sortDir = sortAsc ? "asc" : "desc";
        String resolvedSort = (sortField != null && !sortField.isEmpty()) ? sortField : AgentQueryRecord.F_TIMESTAMP;

        JSONObject body = new JSONObject()
            .put("query", query)
            .put("sort", new JSONArray().put(new JSONObject().put(resolvedSort, new JSONObject().put("order", sortDir))))
            .put("size", limit)
            .put("track_total_hits", true);

        if (searchAfter != null && searchAfter.length() > 0) {
            body.put("search_after", searchAfter);
        } else if (skip > 0 && skip < 10000) {
            body.put("from", skip);
        }

        JSONObject response = httpPost(trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search", body.toString());
        if (response == null) return new SearchResult(new ArrayList<>(), 0);

        long total = 0;
        JSONObject hitsWrapper = response.optJSONObject("hits");
        if (hitsWrapper != null) {
            JSONObject totalObj = hitsWrapper.optJSONObject("total");
            total = totalObj != null ? totalObj.optLong("value", 0) : hitsWrapper.optLong("total", 0);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        JSONArray hits = extractHits(response);
        if (hits != null) {
            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.getJSONObject(i);
                JSONObject source = hit.optJSONObject("_source");
                if (source == null) continue;
                Map<String, Object> row = jsonObjectToMap(source);
                row.put("id", hit.optString("_id", ""));
                row.put("_sortValues", hit.optJSONArray("sort"));
                results.add(row);
            }
        }
        return new SearchResult(results, total);
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

    // ── Topic write-back (used by crons after classification) ─────────────────

    @Override
    public void bulkUpdateTopics(List<TopicUpdate> updates) {
        if (updates == null || updates.isEmpty() || !isConfigured()) return;
        StringBuilder sb = new StringBuilder();
        for (TopicUpdate u : updates) {
            String safeDocId    = u.docId.replace("\\", "\\\\").replace("\"", "\\\"");
            String safeTopic    = u.topic.replace("\\", "\\\\").replace("\"", "\\\"");
            String safeSubTopic = u.subTopic.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("{\"update\":{\"_id\":\"").append(safeDocId).append("\"}}\n");
            sb.append("{\"doc\":{\"topic\":\"").append(safeTopic)
              .append("\",\"subTopic\":\"").append(safeSubTopic)
              .append("\",\"topicProcessed\":true}}\n");
        }
        String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_bulk";
        Request.Builder rb = new Request.Builder()
            .url(url)
            .method("POST", RequestBody.create(sb.toString(), MediaType.parse("application/x-ndjson")))
            .addHeader("Content-Type", "application/x-ndjson");
        addAuthHeader(rb);
        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                logger.error("bulkUpdateTopics failed (" + resp.code() + ") for " + updates.size() + " docs");
            }
        } catch (Exception e) {
            logger.error("bulkUpdateTopics error: " + e.getMessage());
        }
    }

    // ── Scroll API (used by crons) ────────────────────────────────────────────

    @Override
    public void scrollQueryData(int accountId, long startTsMs, long endTsMs, int pageSize,
                                int maxRecords, Consumer<AgentQueryRecord> handler) {
        if (!isConfigured()) return;
        String scrollId = null;
        int delivered = 0;
        try {
            String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search?scroll=" + SCROLL_KEEP_ALIVE;
            JSONObject baseQuery = buildQuery(accountId, startTsMs, endTsMs, null, null, null);
            // Only fetch records not yet topic-classified
            baseQuery.getJSONObject("bool").put("must_not",
                new JSONArray().put(new JSONObject().put("term",
                    new JSONObject().put("topicProcessed", true))));
            JSONObject requestBody = new JSONObject()
                .put("query", baseQuery)
                .put("size", pageSize)
                .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))));
            JSONObject response = httpPost(url, requestBody.toString());
            if (response == null) return;

            scrollId = response.optString("_scroll_id", null);
            JSONArray hits = extractHits(response);

            while (hits != null && hits.length() > 0 && delivered < maxRecords) {
                for (int i = 0; i < hits.length() && delivered < maxRecords; i++) {
                    AgentQueryRecord rec = parseHit(hits.getJSONObject(i));
                    if (rec != null) { handler.accept(rec); delivered++; }
                }
                if (delivered >= maxRecords || scrollId == null) break;
                JSONObject scrollBody = new JSONObject().put("scroll", SCROLL_KEEP_ALIVE).put("scroll_id", scrollId);
                response = httpPost(trimTrailingSlash(ES_HOST) + "/_search/scroll", scrollBody.toString());
                if (response == null) break;
                scrollId = response.optString("_scroll_id", scrollId);
                hits = extractHits(response);
            }
        } catch (Exception e) {
            logger.error("scrollQueryData error for accountId=" + accountId + ": " + e.getMessage());
        } finally {
            if (scrollId != null) releaseScroll(scrollId);
        }
    }

    // ── Generic aggregation ─────────────────────────────────────────────────────

    private JSONObject aggregate(JSONObject query, JSONObject aggs) {
        if (!isConfigured()) return null;
        try {
            JSONObject body = new JSONObject().put("size", 0).put("query", query).put("aggs", aggs);
            JSONObject response = httpPost(trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search", body.toString());
            if (response == null) return null;
            return response.optJSONObject("aggregations");
        } catch (Exception e) {
            logger.error("aggregate error: " + e.getMessage());
            return null;
        }
    }

    // ── Query builder ───────────────────────────────────────────────────────────

    /**
     * Unified query builder: filter values are always terms clauses (single-value filters are
     * just size-1 lists), and isAtlasTraffic is a first-class tri-state parameter (null = no
     * filter, TRUE/FALSE = filter) rather than a stringly-typed convention buried in the map.
     */
    private JSONObject buildQuery(int accountId, long startMs, long endMs,
                                  Map<String, List<String>> filters, String searchString,
                                  Boolean atlasTrafficFilter) throws JSONException {
        JSONArray must = new JSONArray()
            .put(new JSONObject().put("range", new JSONObject()
                .put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("gte", startMs).put("lt", endMs))))
            .put(new JSONObject().put("term", new JSONObject().put(AgentQueryRecord.F_ACCOUNT_ID, accountId)));
        JSONArray mustNot = new JSONArray();

        if (filters != null) {
            for (Map.Entry<String, List<String>> e : filters.entrySet()) {
                List<String> vals = e.getValue();
                if (vals == null || vals.isEmpty()) continue;
                JSONArray arr = new JSONArray();
                for (String v : vals) arr.put(v);
                must.put(new JSONObject().put("terms", new JSONObject().put(e.getKey(), arr)));
            }
        }

        if (atlasTrafficFilter != null) {
            if (atlasTrafficFilter) {
                must.put(new JSONObject().put("term", new JSONObject().put(AgentQueryRecord.F_IS_ATLAS_TRAFFIC, true)));
            } else {
                // "false" also covers docs that predate this field and never had it set.
                mustNot.put(new JSONObject().put("term", new JSONObject().put(AgentQueryRecord.F_IS_ATLAS_TRAFFIC, true)));
            }
        }

        applySearchString(must, searchString);

        JSONObject boolQuery = new JSONObject().put("must", must);
        if (mustNot.length() > 0) boolQuery.put("must_not", mustNot);
        return new JSONObject().put("bool", boolQuery);
    }

    private void applySearchString(JSONArray must, String searchString) throws JSONException {
        if (searchString == null || searchString.isEmpty()) return;
        must.put(new JSONObject().put("bool", new JSONObject()
            .put("should", new JSONArray()
                .put(new JSONObject().put("match_phrase_prefix", new JSONObject().put(AgentQueryRecord.F_QUERY_PAYLOAD, searchString)))
                .put(new JSONObject().put("match_phrase_prefix", new JSONObject().put(AgentQueryRecord.F_USER_NAME, searchString)))
                .put(new JSONObject().put("match_phrase_prefix", new JSONObject().put(AgentQueryRecord.F_SERVICE_ID, searchString))))
            .put("minimum_should_match", 1)));
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

    private static List<Map<String, Object>> parseBuckets(JSONObject aggsResult, String keyField) throws JSONException {
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
            long latest    = subAggLong(bucket, KEY_LATEST_TS);
            long first     = subAggLong(bucket, KEY_FIRST_TS);
            row.put(keyField,                       keyValue);
            row.put(KEY_SPAN_COUNT,                 bucket.optLong("doc_count", 0));
            row.put(KEY_LATEST_TS,                  latest);
            row.put(KEY_FIRST_TS,                   first);
            row.put(KEY_DURATION_MS,                latest > first ? latest - first : 0);
            row.put(AgentQueryRecord.F_INPUT_TOKENS,  inTokens);
            row.put(AgentQueryRecord.F_OUTPUT_TOKENS, outTokens);
            row.put(KEY_TOTAL_TOKENS,                inTokens + outTokens);
            row.put(KEY_MSG_COUNT,                   subAggLong(bucket, KEY_MSG_COUNT));

            JSONObject firstHitAgg = bucket.optJSONObject(AGG_FIRST_HIT);
            if (firstHitAgg != null) {
                JSONObject hitsRoot = firstHitAgg.optJSONObject("hit");
                if (hitsRoot == null) hitsRoot = firstHitAgg;
                JSONArray topHits = hitsRoot.optJSONObject("hits") != null
                    ? hitsRoot.getJSONObject("hits").optJSONArray("hits") : null;
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
                if (!hierarchy.isEmpty()) row.put(KEY_TOPIC_HIERARCHY, hierarchy);
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

    // ── Internal transport helpers ──────────────────────────────────────────────

    private AgentQueryRecord parseHit(JSONObject hit) {
        JSONObject source = hit.optJSONObject("_source");
        if (source == null) return null;
        String queryPayload = source.optString("queryPayload", "");
        if (queryPayload.isEmpty()) return null;
        return new AgentQueryRecord(
            hit.optString("_id", ""),
            source.optInt("accountId", 0),
            source.optString("serviceId", ""),
            source.optString("deviceId", ""),
            source.optString("userName", ""),
            source.optString("sessionIdentifier", ""),
            queryPayload,
            source.optString("responsePayload", ""),
            source.optLong("timestamp", 0L),
            source.optInt("inputTokens", 0),
            source.optInt("outputTokens", 0),
            source.optString("traceId", ""),
            source.optString("spanId", ""),
            source.optBoolean("isAtlasTraffic", false),
            source.optString(AgentQueryRecord.F_TOPIC, ""),
            source.optString(AgentQueryRecord.F_SUB_TOPIC, "")
        );
    }

    private JSONArray extractHits(JSONObject response) {
        JSONObject outer = response.optJSONObject("hits");
        return outer != null ? outer.optJSONArray("hits") : null;
    }

    private JSONObject httpPost(String url, String body) {
        Request.Builder rb = new Request.Builder()
            .url(url)
            .method("POST", RequestBody.create(body, JSON_MEDIA))
            .addHeader("Content-Type", "application/json");
        addAuthHeader(rb);
        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                logger.error("ES request failed (" + resp.code() + ") for " + url);
                return null;
            }
            ResponseBody rbody = resp.body();
            return rbody != null ? new JSONObject(rbody.string()) : null;
        } catch (Exception e) {
            logger.error("ES request error for " + url + ": " + e.getMessage());
            return null;
        }
    }

    private void releaseScroll(String scrollId) {
        try {
            JSONObject body = new JSONObject().put("scroll_id", scrollId);
            Request.Builder rb = new Request.Builder()
                .url(trimTrailingSlash(ES_HOST) + "/_search/scroll")
                .method("DELETE", RequestBody.create(body.toString(), JSON_MEDIA))
                .addHeader("Content-Type", "application/json");
            addAuthHeader(rb);
            try (Response resp = http.newCall(rb.build()).execute()) { /* best-effort */ }
        } catch (Exception ignored) {}
    }

    private void addAuthHeader(Request.Builder rb) {
        if (ES_API_KEY != null && !ES_API_KEY.isEmpty()) {
            rb.addHeader("Authorization", "ApiKey " + ES_API_KEY);
        }
    }

    private static String trimTrailingSlash(String s) {
        return (s == null || !s.endsWith("/")) ? s == null ? "" : s : s.substring(0, s.length() - 1);
    }
}
