package com.akto.utils.search;

import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Backend-agnostic contract for the agent-query search/aggregation feature. Each fetch/search
 * method is semantic (one per actual dashboard need), not a raw query-DSL passthrough, so each
 * implementation (Elasticsearch, Azure Data Explorer) is free to use whatever internal strategy
 * is fastest/exact for its own engine.
 */
public abstract class SearchClient {

    // ── Shared row-shape keys ────────────────────────────────────────────────────
    // Both backends must produce identical Map<String,Object> row shapes for the same
    // request, so the key names themselves are part of the shared contract, not something
    // either implementation should redeclare on its own.
    protected static final String KEY_SPAN_COUNT      = "spanCount";
    protected static final String KEY_LATEST_TS       = "latestTimestamp";
    protected static final String KEY_FIRST_TS        = "firstTimestamp";
    protected static final String KEY_DURATION_MS     = "durationMs";
    protected static final String KEY_TOTAL_TOKENS    = "totalTokens";
    protected static final String KEY_MSG_COUNT       = "messageCount";
    protected static final String KEY_TOPIC_HIERARCHY = "topicHierarchy";
    protected static final String KEY_LABEL           = "label";
    protected static final String KEY_COUNT           = "count";
    protected static final String KEY_MODEL           = "model";
    protected static final String KEY_TERM            = "term";
    protected static final String KEY_TRACE_ID        = "traceId";
    protected static final String KEY_TIMESTAMP       = "timestamp";
    protected static final String KEY_HAS_ACTIVE_GUARDRAIL = "hasActiveGuardrail";
    protected static final String KEY_GUARDRAIL_POLICIES   = "guardrailPolicies";

    // Which half of the unified view a row came from. Inferable from sessionIdentifier, but
    // stated outright so the frontend branches on intent rather than on an absent field.
    protected static final String KEY_ROW_KIND      = "_rowKind";
    public    static final String ROW_KIND_SESSION  = "session";
    public    static final String ROW_KIND_TRACE    = "trace";

    /**
     * Deepest merged page the unified view will serve. Interleaving two streams means
     * over-fetching both to the requested depth, so depth has to be bounded somewhere;
     * at the default page size this is fifty pages.
     */
    private static final int MERGE_WINDOW_MAX = 1000;

    /**
     * Runs the session half of the unified view while the calling thread runs the other half,
     * so the request costs the slower of the two queries rather than their sum. Bounded and
     * daemon: at most this many session queries are in flight at once (extra ones queue), and
     * the pool never keeps the JVM alive.
     */
    private static final ExecutorService UNIFIED_FETCH_POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "unified-traces-fetch");
        t.setDaemon(true);
        return t;
    });

    public abstract boolean isConfigured();

    /**
     * Dual-mode session fetch:
     *  - sessionsLimit == 0: top-500-by-latest-activity summary, used by summary cards.
     *  - sessionsLimit  > 0: cursor-paginated (sessionsAfterKey), used by the sessions table.
     */
    public abstract SessionsResult fetchSessions(
        int accountId, long startMs, long endMs, String searchString,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter,
        int sessionsLimit, String sessionsAfterKey, boolean includeTracesContent);

    public abstract List<Map<String, Object>> fetchMessages(
        int accountId, long startMs, long endMs,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter);

    public abstract SessionAggStats fetchSessionAggStats(
        int accountId, long startMs, long endMs,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter);

    public ArgusStats fetchArgusStats(
        int accountId, long startMs, long endMs, Boolean atlasTrafficFilter, boolean includeTracesContent) {
        return fetchArgusStats(accountId, startMs, endMs, null, atlasTrafficFilter, includeTracesContent);
    }

    /** Same as above, narrowed by field filters (e.g. serviceId) - null for no filters. */
    public abstract ArgusStats fetchArgusStats(
        int accountId, long startMs, long endMs, Map<String, List<String>> filters,
        Boolean atlasTrafficFilter, boolean includeTracesContent);

    /**
     * Time-ranged replacement for UserAnalysisDataDao's lifetime-total read — sums input/output
     * tokens per (serviceId, deviceId) within [startMs, endMs). Only id/totalInputTokens/
     * totalOutputTokens are populated; topic/summary fields are cron-computed elsewhere.
     */
    public abstract List<UserAnalysisData> fetchUserAnalysisTokenTotals(
        int accountId, long startMs, long endMs);

    /**
     * Argus (AGENTIC) posture read: a per-serviceId (not per-device) rollup for the dashboard's
     * own window — token totals plus a real topic/subTopic breakdown straight off the indexed
     * AgentQueryRecord fields (the ones UserAnalysisCron writes back via bulkUpdateTopics).
     * Deliberately NOT the Mongo-materialized "harmful topic" classification
     * (UserAnalysisData#getHarmfulTopics) — that's an LLM-computed, cron-only rollup with no live
     * ES/ADX equivalent; see service/posture/CLAUDE.md's Argus section for why AGENTIC
     * observability uses topic concentration instead of a harmful-topic signal.
     * atlasTrafficFilter=false at the call site — Argus/agentic traffic is exactly the "not
     * Atlas" half of AgentQueryRecord's isAtlasTraffic split. Only id.serviceId (deviceId left
     * blank — this is a per-service rollup)/totalInputTokens/totalOutputTokens/topicHierarchy are
     * populated; harmfulTopics/aiSummary are always empty.
     */
    public abstract List<UserAnalysisData> fetchAgenticServiceObservability(
        int accountId, long startMs, long endMs, int topicsPerServiceCap);

    /**
     * Argus (AGENTIC) posture read: the account-wide (not per-service) topic/subTopic hierarchy
     * for the dashboard's own window — the top `topicsCap` topics overall, each with its top
     * `subTopicsCap` subtopics, straight off the indexed AgentQueryRecord fields. Same
     * atlasTrafficFilter=false rationale as fetchAgenticServiceObservability above (Argus/agentic
     * traffic is the "not Atlas" half of isAtlasTraffic). Returned map shape matches
     * UserAnalysisData#topicHierarchy exactly (domain -> subDomain -> doc_count), insertion order
     * = descending count, so no separate DTO is needed for this global rollup.
     */
    public abstract Map<String, Map<String, Integer>> fetchAgenticGlobalTopicHierarchy(
        int accountId, long startMs, long endMs, int topicsCap, int subTopicsCap);

    public abstract List<Map<String, Object>> fetchTraceDetail(
        int accountId, String traceId, Boolean atlasTrafficFilter);

    public abstract Map<String, List<String>> fetchPromptFilters(
        int accountId, long startMs, long endMs);

    public abstract SearchResult searchPrompts(
        int accountId, long startMs, long endMs, int skip, int limit,
        String sortKey, boolean sortAsc, String searchAfterJson,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString,
        boolean includeTracesContent);

    /**
     * As above, but able to return only the records that carry no session.
     *
     * A session-grouped view can only show traffic that has a session identifier, so the flat
     * list exists to surface the rest. Returning everything there would list the grouped records
     * in both places; this lets the two views partition the data instead of overlapping.
     *
     * Defaults to ignoring the flag, so a backend that has not implemented it keeps behaving
     * exactly as before rather than failing.
     */
    public SearchResult searchPrompts(
            int accountId, long startMs, long endMs, int skip, int limit,
            String sortKey, boolean sortAsc, String searchAfterJson,
            Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString,
            boolean includeTracesContent, boolean onlyWithoutSession) {
        return searchPrompts(accountId, startMs, endMs, skip, limit, sortKey, sortAsc,
                searchAfterJson, filters, atlasTrafficFilter, searchString, includeTracesContent);
    }

    // ── Unified view: conversations and lone invocations in one list ──────────

    /**
     * One time-ordered list in which a conversation is a single row and an invocation that
     * carries no session is a row of its own.
     *
     * Built from the two queries that already exist rather than a third: {@link #fetchSessions}
     * supplies the grouped half, {@link #searchPrompts} with onlyWithoutSession supplies the
     * rest. Those halves partition the data by construction, so no record can appear twice.
     *
     * Both arrive sorted by time descending, and time is the only key they share - a session's
     * latest activity and an invocation's own timestamp are comparable, its user or application
     * is not aggregated the same way on both sides. Hence a straight merge here, and hence a
     * view that can only be ordered by time.
     */
    public UnifiedResult fetchSessionsAndTraces(
            int accountId, long startMs, long endMs, String searchString,
            Map<String, List<String>> filters, Boolean atlasTrafficFilter,
            int skip, int limit, boolean includeTracesContent) {

        int from   = Math.max(skip, 0);
        int size   = Math.max(limit, 1);
        int window = Math.min(from + size, MERGE_WINDOW_MAX);

        // The two halves are independent queries, so they run side by side.
        Future<SessionsResult> groupedFuture = UNIFIED_FETCH_POOL.submit(() ->
                fetchSessions(accountId, startMs, endMs, searchString,
                        filters, atlasTrafficFilter, window, "0", includeTracesContent));

        SearchResult loose = searchPrompts(accountId, startMs, endMs, 0, window,
                AgentQueryRecord.F_TIMESTAMP, false, null,
                filters, atlasTrafficFilter, searchString, includeTracesContent, true);

        SessionsResult grouped;
        try {
            grouped = groupedFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            grouped = null;
        } catch (Exception e) {
            // Same outcome as fetchSessions failing on its own: the list still shows the
            // records without a session rather than failing the whole request.
            grouped = null;
        }

        List<Map<String, Object>> sessions = (grouped != null && grouped.sessions != null)
                ? grouped.sessions : new ArrayList<>();
        // onlyWithoutSession is a request, not a guarantee: the base implementation ignores it,
        // so a backend that has not implemented it answers with everything. Dropping sessioned
        // records here makes the two halves disjoint whatever the backend did, rather than
        // listing a conversation's records both inside its session row and beside it.
        List<Map<String, Object>> traces = new ArrayList<>();
        if (loose != null && loose.hits != null) {
            for (Map<String, Object> row : loose.hits) {
                if (!hasSession(row)) traces.add(row);
            }
        }

        for (Map<String, Object> row : sessions) row.put(KEY_ROW_KIND, ROW_KIND_SESSION);
        for (Map<String, Object> row : traces)   normalizeLooseRow(row);

        List<Map<String, Object>> merged = mergeByTimeDesc(sessions, traces);
        int sliceFrom = Math.min(from, merged.size());
        int sliceTo   = Math.min(sliceFrom + size, merged.size());

        long total = (grouped != null ? grouped.totalSessions : 0)
                   + (loose != null ? loose.total : 0);
        return new UnifiedResult(new ArrayList<>(merged.subList(sliceFrom, sliceTo)), total);
    }

    private static boolean hasSession(Map<String, Object> row) {
        Object sid = row.get(AgentQueryRecord.F_SESSION_IDENTIFIER);
        return sid instanceof String && !((String) sid).isEmpty();
    }

    /**
     * Gives a lone invocation the few aggregate fields a session row gets, so one set of
     * columns can render both. Each is the honest single-record answer rather than a blank:
     * one invocation is one trace, and its own guardrail verdict is the only one it has.
     */
    private static void normalizeLooseRow(Map<String, Object> row) {
        row.put(KEY_ROW_KIND, ROW_KIND_TRACE);
        row.put(KEY_MSG_COUNT, 1L);
        row.put(KEY_HAS_ACTIVE_GUARDRAIL,
                Boolean.TRUE.equals(row.get(AgentQueryRecord.F_GUARDRAIL_VIOLATED)));
        Object policy = row.get(AgentQueryRecord.F_GUARDRAIL_POLICY);
        List<Object> policies = new ArrayList<>();
        if (policy instanceof String && !((String) policy).isEmpty()) policies.add(policy);
        row.put(KEY_GUARDRAIL_POLICIES, policies);
    }

    /** Two-way merge on the only key both halves are ordered by. */
    private static List<Map<String, Object>> mergeByTimeDesc(
            List<Map<String, Object>> a, List<Map<String, Object>> b) {
        List<Map<String, Object>> merged = new ArrayList<>(a.size() + b.size());
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            merged.add(rowTimeMs(a.get(i)) >= rowTimeMs(b.get(j)) ? a.get(i++) : b.get(j++));
        }
        while (i < a.size()) merged.add(a.get(i++));
        while (j < b.size()) merged.add(b.get(j++));
        return merged;
    }

    /** A session row carries its bucket's latest activity; a flat row carries its own time. */
    private static long rowTimeMs(Map<String, Object> row) {
        Object v = row.get(KEY_LATEST_TS);
        if (!(v instanceof Number)) v = row.get(AgentQueryRecord.F_TIMESTAMP);
        return (v instanceof Number) ? ((Number) v).longValue() : 0L;
    }

    /** Used by UserAnalysisCron to fetch not-yet-topic-classified records. */
    public abstract void scrollQueryData(int accountId, long startTsMs, long endTsMs,
        int pageSize, int maxRecords, Consumer<AgentQueryRecord> handler);

    /**
     * Real-invocation evidence for a small set of known-malicious tool/skill names — proves
     * a component was actually called, not just registered/tagged. One bounded, recency-sorted
     * text search per term against queryPayload/responsePayload, scoped to accountId + the time
     * window. A hit does not distinguish which of queryPayload/responsePayload matched, or
     * whether the term appeared as the actual tool name vs. incidental text — it is evidence of
     * likely invocation, not a guarantee. Row shape: {KEY_TERM, KEY_TRACE_ID, KEY_TIMESTAMP}.
     */
    public abstract List<Map<String, Object>> searchMaliciousComponentInvocations(
        int accountId, List<String> maliciousTermNames, long startMs, long endMs, int limitPerTerm);

    /** Used by UserAnalysisCron to write classified topic/subTopic back to the backend. */
    public abstract void bulkUpdateTopics(List<TopicUpdate> updates);

    // ── Shared, backend-agnostic helpers ───────────────────────────────────────

    /**
     * Mirrors GroupByTimeRange.groupByAllRange logic for sparkline bucketing.
     * ≤ 12 days → fine-grained (actual range / 12, min 1 h)
     * ≤ 84 days → weekly (7 d)
     * > 84 days → monthly (30 d)
     * Returns interval in milliseconds; caller computes histStart = endMs - 12*interval.
     */
    protected static long sparklineIntervalMs(long startMs, long endMs) {
        long daysBetween = (endMs - startMs) / (24 * 3600 * 1000L);
        if (daysBetween <= 12) {
            return Math.max(3_600_000L, (endMs - startMs) / 12);
        } else if (daysBetween <= 84) {
            return 7L * 24 * 3600 * 1000;   // 1 week
        } else {
            return 30L * 24 * 3600 * 1000;  // ~1 month
        }
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> jsonObjectToMap(JSONObject obj) {
        Map<String, Object> map = new HashMap<>();
        java.util.Iterator<String> keys = (java.util.Iterator<String>) obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try { map.put(key, obj.get(key)); } catch (JSONException ignored) {}
        }
        return map;
    }

    // ── Shared DTOs ─────────────────────────────────────────────────────────────

    public static class TopicUpdate {
        public final String docId;
        public final String topic;
        public final String subTopic;
        /** The record's own event timestamp (not classification time) — the ADX backend needs
         *  this to populate its Mongo topic-mapping's mandatory timestamp field, which keeps
         *  the topic-filter-by-time-window lookup correctly time-bounded. */
        public final long timestampMs;
        public TopicUpdate(String docId, String topic, String subTopic, long timestampMs) {
            this.docId       = docId;
            this.topic       = topic;
            this.subTopic    = subTopic != null ? subTopic : "";
            this.timestampMs = timestampMs;
        }
    }

    public static class SearchResult {
        public final List<Map<String, Object>> hits;
        public final long total;
        public SearchResult(List<Map<String, Object>> hits, long total) {
            this.hits = hits;
            this.total = total;
        }
    }

    public static class SessionsResult {
        public final List<Map<String, Object>> sessions;
        public final String nextAfterKey;
        public final long totalSessions;
        public SessionsResult(List<Map<String, Object>> sessions, String nextAfterKey, long totalSessions) {
            this.sessions = sessions;
            this.nextAfterKey = nextAfterKey;
            this.totalSessions = totalSessions;
        }
    }

    /** Rows of both kinds in one time-ordered page, plus the combined count behind them. */
    public static class UnifiedResult {
        public final List<Map<String, Object>> rows;
        public final long total;
        public UnifiedResult(List<Map<String, Object>> rows, long total) {
            this.rows  = rows;
            this.total = total;
        }
    }

    public static class SessionAggStats {
        public final long totalSessions;
        public final long inputTokens;
        public final long outputTokens;
        public final List<Map<String, Object>> topUsers;
        /** {KEY_MODEL, KEY_COUNT} rows — unique sessions per model, highest first. */
        public final List<Map<String, Object>> topModels;
        public final List<Map<String, Object>> userBreakdown;
        public final List<Long> sessionSpark;
        public final List<Long> sessionSparkTs;
        public final List<Long> sessionTokenSpark;
        public SessionAggStats(long totalSessions, long inputTokens, long outputTokens,
                                List<Map<String, Object>> topUsers, List<Map<String, Object>> topModels,
                                List<Map<String, Object>> userBreakdown,
                                List<Long> sessionSpark, List<Long> sessionSparkTs, List<Long> sessionTokenSpark) {
            this.totalSessions = totalSessions;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.topUsers = topUsers;
            this.topModels = topModels;
            this.userBreakdown = userBreakdown;
            this.sessionSpark = sessionSpark;
            this.sessionSparkTs = sessionSparkTs;
            this.sessionTokenSpark = sessionTokenSpark;
        }
    }

    public static class ArgusStats {
        public final long totalSpans;
        public final long inputTokens;
        public final long outputTokens;
        public final List<Map<String, Object>> topApps;
        public final List<Map<String, Object>> appBreakdown;
        public final List<Map<String, Object>> topTraces;
        public final List<Long> traceSpark;
        public final List<Long> tokenSpark;
        public final List<Long> traceSparkTs;
        public ArgusStats(long totalSpans, long inputTokens, long outputTokens,
                           List<Map<String, Object>> topApps, List<Map<String, Object>> appBreakdown,
                           List<Map<String, Object>> topTraces,
                           List<Long> traceSpark, List<Long> tokenSpark, List<Long> traceSparkTs) {
            this.totalSpans = totalSpans;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.topApps = topApps;
            this.appBreakdown = appBreakdown;
            this.topTraces = topTraces;
            this.traceSpark = traceSpark;
            this.tokenSpark = tokenSpark;
            this.traceSparkTs = traceSparkTs;
        }
    }
}
