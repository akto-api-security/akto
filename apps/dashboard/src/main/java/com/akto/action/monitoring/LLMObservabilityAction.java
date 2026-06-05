package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
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

    @Setter private int    startTime;
    @Setter private int    endTime;
    @Setter private String searchString;
    @Setter private int    skip        = 0;
    @Setter private int    limit       = 20;
    @Setter private String sortKey     = "timestamp";
    @Setter private int    sortOrder   = 1;
    @Setter private String sessionId;
    @Setter private String userName;
    @Setter private String serviceId;
    @Setter private String traceId;
    @Setter private String searchAfterJson;

    @Getter private List<Map<String, Object>>  sessions      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  messages      = new ArrayList<>();
    @Getter private List<Map<String, Object>>  prompts       = new ArrayList<>();
    @Getter private List<Map<String, Object>>  spans         = new ArrayList<>();
    @Getter private Map<String, List<String>>  filterChoices = new HashMap<>();
    @Getter private long                       total         = 0;

    private long startMs() { return (long) startTime * 1000L; }
    private long endMs()   { return (long) endTime   * 1000L; }

    // ── Per-session view ──────────────────────────────────────────────────────

    public String fetchSessions() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject()
                .put("must", es.buildBaseQuery(accountId, startMs(), endMs(), null, null).getJSONObject("bool").getJSONArray("must"))
                .put("must_not", new JSONArray().put(new JSONObject().put("term", new JSONObject().put("sessionIdentifier.keyword", "")))));

            JSONObject subAggs = new JSONObject()
                .put("latestTimestamp", new JSONObject().put("max", new JSONObject().put("field", "timestamp")))
                .put("inTokens",  new JSONObject().put("sum", new JSONObject().put("field", "inputTokens")))
                .put("outTokens", new JSONObject().put("sum", new JSONObject().put("field", "outputTokens")))
                .put("messageCount", new JSONObject().put("cardinality", new JSONObject().put("field", "traceId.keyword")))
                .put("firstHit", new JSONObject().put("top_hits", new JSONObject()
                    .put("size", 1)
                    .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))))
                    .put("_source", new JSONArray().put("queryPayload").put("serviceId").put("userName").put("sessionIdentifier"))));

            JSONObject aggs = new JSONObject().put("groups", new JSONObject()
                .put("terms", new JSONObject().put("field", "sessionIdentifier.keyword").put("size", 200)
                    .put("order", new JSONObject().put("latestTimestamp", "desc")))
                .put("aggs", subAggs));

            JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
            sessions = parseBuckets(aggsResult, "sessionIdentifier");
        } catch (Exception e) {
            sessions = new ArrayList<>();
        }
        return Action.SUCCESS.toUpperCase();
    }

    // ── Per-message view (grouped by traceId) ─────────────────────────────────

    public String fetchMessages() {
        try {
            ElasticSearchClient es = ElasticSearchClient.instance();
            if (!es.isConfigured()) return Action.SUCCESS.toUpperCase();
            int accountId = Context.accountId.get();

            Map<String, String> extraFilters = null;
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                extraFilters = new HashMap<>();
                extraFilters.put("sessionIdentifier.keyword", sessionId.trim());
            }
            JSONObject baseQ = es.buildBaseQuery(accountId, startMs(), endMs(), extraFilters, null);

            JSONObject filteredQuery = new JSONObject().put("bool", new JSONObject()
                .put("must", baseQ.getJSONObject("bool").getJSONArray("must"))
                .put("must_not", new JSONArray().put(new JSONObject().put("term", new JSONObject().put("traceId.keyword", "")))));

            JSONObject subAggs = new JSONObject()
                .put("latestTimestamp", new JSONObject().put("max", new JSONObject().put("field", "timestamp")))
                .put("inTokens",  new JSONObject().put("sum", new JSONObject().put("field", "inputTokens")))
                .put("outTokens", new JSONObject().put("sum", new JSONObject().put("field", "outputTokens")))
                .put("spanCount", new JSONObject().put("value_count", new JSONObject().put("field", "_id")))
                .put("firstHit", new JSONObject().put("top_hits", new JSONObject()
                    .put("size", 1)
                    .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))))
                    .put("_source", new JSONArray().put("queryPayload").put("responsePayload")
                        .put("serviceId").put("userName").put("sessionIdentifier").put("traceId"))));

            JSONObject aggs = new JSONObject().put("groups", new JSONObject()
                .put("terms", new JSONObject().put("field", "traceId.keyword").put("size", 500)
                    .put("order", new JSONObject().put("latestTimestamp", "desc")))
                .put("aggs", subAggs));

            JSONObject aggsResult = es.aggregate(filteredQuery, aggs);
            messages = parseBuckets(aggsResult, "traceId");
        } catch (Exception e) {
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
                .put("serviceId", new JSONObject().put("terms", new JSONObject().put("field", "serviceId.keyword").put("size", 500)));

            JSONObject aggsResult = es.aggregate(query, aggs);
            filterChoices = new HashMap<>();
            filterChoices.put("userName",  extractBucketKeys(aggsResult, "userName"));
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

            Map<String, String> filters = new HashMap<>();
            if (sessionId != null && !sessionId.trim().isEmpty()) filters.put("sessionIdentifier.keyword", sessionId.trim());
            if (userName  != null && !userName.trim().isEmpty())  filters.put("userName.keyword", userName.trim());
            if (serviceId != null && !serviceId.trim().isEmpty()) filters.put("serviceId.keyword", serviceId.trim());

            JSONArray searchAfter = null;
            if (searchAfterJson != null && !searchAfterJson.trim().isEmpty()) {
                try { searchAfter = new JSONArray(searchAfterJson); } catch (Exception ignored) {}
            }

            SearchResult result = es.search(
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
            Map<String, Object> row = new HashMap<>();
            row.put(keyField, bucket.optString("key", ""));
            row.put("spanCount",       bucket.optLong("doc_count", 0));
            row.put("latestTimestamp", subAggLong(bucket, "latestTimestamp"));
            row.put("totalTokens",     subAggLong(bucket, "inTokens") + subAggLong(bucket, "outTokens"));
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

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
