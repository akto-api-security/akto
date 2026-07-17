package com.akto.utils.search;

import com.akto.utils.elasticsearch.AgentQueryRecord;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public abstract boolean isConfigured();

    /**
     * Dual-mode session fetch:
     *  - sessionsLimit == 0: top-500-by-latest-activity summary, used by summary cards.
     *  - sessionsLimit  > 0: cursor-paginated (sessionsAfterKey), used by the sessions table.
     */
    public abstract SessionsResult fetchSessions(
        int accountId, long startMs, long endMs, String searchString,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter,
        int sessionsLimit, String sessionsAfterKey);

    public abstract List<Map<String, Object>> fetchMessages(
        int accountId, long startMs, long endMs,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter);

    public abstract SessionAggStats fetchSessionAggStats(
        int accountId, long startMs, long endMs,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter);

    public abstract ArgusStats fetchArgusStats(
        int accountId, long startMs, long endMs, Boolean atlasTrafficFilter);

    public abstract List<Map<String, Object>> fetchTraceDetail(
        int accountId, String traceId, Boolean atlasTrafficFilter);

    public abstract Map<String, List<String>> fetchPromptFilters(
        int accountId, long startMs, long endMs);

    public abstract SearchResult searchPrompts(
        int accountId, long startMs, long endMs, int skip, int limit,
        String sortKey, boolean sortAsc, String searchAfterJson,
        Map<String, List<String>> filters, Boolean atlasTrafficFilter, String searchString);

    /** Used by UserAnalysisCron to fetch not-yet-topic-classified records. */
    public abstract void scrollQueryData(int accountId, long startTsMs, long endTsMs,
        int pageSize, int maxRecords, Consumer<AgentQueryRecord> handler);

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

    public static class SessionAggStats {
        public final long totalSessions;
        public final long inputTokens;
        public final long outputTokens;
        public final List<Map<String, Object>> topUsers;
        public final List<Map<String, Object>> userBreakdown;
        public final List<Long> sessionSpark;
        public final List<Long> sessionSparkTs;
        public final List<Long> sessionTokenSpark;
        public SessionAggStats(long totalSessions, long inputTokens, long outputTokens,
                                List<Map<String, Object>> topUsers, List<Map<String, Object>> userBreakdown,
                                List<Long> sessionSpark, List<Long> sessionSparkTs, List<Long> sessionTokenSpark) {
            this.totalSessions = totalSessions;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.topUsers = topUsers;
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
