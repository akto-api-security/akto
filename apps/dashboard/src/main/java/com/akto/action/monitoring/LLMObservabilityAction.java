package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
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
import java.util.List;
import java.util.Map;

public class LLMObservabilityAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(LLMObservabilityAction.class, LogDb.DASHBOARD);

    @Setter private int          startTime;
    @Setter private int          endTime;
    @Setter private String       searchString;
    @Setter private int          skip        = 0;
    @Setter private int          limit       = 20;
    @Setter private String       sortKey     = "timestamp";
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
    @Setter private List<String> userNames   = new ArrayList<>();
    @Setter private List<String> serviceIds  = new ArrayList<>();
    @Setter private List<String> sessionIds  = new ArrayList<>();

    @Getter private List<Map<String, Object>>  sessions      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  messages      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  prompts       = new ArrayList<>();
    @Getter private List<Map<String, Object>>  spans         = new ArrayList<>();
    @Getter private Map<String, List<String>>  filterChoices = new HashMap<>();
    @Getter private long                       total         = 0;

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
            JSONObject baseQ = es.buildBaseQueryMulti(accountId, startMs(), endMs(), extraFilters.isEmpty() ? null : extraFilters, null);
            JSONArray mustArr = baseQ.getJSONObject("bool").getJSONArray("must");
            mustArr.put(new JSONObject().put("exists", new JSONObject().put("field", "sessionIdentifier")));
            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject().put("must", mustArr));

            JSONObject subAggs = new JSONObject()
                .put("latestTimestamp", new JSONObject().put("max", new JSONObject().put("field", "timestamp")))
                .put("firstTimestamp",  new JSONObject().put("min", new JSONObject().put("field", "timestamp")))
                .put("inTokens",        new JSONObject().put("sum", new JSONObject().put("field", "inputTokens")))
                .put("outTokens",       new JSONObject().put("sum", new JSONObject().put("field", "outputTokens")))
                .put("messageCount",    new JSONObject().put("cardinality", new JSONObject().put("field", "traceId.keyword")))
                .put("firstHit", new JSONObject().put("top_hits", new JSONObject()
                    .put("size", 1)
                    .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))))
                    .put("_source", new JSONArray().put("queryPayload").put("responsePayload")
                        .put("serviceId").put("userName").put("deviceId").put("sessionIdentifier"))));

            if (sessionsLimit > 0) {
                // ── Paginated path: composite aggregation ────────────────────────────
                int pageSize = Math.min(sessionsLimit, 100);
                JSONObject compositeSource = new JSONObject()
                    .put("sessionIdentifier", new JSONObject()
                        .put("terms", new JSONObject().put("field", "sessionIdentifier.keyword")));
                JSONObject composite = new JSONObject()
                    .put("size", pageSize)
                    .put("sources", new JSONArray().put(compositeSource));
                if (sessionsAfterKey != null && !sessionsAfterKey.trim().isEmpty()) {
                    try { composite.put("after", new JSONObject(sessionsAfterKey)); }
                    catch (JSONException ignored) {}
                }
                JSONObject aggs = new JSONObject()
                    .put("groups", new JSONObject().put("composite", composite).put("aggs", subAggs))
                    .put("totalSessions", new JSONObject()
                        .put("cardinality", new JSONObject().put("field", "sessionIdentifier.keyword")));

                JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
                sessions = parseBuckets(aggsResult, "sessionIdentifier");
                sessions.sort((a, b) -> {
                    long ta = a.get("latestTimestamp") instanceof Number ? ((Number) a.get("latestTimestamp")).longValue() : 0L;
                    long tb = b.get("latestTimestamp") instanceof Number ? ((Number) b.get("latestTimestamp")).longValue() : 0L;
                    return Long.compare(tb, ta);
                });
                if (aggsResult != null) {
                    JSONObject groups = aggsResult.optJSONObject("groups");
                    if (groups != null) {
                        JSONObject afterKeyObj = groups.optJSONObject("after_key");
                        JSONArray  buckets     = groups.optJSONArray("buckets");
                        if (afterKeyObj != null && buckets != null && buckets.length() >= pageSize)
                            nextAfterKey = afterKeyObj.toString();
                    }
                    JSONObject totalAgg = aggsResult.optJSONObject("totalSessions");
                    if (totalAgg != null) totalSessions = (long) totalAgg.optDouble("value", 0);
                }
            } else {
                // ── Summary path: terms aggregation, top-500 by latest activity ──────
                JSONObject aggs = new JSONObject().put("groups", new JSONObject()
                    .put("terms", new JSONObject().put("field", "sessionIdentifier.keyword").put("size", 500)
                        .put("order", new JSONObject().put("latestTimestamp", "desc")))
                    .put("aggs", subAggs));
                JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
                sessions = parseBuckets(aggsResult, "sessionIdentifier");
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
            mustArr.put(new JSONObject().put("exists", new JSONObject().put("field", "traceId")));

            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject().put("must", mustArr));

            JSONObject subAggs = new JSONObject()
                .put("latestTimestamp", new JSONObject().put("max", new JSONObject().put("field", "timestamp")))
                .put("firstTimestamp", new JSONObject().put("min", new JSONObject().put("field", "timestamp")))
                .put("inTokens",  new JSONObject().put("sum", new JSONObject().put("field", "inputTokens")))
                .put("outTokens", new JSONObject().put("sum", new JSONObject().put("field", "outputTokens")))
                .put("spanCount", new JSONObject().put("value_count", new JSONObject().put("field", "spanId.keyword")))
                .put("firstHit", new JSONObject().put("top_hits", new JSONObject()
                    .put("size", 1)
                    .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))))
                    .put("_source", new JSONArray().put("queryPayload").put("responsePayload")
                        .put("serviceId").put("userName").put("deviceId").put("sessionIdentifier").put("traceId"))));

            JSONObject aggs = new JSONObject().put("groups", new JSONObject()
                .put("terms", new JSONObject().put("field", "traceId.keyword").put("size", 500)
                    .put("order", new JSONObject().put("latestTimestamp", "desc")))
                .put("aggs", subAggs));

            JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
            messages = parseBuckets(aggsResult, "traceId");
        } catch (Exception e) {
            logger.error("fetchMessages error: " + e.getMessage());
            messages = new ArrayList<>();
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
            filters.put("traceId.keyword", traceId.trim());
            if (CONTEXT_SOURCE.ENDPOINT.equals(Context.contextSource.get()))
                filters.put("isAtlasTraffic", "true");
            JSONObject query = es.buildBaseQuery(accountId, 0L, Long.MAX_VALUE, filters, null);

            JSONObject body = new JSONObject()
                .put("query", query)
                .put("size", 500)
                .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))));

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
                .put("userName",  new JSONObject().put("terms", new JSONObject().put("field", "userName.keyword").put("size", 500)))
                .put("deviceId",  new JSONObject().put("terms", new JSONObject().put("field", "deviceId.keyword").put("size", 500)))
                .put("serviceId", new JSONObject().put("terms", new JSONObject().put("field", "serviceId.keyword").put("size", 500)));

            JSONObject aggsResult = es.aggregate(query, aggs);
            filterChoices = new HashMap<>();
            filterChoices.put("userName",  extractBucketKeys(aggsResult, "userName"));
            filterChoices.put("deviceId",  extractBucketKeys(aggsResult, "deviceId"));
            filterChoices.put("serviceId", extractBucketKeys(aggsResult, "serviceId"));
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

    private List<Map<String, Object>> parseBuckets(JSONObject aggsResult, String keyField) throws JSONException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (aggsResult == null) return result;
        JSONObject groups = aggsResult.optJSONObject("groups");
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
            long inTokens  = subAggLong(bucket, "inTokens");
            long outTokens = subAggLong(bucket, "outTokens");
            long latest    = subAggLong(bucket, "latestTimestamp");
            long first     = subAggLong(bucket, "firstTimestamp");
            row.put(keyField, keyValue);
            row.put("spanCount",       bucket.optLong("doc_count", 0));
            row.put("latestTimestamp", latest);
            row.put("firstTimestamp",  first);
            row.put("durationMs",      latest > first ? latest - first : 0);
            row.put("inputTokens",     inTokens);
            row.put("outputTokens",    outTokens);
            row.put("totalTokens",     inTokens + outTokens);
            row.put("messageCount",    subAggLong(bucket, "messageCount"));

            JSONObject firstHitAgg = bucket.optJSONObject("firstHit");
            if (firstHitAgg != null) {
                JSONArray topHits = firstHitAgg.optJSONObject("hits") != null
                    ? firstHitAgg.getJSONObject("hits").optJSONArray("hits") : null;
                if (topHits != null && topHits.length() > 0) {
                    JSONObject src = topHits.getJSONObject(0).optJSONObject("_source");
                    if (src != null) {
                        row.put("queryPayload",      src.optString("queryPayload", ""));
                        row.put("responsePayload",   src.optString("responsePayload", ""));
                        row.put("serviceId",         src.optString("serviceId", ""));
                        row.put("userName",          src.optString("userName", ""));
                        row.put("deviceId",          src.optString("deviceId", ""));
                        row.put("sessionIdentifier", src.optString("sessionIdentifier", ""));
                        row.put("traceId",           src.optString("traceId", ""));
                    }
                }
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
        List<String> out = new ArrayList<>();
        if (aggsResult == null) return out;
        JSONObject agg = aggsResult.optJSONObject(field);
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
        if (frontendKey == null) return "timestamp";
        switch (frontendKey) {
            case "timeStampMs":
            case "timestamp":  return "timestamp";
            case "userName":   return "userName.keyword";
            case "serviceId":  return "serviceId.keyword";
            default:           return "timestamp";
        }
    }

    /** Merges single-value fields and multi-value lists into one Map for buildBaseQueryMulti. */
    private Map<String, List<String>> buildMultiFilters(boolean includeSession) {
        Map<String, List<String>> f = new HashMap<>();
        // Session filter: prefer multi-value list, fall back to single field
        if (includeSession) {
            List<String> sessions = nonEmpty(sessionIds);
            if (sessions.isEmpty() && sessionId != null && !sessionId.trim().isEmpty())
                sessions = java.util.Collections.singletonList(sessionId.trim());
            if (!sessions.isEmpty()) f.put("sessionIdentifier.keyword", sessions);
        }
        // userName
        List<String> users = nonEmpty(userNames);
        if (users.isEmpty() && userName != null && !userName.trim().isEmpty())
            users = java.util.Collections.singletonList(userName.trim());
        if (!users.isEmpty()) f.put("userName.keyword", users);
        // serviceId
        List<String> services = nonEmpty(serviceIds);
        if (services.isEmpty() && serviceId != null && !serviceId.trim().isEmpty())
            services = java.util.Collections.singletonList(serviceId.trim());
        if (!services.isEmpty()) f.put("serviceId.keyword", services);
        // deviceId (single-value only; no ag-grid filter for this field)
        if (deviceId != null && !deviceId.trim().isEmpty())
            f.put("deviceId.keyword", java.util.Collections.singletonList(deviceId.trim()));
        // traceId — used when scoping Messages tab to a specific trace
        if (traceId != null && !traceId.trim().isEmpty())
            f.put("traceId.keyword", java.util.Collections.singletonList(traceId.trim()));
        // Atlas traffic filter: ENDPOINT context only shows Atlas-sourced records
        if (CONTEXT_SOURCE.ENDPOINT.equals(Context.contextSource.get()))
            f.put("isAtlasTraffic", java.util.Collections.singletonList("true"));
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
