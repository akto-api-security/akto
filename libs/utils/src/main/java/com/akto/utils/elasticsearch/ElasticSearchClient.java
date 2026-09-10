package com.akto.utils.elasticsearch;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.agentic_sessions.UserAnalysisData.UserAnalysisDataKey;
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
    private static final String AGG_TOP_MODELS          = "topModelsBySessions";
    private static final String AGG_USER_BREAKDOWN      = "userBreakdown";
    private static final String AGG_TOTAL_SPANS         = "totalSpans";
    private static final String AGG_TOP_APPS            = "topApps";
    private static final String AGG_TOP_TRACES          = "topTraces";
    private static final String AGG_TRACE_SPARK         = "traceSpark";
    private static final String AGG_SESSION_SPARK       = "sessionSpark";
    private static final int    USER_BREAKDOWN_SIZE      = 3;
    // Cap on fetchMessages' returned (grouped) trace rows.
    private static final int    MESSAGES_SIZE            = 500;
    // fetchMessages must pull every raw doc in scope (not just ones with a traceId) to carry
    // forward trace membership onto untagged spans — capped well under ES's default
    // index.max_result_window (10_000) as a defense-in-depth bound; a session/window with more
    // docs than this loses grouping accuracy for its oldest spans (see the truncation log).
    private static final int    RAW_FETCH_CAP            = 5_000;
    // Cap on the spans returned for a single trace (rendered as waterfall bars + span cards).
    private static final int    TRACE_DETAIL_SIZE        = 500;
    // "model" lives inside the responsePayload JSON, not as an indexed field, so it cannot be a
    // terms agg. We sample the most recent sessions and tally their models application-side —
    // same cap the sessions-summary path uses.
    private static final int    MODEL_SESSION_SAMPLE     = 500;
    private static final int    TOP_N_MODELS             = 5;
    // Nested agg: terms on topic.keyword → sub-agg terms on subTopic.keyword.
    // Preserves domain→subDomain link so the frontend can show the hierarchy correctly.
    private static final String AGG_TOPIC_HIERARCHY     = "topicHierarchyAgg";
    // Distinct policy names hit by any span in the session — flat, no hierarchy to preserve.
    private static final String AGG_GUARDRAIL_POLICIES  = "guardrailPoliciesAgg";
    private static final int    GUARDRAIL_POLICIES_BREADTH = 10;
    // Nested agg: terms on serviceId.keyword → sub-agg terms on deviceId.keyword, with token sums.
    private static final String AGG_USER_ANALYSIS_SERVICE  = "userAnalysisByService";
    private static final String AGG_USER_ANALYSIS_DEVICE   = "userAnalysisByDevice";
    private static final int    USER_ANALYSIS_SERVICE_SIZE = 200;
    private static final int    USER_ANALYSIS_DEVICE_SIZE  = 2000;

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
                                         int sessionsLimit, String sessionsAfterKey, boolean includeTracesContent) {
        List<Map<String, Object>> sessions = new ArrayList<>();
        String nextAfterKey = null;
        long totalSessions = 0;
        if (!isConfigured()) return new SessionsResult(sessions, null, 0);
        try {
            JSONObject filteredQuery = buildQuery(accountId, startMs, endMs, filters, searchString, atlasTrafficFilter);
            filteredQuery.getJSONObject("bool").getJSONArray("must")
                .put(new JSONObject().put("exists", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER)));

            JSONArray sessionHitSource = new JSONArray();
            if (includeTracesContent) {
                sessionHitSource.put(AgentQueryRecord.F_QUERY_PAYLOAD).put(AgentQueryRecord.F_RESPONSE_PAYLOAD);
            }
            sessionHitSource.put(AgentQueryRecord.F_SERVICE_ID).put(AgentQueryRecord.F_USER_NAME)
                .put(AgentQueryRecord.F_DEVICE_ID).put(AgentQueryRecord.F_SESSION_IDENTIFIER);

            JSONObject subAggs = new JSONObject()
                .put(KEY_LATEST_TS,   new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(KEY_FIRST_TS,    new JSONObject().put("min", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_IN_TOKENS,   new JSONObject().put("sum", new JSONObject().put("field", AgentQueryRecord.F_INPUT_TOKENS)))
                .put(AGG_OUT_TOKENS,  new JSONObject().put("sum", new JSONObject().put("field", AgentQueryRecord.F_OUTPUT_TOKENS)))
                .put(KEY_MSG_COUNT,   new JSONObject().put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_TRACE_ID_KW)))
                // Boolean fields aggregate as 0/1 — max is 1 (true) as soon as any span in the
                // session tripped a guardrail. Sessions with no guardrail-evaluated spans at all
                // (field absent on every doc) get a null value, which subAggLong reads as 0/false.
                .put(KEY_HAS_ACTIVE_GUARDRAIL, new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_GUARDRAIL_VIOLATED)))
                .put(AGG_GUARDRAIL_POLICIES, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_GUARDRAIL_POLICY_KW).put("size", GUARDRAIL_POLICIES_BREADTH)))
                .put(AGG_TOPIC_HIERARCHY, new JSONObject()
                    .put("terms", new JSONObject().put("field", AgentQueryRecord.F_TOPIC_KW).put("size", 5))
                    .put("aggs", new JSONObject()
                        .put("subTopics", new JSONObject()
                            .put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 5)))))
                .put(AGG_FIRST_HIT, new JSONObject()
                    // Earliest LLM row: merged /v1/messages (has model), browser-extension rows
                    // ({"userInput": ...}, no model), or legacy prompt-only (body, not tool/MCP).
                    .put("filter", new JSONObject().put("bool", new JSONObject()
                        .put("minimum_should_match", 1)
                        .put("should", new JSONArray()
                            .put(new JSONObject().put("match_phrase", new JSONObject()
                                .put(AgentQueryRecord.F_RESPONSE_PAYLOAD, "model")))
                            // Browser rows: a real turn only — sub-path captures carry no trace id.
                            .put(new JSONObject().put("bool", new JSONObject()
                                .put("must", new JSONArray()
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "userInput")))
                                    .put(new JSONObject().put("exists", new JSONObject()
                                        .put("field", AgentQueryRecord.F_TRACE_ID))))))
                            .put(new JSONObject().put("bool", new JSONObject()
                                .put("must", new JSONArray()
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "\"body\""))))
                                .put("must_not", new JSONArray()
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "toolName")))
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "tools/call"))))))
                            // Catch-all for other payload shapes (e.g. Copilot Studio): any non-tool-call turn with a traceId.
                            .put(new JSONObject().put("bool", new JSONObject()
                                .put("must", new JSONArray()
                                    .put(new JSONObject().put("exists", new JSONObject()
                                        .put("field", AgentQueryRecord.F_TRACE_ID))))
                                .put("must_not", new JSONArray()
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "toolName")))
                                    .put(new JSONObject().put("match_phrase", new JSONObject()
                                        .put(AgentQueryRecord.F_QUERY_PAYLOAD, "tools/call")))))))))
                    .put("aggs", new JSONObject().put("hit", new JSONObject().put("top_hits", new JSONObject()
                        .put("size", 1)
                        .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))))
                        .put("_source", sessionHitSource)))));

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

    /**
     * Raw per-doc fetch, so that spans with no traceId can be related to the trace they actually
     * belong to (see {@link #groupByEffectiveTraceId}) — this can't be done with a terms agg on
     * traceId, since that agg simply drops docs where the field is missing.
     */
    @Override
    public List<Map<String, Object>> fetchMessages(int accountId, long startMs, long endMs,
                                                     Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (!isConfigured()) return messages;
        try {
            JSONObject query = buildQuery(accountId, startMs, endMs, filters, null, atlasTrafficFilter);
            List<Map<String, Object>> docs = fetchRawDocsDescByTime(query, RAW_FETCH_CAP, "fetchMessages", accountId);
            java.util.Collections.reverse(docs); // ascending by timestamp — carry-forward needs chronological order

            messages = groupByEffectiveTraceId(docs);
        } catch (Exception e) {
            logger.error("fetchMessages error for accountId=" + accountId + ": " + e.getMessage());
            messages = new ArrayList<>();
        }
        return messages;
    }

    /** Raw hits (no aggregation), newest first, capped at `cap` with a truncation warning if hit. */
    private List<Map<String, Object>> fetchRawDocsDescByTime(JSONObject query, int cap, String callerLabel, int accountId) throws JSONException {
        List<Map<String, Object>> docs = new ArrayList<>();
        JSONObject body = new JSONObject()
            .put("query", query)
            .put("size", cap)
            .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "desc"))));

        JSONObject response = httpPost(trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search", body.toString());
        JSONArray hits = response != null ? extractHits(response) : null;
        if (hits == null) return docs;
        if (hits.length() >= cap) {
            logger.error(callerLabel + " raw fetch hit its cap (" + cap + ") for accountId=" + accountId
                + " — trace grouping may be incomplete for the oldest spans in range; narrow the time range or session.");
        }
        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            JSONObject source = hit.optJSONObject("_source");
            if (source == null) continue;
            Map<String, Object> row = jsonObjectToMap(source);
            row.put("id", hit.optString("_id", ""));
            docs.add(row);
        }
        return docs;
    }

    /**
     * Only a fraction of docs in a session actually carry a traceId — e.g. a real agent turn —
     * while everything else the integration logs around it (hook events, tool-call telemetry,
     * etc.) never gets tagged. Verified against production data (account 1787207677): every
     * untagged doc's timestamp falls between one traced doc and the next *in the same session*,
     * so the correct trace for an untagged doc is simply the most recent traced doc at or before
     * it — a classic carry-forward/LOCF join. Docs before a session's first-ever traced doc (or
     * in a session with no traced doc at all) have nothing to carry forward from, so each becomes
     * its own single-span "trace" instead of being merged with unrelated spans.
     *
     * One wrinkle also found in that data: setup events (e.g. "SessionStart", "InstructionsLoaded")
     * can land in the *exact same millisecond* as the real request they precede — ES's tie order
     * for same-timestamp docs is not guaranteed to put them before it, so a naive single pass can
     * wrongly treat some of a trace's own lead-in events as pre-trace orphans. Docs are therefore
     * resolved one (session, timestamp) instant at a time: when an instant contains exactly one
     * distinct traceId, every doc in that instant — regardless of array order — resolves to it.
     *
     * @param docsAscByTime raw docs, already sorted ascending by timestamp (carry-forward is
     *                       order-dependent — do not pass docs in any other order).
     */
    private List<Map<String, Object>> groupByEffectiveTraceId(List<Map<String, Object>> docsAscByTime) {
        Map<String, String> effectiveTraceIdByGroup = new HashMap<>();
        LinkedHashMap<String, List<Map<String, Object>>> groups = resolveGroups(docsAscByTime, effectiveTraceIdByGroup);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            result.add(buildTraceRowFromGroup(e.getValue(), effectiveTraceIdByGroup.get(e.getKey())));
        }
        result.sort((a, b) -> Long.compare(asLong(b.get(KEY_LATEST_TS)), asLong(a.get(KEY_LATEST_TS))));
        return result.size() > MESSAGES_SIZE ? new ArrayList<>(result.subList(0, MESSAGES_SIZE)) : result;
    }

    /**
     * The carry-forward resolution itself, factored out so fetchTraceDetail can look up a single
     * trace's actual member docs (a plain traceId.keyword filter only finds the one tagged doc —
     * see fetchTraceDetail) without duplicating this logic.
     *
     * Returns groups keyed "trace:&lt;sessionId&gt;:&lt;effectiveTraceId&gt;" (real or carried-
     * forward trace membership) or "orphan:&lt;n&gt;" (no trace anywhere to relate the doc to) —
     * populates effectiveTraceIdByGroupOut with the resolved traceId for every "trace:" key.
     */
    private LinkedHashMap<String, List<Map<String, Object>>> resolveGroups(
            List<Map<String, Object>> docsAscByTime, Map<String, String> effectiveTraceIdByGroupOut) {
        LinkedHashMap<String, List<Map<String, Object>>> instants = new LinkedHashMap<>();
        for (Map<String, Object> doc : docsAscByTime) {
            String instantKey = strVal(doc.get(AgentQueryRecord.F_SESSION_IDENTIFIER)) + "@" + asLong(doc.get(AgentQueryRecord.F_TIMESTAMP));
            instants.computeIfAbsent(instantKey, k -> new ArrayList<>()).add(doc);
        }

        Map<String, String> lastTraceIdBySession = new HashMap<>();
        LinkedHashMap<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        int[] orphanCounter = {0};

        for (List<Map<String, Object>> instant : instants.values()) {
            String sessionId = strVal(instant.get(0).get(AgentQueryRecord.F_SESSION_IDENTIFIER));
            java.util.LinkedHashSet<String> tracesInInstant = new java.util.LinkedHashSet<>();
            for (Map<String, Object> d : instant) {
                String t = strVal(d.get(AgentQueryRecord.F_TRACE_ID));
                if (!t.isEmpty()) tracesInInstant.add(t);
            }

            if (tracesInInstant.size() == 1) {
                // Unambiguous: the whole instant (tagged doc + any same-millisecond siblings,
                // whichever side of it they landed on) belongs to this one trace.
                String eff = tracesInInstant.iterator().next();
                lastTraceIdBySession.put(sessionId, eff);
                String groupKey = "trace:" + sessionId + ":" + eff;
                effectiveTraceIdByGroupOut.put(groupKey, eff);
                groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).addAll(instant);
            } else {
                // Zero traceIds (ordinary carry-forward), or — rarely — more than one distinct
                // traceId tied at the same millisecond, which is genuinely ambiguous: resolve
                // each doc by its own traceId if it has one, otherwise fall back to whatever was
                // last resolved *before* this instant rather than guessing between the ties.
                for (Map<String, Object> d : instant) {
                    String own = strVal(d.get(AgentQueryRecord.F_TRACE_ID));
                    String eff = !own.isEmpty() ? own : lastTraceIdBySession.get(sessionId);
                    if (!own.isEmpty()) lastTraceIdBySession.put(sessionId, own);

                    String groupKey = (eff != null && !eff.isEmpty())
                        ? "trace:" + sessionId + ":" + eff
                        : "orphan:" + (orphanCounter[0]++);
                    groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(d);
                    if (eff != null && !eff.isEmpty()) effectiveTraceIdByGroupOut.put(groupKey, eff);
                }
            }
        }
        return groups;
    }

    /** groupDocs must already be in ascending-timestamp order (the first/last entries are the trace's first/latest hit). */
    private Map<String, Object> buildTraceRowFromGroup(List<Map<String, Object>> groupDocs, String effectiveTraceId) {
        Map<String, Object> first = groupDocs.get(0);
        Map<String, Object> last  = groupDocs.get(groupDocs.size() - 1);
        long firstTs  = asLong(first.get(AgentQueryRecord.F_TIMESTAMP));
        long latestTs = asLong(last.get(AgentQueryRecord.F_TIMESTAMP));

        long sumIn = 0, sumOut = 0;
        boolean hasGuardrail = false;
        java.util.LinkedHashSet<String> guardrailPolicies = new java.util.LinkedHashSet<>();
        LinkedHashMap<String, java.util.LinkedHashSet<String>> topicHierarchy = new LinkedHashMap<>();
        for (Map<String, Object> d : groupDocs) {
            sumIn  += asLong(d.get(AgentQueryRecord.F_INPUT_TOKENS));
            sumOut += asLong(d.get(AgentQueryRecord.F_OUTPUT_TOKENS));
            if (Boolean.TRUE.equals(d.get(AgentQueryRecord.F_GUARDRAIL_VIOLATED))) hasGuardrail = true;
            String policy = strVal(d.get(AgentQueryRecord.F_GUARDRAIL_POLICY));
            if (!policy.isEmpty()) guardrailPolicies.add(policy);
            String topic = strVal(d.get(AgentQueryRecord.F_TOPIC));
            if (!topic.isEmpty()) {
                java.util.LinkedHashSet<String> subs = topicHierarchy.computeIfAbsent(topic, k -> new java.util.LinkedHashSet<>());
                String subTopic = strVal(d.get(AgentQueryRecord.F_SUB_TOPIC));
                if (!subTopic.isEmpty()) subs.add(subTopic);
            }
        }

        Map<String, Object> row = new HashMap<>();
        if (effectiveTraceId != null && !effectiveTraceId.isEmpty()) row.put(AgentQueryRecord.F_TRACE_ID, effectiveTraceId);
        row.put(KEY_SPAN_COUNT,   (long) groupDocs.size());
        row.put(KEY_LATEST_TS,    latestTs);
        row.put(KEY_FIRST_TS,     firstTs);
        row.put(KEY_DURATION_MS,  latestTs > firstTs ? latestTs - firstTs : 0);
        row.put(AgentQueryRecord.F_INPUT_TOKENS,  sumIn);
        row.put(AgentQueryRecord.F_OUTPUT_TOKENS, sumOut);
        row.put(KEY_TOTAL_TOKENS, sumIn + sumOut);
        row.put(KEY_HAS_ACTIVE_GUARDRAIL, hasGuardrail);
        row.put(KEY_GUARDRAIL_POLICIES, new ArrayList<>(guardrailPolicies));
        row.put(AgentQueryRecord.F_QUERY_PAYLOAD,      first.get(AgentQueryRecord.F_QUERY_PAYLOAD));
        row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD,   first.get(AgentQueryRecord.F_RESPONSE_PAYLOAD));
        row.put(AgentQueryRecord.F_SERVICE_ID,         first.get(AgentQueryRecord.F_SERVICE_ID));
        row.put(AgentQueryRecord.F_USER_NAME,          first.get(AgentQueryRecord.F_USER_NAME));
        row.put(AgentQueryRecord.F_DEVICE_ID,          first.get(AgentQueryRecord.F_DEVICE_ID));
        row.put(AgentQueryRecord.F_SESSION_IDENTIFIER, first.get(AgentQueryRecord.F_SESSION_IDENTIFIER));
        if (!topicHierarchy.isEmpty()) {
            Map<String, Object> hierarchyOut = new LinkedHashMap<>();
            for (Map.Entry<String, java.util.LinkedHashSet<String>> e : topicHierarchy.entrySet())
                hierarchyOut.put(e.getKey(), new ArrayList<>(e.getValue()));
            row.put(KEY_TOPIC_HIERARCHY, hierarchyOut);
        }
        return row;
    }

    private static String strVal(Object v) {
        return v != null ? v.toString() : "";
    }

    private static long asLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    // ── Session-level aggregated stats (accurate cardinality + token sums) ──────

    @Override
    public SessionAggStats fetchSessionAggStats(int accountId, long startMs, long endMs,
                                                 Map<String, List<String>> filters, Boolean atlasTrafficFilter) {
        long aggTotalSessions = 0, aggInputTokens = 0, aggOutputTokens = 0;
        List<Map<String, Object>> aggTopUsers = new ArrayList<>();
        List<Map<String, Object>> aggTopModels = new ArrayList<>();
        List<Map<String, Object>> aggUserBreakdown = new ArrayList<>();
        List<Long> aggSessionSpark = new ArrayList<>();
        List<Long> aggSessionSparkTs = new ArrayList<>();
        List<Long> aggSessionTokenSpark = new ArrayList<>();

        if (!isConfigured()) {
            return new SessionAggStats(0, 0, 0, aggTopUsers, aggTopModels, aggUserBreakdown,
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
                .put(AGG_TOP_MODELS, sessionModelSampleAgg())
                .put(AGG_USER_BREAKDOWN, new JSONObject()
                    .put("terms", new JSONObject()
                        .put("field", AgentQueryRecord.F_USER_NAME_KW)
                        .put("size", USER_BREAKDOWN_SIZE)
                        // Rank buckets by the cardinality sub-agg (unique sessions), not the
                        // default doc_count (messages) — otherwise a chatty low-session user
                        // could bump a genuinely higher-session user out of the top N.
                        .put("order", new JSONObject().put(AGG_TOTAL_SESSIONS, "desc")))
                    // Cardinality sub-agg so the breakdown reflects unique sessions per user,
                    // not raw message/doc count (which double-counts multi-message sessions).
                    .put("aggs", new JSONObject()
                        .put(AGG_TOTAL_SESSIONS, new JSONObject()
                            .put("cardinality", new JSONObject().put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)))))
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
                return new SessionAggStats(0, 0, 0, aggTopUsers, aggTopModels, aggUserBreakdown,
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
            aggTopModels.addAll(parseTopModels(aggsResult));
            aggUserBreakdown.addAll(parseBreakdown(aggsResult, AGG_USER_BREAKDOWN, USER_BREAKDOWN_SIZE, AGG_TOTAL_SESSIONS));

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
            aggTopUsers, aggTopModels, aggUserBreakdown, aggSessionSpark, aggSessionSparkTs, aggSessionTokenSpark);
    }

    // ── Argus aggregated stats (total spans + token sums + top apps/traces + sparklines) ──

    @Override
    public ArgusStats fetchArgusStats(int accountId, long startMs, long endMs, Boolean atlasTrafficFilter, boolean includeTracesContent) {
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

            JSONArray traceHitSource = new JSONArray();
            if (includeTracesContent) {
                traceHitSource.put(AgentQueryRecord.F_QUERY_PAYLOAD).put(AgentQueryRecord.F_RESPONSE_PAYLOAD);
            }
            traceHitSource.put(AgentQueryRecord.F_SERVICE_ID).put(AgentQueryRecord.F_TRACE_ID);

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
                            .put("_source", traceHitSource)))))
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
                        JSONObject src = firstHitSource(b);
                        if (src != null) {
                            row.put(AgentQueryRecord.F_QUERY_PAYLOAD,    src.optString(AgentQueryRecord.F_QUERY_PAYLOAD,    ""));
                            row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD, src.optString(AgentQueryRecord.F_RESPONSE_PAYLOAD, ""));
                            row.put(AgentQueryRecord.F_SERVICE_ID,       src.optString(AgentQueryRecord.F_SERVICE_ID,       ""));
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

    // ── Time-ranged token totals per (serviceId, deviceId) ─────────────────────
    // On-the-fly, date-range-scoped replacement for UserAnalysisDataDao's lifetime counter.
    // Atlas-only (atlasTrafficFilter=true) — the lifetime counter itself has no such split, so
    // "All time" vs. a real range can disagree until that counter/cron gets the same scoping.
    @Override
    public List<UserAnalysisData> fetchUserAnalysisTokenTotals(int accountId, long startMs, long endMs) {
        List<UserAnalysisData> rows = new ArrayList<>();
        if (!isConfigured()) return rows;
        try {
            JSONObject filteredQuery = buildQuery(accountId, startMs, endMs, null, null, Boolean.TRUE);

            JSONObject aggs = new JSONObject()
                .put(AGG_USER_ANALYSIS_SERVICE, new JSONObject()
                    .put("terms", new JSONObject()
                        .put("field", AgentQueryRecord.F_SERVICE_ID_KW)
                        .put("size", USER_ANALYSIS_SERVICE_SIZE))
                    .put("aggs", new JSONObject()
                        .put(AGG_USER_ANALYSIS_DEVICE, new JSONObject()
                            .put("terms", new JSONObject()
                                .put("field", AgentQueryRecord.F_DEVICE_ID_KW)
                                .put("size", USER_ANALYSIS_DEVICE_SIZE))
                            .put("aggs", tokenSubAggs()))));

            JSONObject aggsResult = aggregate(filteredQuery, aggs);
            if (aggsResult == null) return rows;

            JSONObject serviceAgg = aggsResult.optJSONObject(AGG_USER_ANALYSIS_SERVICE);
            JSONArray serviceBuckets = serviceAgg != null ? serviceAgg.optJSONArray("buckets") : null;
            if (serviceBuckets == null) return rows;
            // Unlike this file's "top N" aggs, this method must be exhaustive — warn if the
            // service/device size caps truncated real data.
            long otherServices = serviceAgg.optLong("sum_other_doc_count", 0);
            if (otherServices > 0) {
                logger.error("fetchUserAnalysisTokenTotals: accountId=" + accountId + " truncated "
                    + otherServices + " docs beyond top " + USER_ANALYSIS_SERVICE_SIZE + " serviceIds");
            }

            for (int i = 0; i < serviceBuckets.length(); i++) {
                JSONObject serviceBucket = serviceBuckets.optJSONObject(i);
                if (serviceBucket == null) continue;
                String serviceId = serviceBucket.optString("key", "");

                JSONObject deviceAgg = serviceBucket.optJSONObject(AGG_USER_ANALYSIS_DEVICE);
                JSONArray deviceBuckets = deviceAgg != null ? deviceAgg.optJSONArray("buckets") : null;
                if (deviceBuckets == null) continue;
                long otherDevices = deviceAgg.optLong("sum_other_doc_count", 0);
                if (otherDevices > 0) {
                    logger.error("fetchUserAnalysisTokenTotals: accountId=" + accountId + " serviceId=" + serviceId
                        + " truncated " + otherDevices + " docs beyond top " + USER_ANALYSIS_DEVICE_SIZE + " deviceIds");
                }

                for (int j = 0; j < deviceBuckets.length(); j++) {
                    JSONObject deviceBucket = deviceBuckets.optJSONObject(j);
                    if (deviceBucket == null) continue;
                    String deviceId = deviceBucket.optString("key", "");
                    long in  = subAggLong(deviceBucket, AGG_IN_TOKENS);
                    long out = subAggLong(deviceBucket, AGG_OUT_TOKENS);
                    if (in == 0 && out == 0) continue;

                    UserAnalysisData row = new UserAnalysisData();
                    row.setId(new UserAnalysisDataKey(serviceId, deviceId));
                    row.setTotalInputTokens(in);
                    row.setTotalOutputTokens(out);
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            logger.error("fetchUserAnalysisTokenTotals error for accountId=" + accountId + ": " + e.getMessage());
        }
        return rows;
    }

    // ── Spans for a single message/trace ──────────────────────────────────────

    /**
     * A traceId only ever tags one doc per trace (see fetchMessages/resolveGroups) — everything
     * else the integration logs around it carries no traceId of its own and only belongs to this
     * trace via carry-forward. So a plain traceId.keyword filter here would return just that one
     * tagged doc instead of the whole trace: resolve which session the traceId belongs to first,
     * then re-run the same carry-forward grouping over that session and pick the matching group.
     */
    @Override
    public List<Map<String, Object>> fetchTraceDetail(int accountId, String traceId, Boolean atlasTrafficFilter) {
        List<Map<String, Object>> spans = new ArrayList<>();
        if (!isConfigured() || traceId == null || traceId.trim().isEmpty()) return spans;
        String tid = traceId.trim();
        try {
            String sessionId = resolveSessionForTraceId(accountId, tid, atlasTrafficFilter);
            if (sessionId == null || sessionId.isEmpty()) return spans;

            Map<String, List<String>> sessionFilter = new HashMap<>();
            sessionFilter.put(AgentQueryRecord.F_SESSION_IDENTIFIER_KW, java.util.Collections.singletonList(sessionId));
            JSONObject query = buildQuery(accountId, 0L, Long.MAX_VALUE, sessionFilter, null, atlasTrafficFilter);
            List<Map<String, Object>> docs = fetchRawDocsDescByTime(query, RAW_FETCH_CAP, "fetchTraceDetail", accountId);
            java.util.Collections.reverse(docs); // ascending — carry-forward needs chronological order

            Map<String, String> effectiveTraceIdByGroup = new HashMap<>();
            LinkedHashMap<String, List<Map<String, Object>>> groups = resolveGroups(docs, effectiveTraceIdByGroup);
            List<Map<String, Object>> matched = groups.get("trace:" + sessionId + ":" + tid);
            spans = matched != null ? matched : new ArrayList<>();
            if (spans.size() > TRACE_DETAIL_SIZE) spans = capPreservingGuardrailHits(spans, TRACE_DETAIL_SIZE);
        } catch (Exception e) {
            logger.error("fetchTraceDetail error for accountId=" + accountId + ": " + e.getMessage());
            return new ArrayList<>();
        }
        return spans;
    }

    /**
     * Truncating a huge trace to the display cap by just keeping the earliest N can silently drop
     * the very spans a reviewer opened the trace to look at: a session's guardrail hits can land
     * anywhere in a 1000+-span trace (verified against production data — 8 hits in one trace, all
     * past position 500), so every guardrail-violated span is kept regardless of position, and the
     * cap is only spent on the rest. Re-sorts back to ascending order afterward since the waterfall
     * graph and span list both assume chronological order.
     */
    private static List<Map<String, Object>> capPreservingGuardrailHits(List<Map<String, Object>> spansAsc, int cap) {
        List<Map<String, Object>> violated = new ArrayList<>();
        List<Map<String, Object>> rest = new ArrayList<>();
        for (Map<String, Object> s : spansAsc) {
            (Boolean.TRUE.equals(s.get(AgentQueryRecord.F_GUARDRAIL_VIOLATED)) ? violated : rest).add(s);
        }
        List<Map<String, Object>> kept = new ArrayList<>(violated.size() > cap ? violated.subList(0, cap) : violated);
        int remaining = cap - kept.size();
        if (remaining > 0) kept.addAll(rest.subList(0, Math.min(remaining, rest.size())));
        kept.sort((a, b) -> Long.compare(asLong(a.get(AgentQueryRecord.F_TIMESTAMP)), asLong(b.get(AgentQueryRecord.F_TIMESTAMP))));
        return kept;
    }

    /** Which session a traceId's one tagged doc belongs to, or null if no doc carries it. */
    private String resolveSessionForTraceId(int accountId, String traceId, Boolean atlasTrafficFilter) throws JSONException {
        Map<String, List<String>> filters = new HashMap<>();
        filters.put(AgentQueryRecord.F_TRACE_ID_KW, java.util.Collections.singletonList(traceId));
        JSONObject query = buildQuery(accountId, 0L, Long.MAX_VALUE, filters, null, atlasTrafficFilter);
        JSONObject body = new JSONObject()
            .put("query", query)
            .put("size", 1)
            .put("_source", new JSONArray().put(AgentQueryRecord.F_SESSION_IDENTIFIER));

        JSONObject response = httpPost(trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search", body.toString());
        if (response == null) return null;
        JSONArray hits = extractHits(response);
        if (hits == null || hits.length() == 0) return null;
        JSONObject source = hits.optJSONObject(0) != null ? hits.optJSONObject(0).optJSONObject("_source") : null;
        return source != null ? source.optString(AgentQueryRecord.F_SESSION_IDENTIFIER, null) : null;
    }

    // ── Conversation context window (before/after a message with no session/trace id) ─────────
    // Used by the threat-detection "Session Context" fallback: a flagged event that carries no
    // sessionId of its own, so the only correlation we have is serviceId (host header) + a rough
    // timestamp. Always fetches the nearest before/after docs by proximity (deterministic — this
    // never fails to return *something* as long as the host has nearby traffic), and separately
    // tags each returned turn with how confidently it can be said to share the anchor's
    // conversation: a raw sessionIdentifier match is a free, hard signal when present, but
    // sessionIdentifier is optional per-integration and frequently blank, so turns without it are
    // left tagged RESOLUTION_ORPHAN for the caller to resolve via LLM content classification
    // instead of silently trusting mere host+time proximity as "the same conversation".

    /** fetchContextWindow row-shape key: how a turn's relation to the anchor was resolved. */
    public static final String KEY_RESOLUTION_METHOD    = "resolutionMethod";
    public static final String RESOLUTION_SESSION_MATCH = "session_match";
    public static final String RESOLUTION_ORPHAN        = "orphan";

    // Half-width of the raw band searched around the target timestamp for context-window candidates.
    private static final long CONTEXT_WINDOW_BAND_MS = 2L * 900 * 1000; // +/- 30mins

    public static class ContextWindowResult {
        public final Map<String, Object> anchor;
        public final List<Map<String, Object>> before;
        public final List<Map<String, Object>> after;
        public final boolean configured;

        public ContextWindowResult(Map<String, Object> anchor, List<Map<String, Object>> before,
                                    List<Map<String, Object>> after, boolean configured) {
            this.anchor = anchor;
            this.before = before;
            this.after = after;
            this.configured = configured;
        }

        static ContextWindowResult unconfigured() {
            return new ContextWindowResult(null, new ArrayList<>(), new ArrayList<>(), false);
        }
    }

    public ContextWindowResult fetchContextWindow(int accountId, String serviceId, long targetTsMs,
                                                   int beforeCount, int afterCount, boolean isAtlasTraffic) {
        if (!isConfigured() || serviceId == null || serviceId.trim().isEmpty()) {
            return ContextWindowResult.unconfigured();
        }
        try {
            Map<String, List<String>> filters = new HashMap<>();
            filters.put(AgentQueryRecord.F_SERVICE_ID_KW, java.util.Collections.singletonList(serviceId));

            JSONObject query = buildQuery(accountId,
                Math.max(0, targetTsMs - CONTEXT_WINDOW_BAND_MS), targetTsMs + CONTEXT_WINDOW_BAND_MS,
                filters, null, isAtlasTraffic);
            List<Map<String, Object>> docsDesc = fetchRawDocsDescByTime(query, RAW_FETCH_CAP, "fetchContextWindow", accountId);
            if (docsDesc.isEmpty()) {
                return new ContextWindowResult(null, new ArrayList<>(), new ArrayList<>(), true);
            }
            List<Map<String, Object>> docsAsc = new ArrayList<>(docsDesc);
            java.util.Collections.reverse(docsAsc);

            // Anchor = doc with timestamp closest to targetTsMs (the flagged message itself, when
            // its own detection timestamp lines up with its ES doc within the search band).
            Map<String, Object> anchorDoc = docsAsc.get(0);
            long bestDelta = Math.abs(asLong(anchorDoc.get(AgentQueryRecord.F_TIMESTAMP)) - targetTsMs);
            for (Map<String, Object> d : docsAsc) {
                long delta = Math.abs(asLong(d.get(AgentQueryRecord.F_TIMESTAMP)) - targetTsMs);
                if (delta < bestDelta) { bestDelta = delta; anchorDoc = d; }
            }
            String anchorSessionId = strVal(anchorDoc.get(AgentQueryRecord.F_SESSION_IDENTIFIER));
            long anchorTs = asLong(anchorDoc.get(AgentQueryRecord.F_TIMESTAMP));

            // Partition the rest of the window into: same session as the anchor (deterministic
            // match, kept), a different non-empty session on the same host (a different
            // conversation — excluded, not just noise worth diluting the window with), or orphan
            // (no sessionIdentifier at all — kept, left for the LLM fallback to judge).
            List<Map<String, Object>> beforePool = new ArrayList<>();
            List<Map<String, Object>> afterPool  = new ArrayList<>();
            for (Map<String, Object> d : docsAsc) {
                if (d == anchorDoc) continue;
                String sid = strVal(d.get(AgentQueryRecord.F_SESSION_IDENTIFIER));
                if (!sid.isEmpty() && !sid.equals(anchorSessionId)) continue;
                long ts = asLong(d.get(AgentQueryRecord.F_TIMESTAMP));
                if (ts < anchorTs) beforePool.add(d);
                else if (ts > anchorTs) afterPool.add(d);
            }

            List<Map<String, Object>> before = foldIntoTurns(lastN(beforePool, beforeCount), anchorSessionId);
            List<Map<String, Object>> after  = foldIntoTurns(firstN(afterPool, afterCount), anchorSessionId);
            Map<String, Object> anchorTurn = foldIntoTurns(
                java.util.Collections.singletonList(anchorDoc), anchorSessionId).get(0);

            return new ContextWindowResult(anchorTurn, before, after, true);
        } catch (Exception e) {
            logger.error("fetchContextWindow error for accountId=" + accountId + ": " + e.getMessage());
            return ContextWindowResult.unconfigured();
        }
    }

    /** Folds same-instant/same-trace docs into turn rows via the existing carry-forward grouping
     *  (resolveGroups/buildTraceRowFromGroup — the same logic fetchMessages uses), then tags each
     *  resulting turn with how trustworthy its relation to the anchor's session is. */
    private List<Map<String, Object>> foldIntoTurns(List<Map<String, Object>> docsAsc, String anchorSessionId) {
        if (docsAsc.isEmpty()) return new ArrayList<>();
        Map<String, String> effectiveTraceIdByGroup = new HashMap<>();
        LinkedHashMap<String, List<Map<String, Object>>> groups = resolveGroups(docsAsc, effectiveTraceIdByGroup);

        List<Map<String, Object>> turns = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            Map<String, Object> row = buildTraceRowFromGroup(e.getValue(), effectiveTraceIdByGroup.get(e.getKey()));
            String sid = strVal(row.get(AgentQueryRecord.F_SESSION_IDENTIFIER));
            row.put(KEY_RESOLUTION_METHOD,
                (!sid.isEmpty() && sid.equals(anchorSessionId)) ? RESOLUTION_SESSION_MATCH : RESOLUTION_ORPHAN);
            turns.add(row);
        }
        turns.sort((a, b) -> Long.compare(asLong(a.get(KEY_LATEST_TS)), asLong(b.get(KEY_LATEST_TS))));
        return turns;
    }

    private static List<Map<String, Object>> lastN(List<Map<String, Object>> ascList, int n) {
        int size = ascList.size();
        return n >= size ? new ArrayList<>(ascList) : new ArrayList<>(ascList.subList(size - n, size));
    }

    private static List<Map<String, Object>> firstN(List<Map<String, Object>> ascList, int n) {
        return n >= ascList.size() ? new ArrayList<>(ascList) : new ArrayList<>(ascList.subList(0, n));
    }

    // ── Real-invocation check for known-malicious tool/skill names ─────────────

    @Override
    public List<Map<String, Object>> searchMaliciousComponentInvocations(
            int accountId, List<String> maliciousTermNames, long startMs, long endMs, int limitPerTerm) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!isConfigured() || maliciousTermNames == null || maliciousTermNames.isEmpty()) return results;

        for (String term : maliciousTermNames) {
            if (term == null || term.trim().isEmpty()) continue;
            try {
                JSONObject query = buildQuery(accountId, startMs, endMs, null, null, null);
                JSONArray should = new JSONArray()
                    .put(new JSONObject().put("match_phrase", new JSONObject().put(AgentQueryRecord.F_QUERY_PAYLOAD, term)))
                    .put(new JSONObject().put("match_phrase", new JSONObject().put(AgentQueryRecord.F_RESPONSE_PAYLOAD, term)));
                query.getJSONObject("bool").getJSONArray("must")
                    .put(new JSONObject().put("bool", new JSONObject().put("should", should).put("minimum_should_match", 1)));

                JSONObject body = new JSONObject()
                    .put("query", query)
                    .put("size", limitPerTerm)
                    .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "desc"))))
                    .put("_source", new JSONArray().put(AgentQueryRecord.F_TRACE_ID).put(AgentQueryRecord.F_TIMESTAMP));

                JSONObject response = httpPost(trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search", body.toString());
                if (response == null) continue;

                JSONArray hits = extractHits(response);
                if (hits == null) continue;
                for (int i = 0; i < hits.length(); i++) {
                    JSONObject hit = hits.optJSONObject(i);
                    if (hit == null) continue;
                    JSONObject source = hit.optJSONObject("_source");
                    if (source == null) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put(KEY_TERM, term);
                    row.put(KEY_TRACE_ID, source.optString(AgentQueryRecord.F_TRACE_ID, ""));
                    row.put(KEY_TIMESTAMP, source.optLong(AgentQueryRecord.F_TIMESTAMP, 0L));
                    results.add(row);
                }
            } catch (Exception e) {
                logger.error("searchMaliciousComponentInvocations error for accountId=" + accountId + ", term=" + term + ": " + e.getMessage());
            }
        }
        return results;
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
                .put(AgentQueryRecord.F_SUB_TOPIC, new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_SUB_TOPIC_KW).put("size", 200)))
                .put(AgentQueryRecord.F_GUARDRAIL_POLICY, new JSONObject().put("terms", new JSONObject().put("field", AgentQueryRecord.F_GUARDRAIL_POLICY_KW).put("size", 100)));

            JSONObject aggsResult = aggregate(query, aggs);
            filterChoices.put(AgentQueryRecord.F_USER_NAME,  extractBucketKeys(aggsResult, AgentQueryRecord.F_USER_NAME));
            filterChoices.put(AgentQueryRecord.F_DEVICE_ID,  extractBucketKeys(aggsResult, AgentQueryRecord.F_DEVICE_ID));
            filterChoices.put(AgentQueryRecord.F_SERVICE_ID, extractBucketKeys(aggsResult, AgentQueryRecord.F_SERVICE_ID));
            filterChoices.put("topic",    extractBucketKeys(aggsResult, "topic"));
            filterChoices.put("subTopic", extractBucketKeys(aggsResult, "subTopic"));
            filterChoices.put(AgentQueryRecord.F_GUARDRAIL_POLICY, extractBucketKeys(aggsResult, AgentQueryRecord.F_GUARDRAIL_POLICY));
        } catch (Exception e) {
            return new HashMap<>();
        }
        return filterChoices;
    }

    // ── Paginated flat prompt search ───────────────────────────────────────────

    @Override
    public SearchResult searchPrompts(int accountId, long startMs, long endMs, int skip, int limit,
                                       String sortKey, boolean sortAsc, String searchAfterJson,
                                       Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString,
                                       boolean includeTracesContent) {
        if (!isConfigured()) return new SearchResult(new ArrayList<>(), 0);
        try {
            JSONArray searchAfter = null;
            if (searchAfterJson != null && !searchAfterJson.trim().isEmpty()) {
                try { searchAfter = new JSONArray(searchAfterJson); } catch (Exception ignored) {}
            }
            JSONObject query = buildQuery(accountId, startMs, endMs, filters, searchString, atlasTrafficFilter);
            return executeSearch(query, skip, Math.min(limit, 100), toEsField(sortKey), sortAsc, searchAfter, includeTracesContent);
        } catch (Exception e) {
            logger.error("searchPrompts error for accountId=" + accountId + ": " + e.getMessage());
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    private SearchResult executeSearch(JSONObject query, int skip, int limit, String sortField, boolean sortAsc,
                                        JSONArray searchAfter, boolean includeTracesContent) throws JSONException {
        String sortDir = sortAsc ? "asc" : "desc";
        String resolvedSort = (sortField != null && !sortField.isEmpty()) ? sortField : AgentQueryRecord.F_TIMESTAMP;

        JSONObject body = new JSONObject()
            .put("query", query)
            .put("sort", new JSONArray().put(new JSONObject().put(resolvedSort, new JSONObject().put("order", sortDir))))
            .put("size", limit)
            .put("track_total_hits", true);

        if (!includeTracesContent) {
            body.put("_source", new JSONObject().put("excludes", new JSONArray()
                .put(AgentQueryRecord.F_QUERY_PAYLOAD).put(AgentQueryRecord.F_RESPONSE_PAYLOAD)));
        }

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
                // guardrailViolated is mapped as a boolean field — a "true"/"false" string in a
                // terms clause would be matched against the field's indexed term, not coerced,
                // so it must go in as an actual boolean the same way isAtlasTraffic does below.
                if (AgentQueryRecord.F_GUARDRAIL_VIOLATED.equals(e.getKey())) {
                    for (String v : vals) arr.put(Boolean.parseBoolean(v));
                } else {
                    for (String v : vals) arr.put(v);
                }
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
     * Terms agg over the most recent MODEL_SESSION_SAMPLE sessions, each carrying the earliest
     * document that actually names a model. Only responsePayload is pulled back — the model is
     * parsed out of it in {@link #parseTopModels}.
     */
    private static JSONObject sessionModelSampleAgg() throws JSONException {
        return new JSONObject()
            .put("terms", new JSONObject()
                .put("field", AgentQueryRecord.F_SESSION_IDENTIFIER_KW)
                .put("size", MODEL_SESSION_SAMPLE)
                .put("order", new JSONObject().put(KEY_LATEST_TS, "desc")))
            .put("aggs", new JSONObject()
                .put(KEY_LATEST_TS, new JSONObject().put("max", new JSONObject().put("field", AgentQueryRecord.F_TIMESTAMP)))
                .put(AGG_FIRST_HIT, new JSONObject()
                    .put("filter", new JSONObject().put("match_phrase", new JSONObject()
                        .put(AgentQueryRecord.F_RESPONSE_PAYLOAD, "model")))
                    .put("aggs", new JSONObject().put("hit", new JSONObject().put("top_hits", new JSONObject()
                        .put("size", 1)
                        .put("sort", new JSONArray().put(new JSONObject().put(AgentQueryRecord.F_TIMESTAMP, new JSONObject().put("order", "asc"))))
                        .put("_source", new JSONArray().put(AgentQueryRecord.F_RESPONSE_PAYLOAD)))))));
    }

    /**
     * Tallies unique sessions per model from {@link #sessionModelSampleAgg()} and returns the
     * top TOP_N_MODELS as {KEY_MODEL, KEY_COUNT} rows, highest first.
     */
    private static List<Map<String, Object>> parseTopModels(JSONObject aggsResult) throws JSONException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (aggsResult == null) return out;
        JSONObject agg = aggsResult.optJSONObject(AGG_TOP_MODELS);
        if (agg == null) return out;
        JSONArray buckets = agg.optJSONArray("buckets");
        if (buckets == null) return out;

        Map<String, Long> sessionsByModel = new HashMap<>();
        for (int i = 0; i < buckets.length(); i++) {
            JSONObject b = buckets.optJSONObject(i);
            if (b == null) continue;
            JSONObject src = firstHitSource(b);
            if (src == null) continue;
            String model = extractModel(src.optString(AgentQueryRecord.F_RESPONSE_PAYLOAD, ""));
            if (model.isEmpty()) continue;
            sessionsByModel.merge(model, 1L, Long::sum);
        }

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(sessionsByModel.entrySet());
        ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(TOP_N_MODELS, ranked.size()); i++) {
            Map<String, Object> row = new HashMap<>();
            row.put(KEY_MODEL, ranked.get(i).getKey());
            row.put(KEY_COUNT, ranked.get(i).getValue());
            out.add(row);
        }
        return out;
    }

    /** Reads the model name out of a responsePayload JSON string; "" when absent/unparseable. */
    private static String extractModel(String responsePayload) {
        if (responsePayload == null || responsePayload.isEmpty()) return "";
        try {
            return new JSONObject(responsePayload).optString("model", "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Resolves an AGG_FIRST_HIT sub-agg — either a bare top_hits or one wrapped in a filter agg
     * under "hit" — down to the single top hit's _source. Null when the bucket has no hit.
     */
    private static JSONObject firstHitSource(JSONObject bucket) throws JSONException {
        JSONObject firstHitAgg = bucket.optJSONObject(AGG_FIRST_HIT);
        if (firstHitAgg == null) return null;
        JSONObject hitsRoot = firstHitAgg.optJSONObject("hit");
        if (hitsRoot == null) hitsRoot = firstHitAgg;
        JSONObject hits = hitsRoot.optJSONObject("hits");
        JSONArray topHits = hits != null ? hits.optJSONArray("hits") : null;
        if (topHits == null || topHits.length() == 0) return null;
        return topHits.getJSONObject(0).optJSONObject("_source");
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

    // Extracts up to maxItems {label, count} entries from a terms aggregation, reading count
    // from the named cardinality sub-agg rather than doc_count (which would double-count
    // entities, e.g. sessions, that span multiple documents).
    private static List<Map<String, Object>> parseBreakdown(
            JSONObject aggsResult, String aggName, int maxItems, String cardinalitySubAggName) throws JSONException {
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
            entry.put(KEY_COUNT, subAggLong(b, cardinalitySubAggName));
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
            // No traceId on any doc -> fall back to raw doc count (matches fetchMessages, which
            // can't group untraced spans into an existing trace when the session has none at all,
            // so it shows one row per doc there too).
            long msgCount = subAggLong(bucket, KEY_MSG_COUNT);
            if (msgCount == 0 && bucket.has(KEY_MSG_COUNT)) msgCount = bucket.optLong("doc_count", 0);
            row.put(KEY_MSG_COUNT,                   msgCount);
            row.put(KEY_HAS_ACTIVE_GUARDRAIL,         subAggLong(bucket, KEY_HAS_ACTIVE_GUARDRAIL) > 0);
            row.put(KEY_GUARDRAIL_POLICIES,           extractBucketKeyList(bucket.optJSONObject(AGG_GUARDRAIL_POLICIES)));

            JSONObject src = firstHitSource(bucket);
            if (src != null) {
                row.put(AgentQueryRecord.F_QUERY_PAYLOAD,       src.optString(AgentQueryRecord.F_QUERY_PAYLOAD,       ""));
                row.put(AgentQueryRecord.F_RESPONSE_PAYLOAD,    src.optString(AgentQueryRecord.F_RESPONSE_PAYLOAD,    ""));
                row.put(AgentQueryRecord.F_SERVICE_ID,          src.optString(AgentQueryRecord.F_SERVICE_ID,          ""));
                row.put(AgentQueryRecord.F_USER_NAME,           src.optString(AgentQueryRecord.F_USER_NAME,           ""));
                row.put(AgentQueryRecord.F_DEVICE_ID,           src.optString(AgentQueryRecord.F_DEVICE_ID,           ""));
                row.put(AgentQueryRecord.F_SESSION_IDENTIFIER,  src.optString(AgentQueryRecord.F_SESSION_IDENTIFIER,  ""));
                row.put(AgentQueryRecord.F_TRACE_ID,            src.optString(AgentQueryRecord.F_TRACE_ID,            ""));
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
