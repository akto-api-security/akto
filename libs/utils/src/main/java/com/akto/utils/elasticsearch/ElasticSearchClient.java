package com.akto.utils.elasticsearch;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Low-level Elasticsearch client.
 * Owns HTTP transport, query construction, and response parsing.
 * Business-specific aggregations belong in the calling action class.
 */
public class ElasticSearchClient {

    private static final LoggerMaker logger = new LoggerMaker(ElasticSearchClient.class, LogDb.DASHBOARD);

    private static final String ES_HOST  = System.getenv("ES_HOST");
    private static final String ES_API_KEY = System.getenv("ES_API_KEY");
    private static final String ES_INDEX = System.getenv("ES_INDEX_AGENT_QUERY");

    private static final String SCROLL_KEEP_ALIVE = "2m";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");

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

    public boolean isConfigured() {
        return ES_HOST != null && !ES_HOST.isEmpty()
            && ES_INDEX != null && !ES_INDEX.isEmpty();
    }

    public String getIndex() { return ES_INDEX; }

    // ── Topic write-back (used by crons after classification) ─────────────────

    public static class TopicUpdate {
        public final String docId;
        public final String topic;
        public TopicUpdate(String docId, String topic) {
            this.docId = docId;
            this.topic = topic;
        }
    }

    public void bulkUpdateTopics(List<TopicUpdate> updates) {
        if (updates == null || updates.isEmpty() || !isConfigured()) return;
        StringBuilder sb = new StringBuilder();
        for (TopicUpdate u : updates) {
            String safeDocId = u.docId.replace("\\", "\\\\").replace("\"", "\\\"");
            String safeTopic = u.topic.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("{\"update\":{\"_id\":\"").append(safeDocId).append("\"}}\n");
            sb.append("{\"doc\":{\"topic\":\"").append(safeTopic).append("\",\"topicProcessed\":true}}\n");
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

    public void scrollQueryData(int accountId, long startTsMs, long endTsMs, int pageSize,
                                int maxRecords, Consumer<AgentQueryRecord> handler) {
        if (!isConfigured()) return;
        String scrollId = null;
        int delivered = 0;
        try {
            String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search?scroll=" + SCROLL_KEEP_ALIVE;
            JSONObject baseQuery = buildBaseQuery(accountId, startTsMs, endTsMs, null, null);
            // Only fetch records not yet topic-classified
            baseQuery.getJSONObject("bool").put("must_not",
                new JSONArray().put(new JSONObject().put("term",
                    new JSONObject().put("topicProcessed", true))));
            JSONObject response = httpPost(url, baseQuery
                .put("size", pageSize)
                .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))))
                .toString());
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

    // ── Paginated search (used by the LLM observability prompt table) ─────────

    public static class SearchResult {
        public final List<Map<String, Object>> hits;
        public final long total;
        public SearchResult(List<Map<String, Object>> hits, long total) {
            this.hits = hits;
            this.total = total;
        }
    }

    /** Single-value filter search (backward-compat for existing callers). */
    public SearchResult search(int accountId, long startTsMs, long endTsMs,
                               int skip, int limit,
                               String sortField, boolean sortAsc,
                               JSONArray searchAfter,
                               Map<String, String> filters,
                               String searchString) {
        if (!isConfigured()) return new SearchResult(new ArrayList<>(), 0);
        try {
            return executeSearch(buildBaseQuery(accountId, startTsMs, endTsMs, filters, searchString),
                skip, limit, sortField, sortAsc, searchAfter);
        } catch (Exception e) {
            logger.error("search error for accountId=" + accountId + ": " + e.getMessage());
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    /** Multi-value filter search — each filter field supports multiple selected values. */
    public SearchResult searchMulti(int accountId, long startTsMs, long endTsMs,
                                    int skip, int limit,
                                    String sortField, boolean sortAsc,
                                    JSONArray searchAfter,
                                    Map<String, List<String>> filters,
                                    String searchString) {
        if (!isConfigured()) return new SearchResult(new ArrayList<>(), 0);
        try {
            return executeSearch(buildBaseQueryMulti(accountId, startTsMs, endTsMs, filters, searchString),
                skip, limit, sortField, sortAsc, searchAfter);
        } catch (Exception e) {
            logger.error("searchMulti error for accountId=" + accountId + ": " + e.getMessage());
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    private SearchResult executeSearch(JSONObject query, int skip, int limit,
                                       String sortField, boolean sortAsc, JSONArray searchAfter) throws JSONException {
        String sortDir = sortAsc ? "asc" : "desc";
        String resolvedSort = (sortField != null && !sortField.isEmpty()) ? sortField : "timestamp";

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

    // ── Generic aggregation (used by action classes for feature-specific aggs) ─

    /**
     * Fires a size:0 aggregation query and returns the raw aggregations object.
     * Callers (action classes) build the agg body and parse the result themselves.
     */
    public JSONObject aggregate(JSONObject query, JSONObject aggs) {
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

    // ── Query builders (reusable by action classes) ───────────────────────────

    public JSONObject buildBaseQuery(int accountId, long startTsMs, long endTsMs,
                                     Map<String, String> filters, String searchString) throws JSONException {
        JSONArray must = new JSONArray()
            .put(new JSONObject().put("range", new JSONObject()
                .put("timestamp", new JSONObject().put("gte", startTsMs).put("lt", endTsMs))))
            .put(new JSONObject().put("term", new JSONObject().put("accountId", accountId)));

        if (filters != null) {
            for (Map.Entry<String, String> e : filters.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    must.put(new JSONObject().put("term", new JSONObject().put(e.getKey(), e.getValue())));
                }
            }
        }

        applySearchString(must, searchString);

        return new JSONObject().put("bool", new JSONObject().put("must", must));
    }

    /**
     * Multi-value variant: each filter entry emits a {@code terms} clause (OR within a field, AND across fields).
     */
    public JSONObject buildBaseQueryMulti(int accountId, long startTsMs, long endTsMs,
                                          Map<String, List<String>> multiFilters, String searchString) throws JSONException {
        JSONArray must = new JSONArray()
            .put(new JSONObject().put("range", new JSONObject()
                .put("timestamp", new JSONObject().put("gte", startTsMs).put("lt", endTsMs))))
            .put(new JSONObject().put("term", new JSONObject().put("accountId", accountId)));

        if (multiFilters != null) {
            for (Map.Entry<String, List<String>> e : multiFilters.entrySet()) {
                List<String> vals = e.getValue();
                if (vals == null || vals.isEmpty()) continue;
                // Boolean fields must use a term clause with an actual boolean value, not a terms array.
                if (vals.size() == 1 && isBooleanField(e.getKey())) {
                    must.put(new JSONObject().put("term", new JSONObject()
                        .put(e.getKey(), Boolean.parseBoolean(vals.get(0)))));
                } else {
                    JSONArray arr = new JSONArray();
                    for (String v : vals) arr.put(v);
                    must.put(new JSONObject().put("terms", new JSONObject().put(e.getKey(), arr)));
                }
            }
        }

        applySearchString(must, searchString);

        return new JSONObject().put("bool", new JSONObject().put("must", must));
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    private static boolean isBooleanField(String field) {
        return "isAtlasTraffic".equals(field);
    }

    private void applySearchString(JSONArray must, String searchString) throws JSONException {
        if (searchString == null || searchString.isEmpty()) return;
        must.put(new JSONObject().put("bool", new JSONObject()
            .put("should", new JSONArray()
                .put(new JSONObject().put("match_phrase_prefix", new JSONObject().put("queryPayload", searchString)))
                .put(new JSONObject().put("match_phrase_prefix", new JSONObject().put("userName", searchString)))
                .put(new JSONObject().put("match_phrase_prefix", new JSONObject().put("serviceId", searchString))))
            .put("minimum_should_match", 1)));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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
            source.optBoolean("isAtlasTraffic", false)
        );
    }

    public JSONArray extractHits(JSONObject response) {
        JSONObject outer = response.optJSONObject("hits");
        return outer != null ? outer.optJSONArray("hits") : null;
    }

    public JSONObject httpPost(String url, String body) {
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

    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonObjectToMap(JSONObject obj) {
        Map<String, Object> map = new HashMap<>();
        java.util.Iterator<String> keys = (java.util.Iterator<String>) obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try { map.put(key, obj.get(key)); } catch (JSONException ignored) {}
        }
        return map;
    }
}
