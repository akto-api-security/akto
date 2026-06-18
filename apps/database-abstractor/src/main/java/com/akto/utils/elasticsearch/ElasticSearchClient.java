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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ES REST client for agentic query records.
 * Configured by env vars: ES_HOST, ES_USERNAME, ES_PASSWORD, ES_INDEX_AGENT_QUERY.
 * If unset, isConfigured() returns false and all operations silently no-op.
 */
public class ElasticSearchClient {

    private static final LoggerMaker logger = new LoggerMaker(ElasticSearchClient.class, LogDb.DB_ABS);

    private static final String ES_HOST = System.getenv("ES_HOST");
    private static final String ES_API_KEY = System.getenv("ES_API_KEY");
    private static final String ES_INDEX = System.getenv("ES_INDEX_AGENT_QUERY");

    private static final String SCROLL_KEEP_ALIVE = "2m";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json");
    private static final MediaType NDJSON_MEDIA = MediaType.parse("application/x-ndjson");

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

    /**
     * Bulk-indexes a batch of agent query records into ES.
     * Maps timeStampMs → timestamp at write time. Fire-and-forget; errors are logged only.
     */
    public void bulkIndexAgentQueryRecords(List<AgentQueryRecord> records) {
        logger.info("Bulk indexing agent query records: " + records.size());
        if (!isConfigured() || records == null || records.isEmpty()) return;

        StringBuilder ndjson = new StringBuilder();
        for (AgentQueryRecord r : records) {
            ndjson.append("{\"index\":{}}\n");
            try {
                JSONObject doc = new JSONObject()
                    .put("accountId",         r.getAccountId())
                    .put("serviceId",         r.getServiceId())
                    .put("deviceId",          r.getDeviceId())
                    .put("userName",          r.getUserName())
                    .put("sessionIdentifier", r.getSessionIdentifier())
                    .put("queryPayload",      r.getQueryPayload())
                    .put("responsePayload",   r.getResponsePayload())
                    .put("timestamp",         r.getTimeStampMs())
                    .put("inputTokens",       r.getInputTokens())
                    .put("outputTokens",      r.getOutputTokens())
                    .put("isAtlasTraffic",    r.isAtlasTraffic());
                ndjson.append(doc).append("\n");
            } catch (JSONException e) {
                logger.error("Failed to serialize AgentQueryRecord: " + e.getMessage());
            }
        }

        String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_bulk";
        Request.Builder rb = new Request.Builder()
            .url(url)
            .method("POST", RequestBody.create(ndjson.toString(), NDJSON_MEDIA))
            .addHeader("Content-Type", "application/x-ndjson");
        addAuthHeader(rb);

        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                logger.error("ES bulk index failed (" + resp.code() + ") for " + url);
            }
            logger.info("ES bulk index successful for " + url);
        } catch (Exception e) {
            logger.error("ES bulk index error: " + e.getMessage());
        }
    }

    /**
     * Scrolls ES for agentic query records in [startTsMs, endTsMs) for the given accountId.
     * Invokes handler for each hit up to maxRecords. Always releases the scroll cursor on exit.
     */
    public void scrollQueryData(int accountId, long startTsMs, long endTsMs, int pageSize,
                                int maxRecords, Consumer<AgentQueryRecord> handler) {
        if (!isConfigured()) return;

        String scrollId = null;
        int delivered = 0;
        try {
            String initialBody = buildInitialQuery(accountId, startTsMs, endTsMs, pageSize).toString();
            String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search?scroll=" + SCROLL_KEEP_ALIVE;
            JSONObject response = httpPost(url, initialBody);
            if (response == null) return;

            scrollId = response.optString("_scroll_id", null);
            JSONArray hits = extractHits(response);

            while (hits != null && hits.length() > 0 && delivered < maxRecords) {
                for (int i = 0; i < hits.length() && delivered < maxRecords; i++) {
                    AgentQueryRecord rec = parseHit(hits.getJSONObject(i));
                    if (rec != null) {
                        handler.accept(rec);
                        delivered++;
                    }
                }
                if (delivered >= maxRecords || scrollId == null) break;

                JSONObject scrollBody = new JSONObject()
                    .put("scroll", SCROLL_KEEP_ALIVE)
                    .put("scroll_id", scrollId);
                String scrollUrl = trimTrailingSlash(ES_HOST) + "/_search/scroll";
                response = httpPost(scrollUrl, scrollBody.toString());
                if (response == null) break;
                scrollId = response.optString("_scroll_id", scrollId);
                hits = extractHits(response);
            }
        } catch (Exception e) {
            logger.error("Error scrolling ES for accountId " + accountId + ": " + e.getMessage());
        } finally {
            if (scrollId != null) {
                releaseScroll(scrollId);
            }
        }
    }

    private JSONObject buildInitialQuery(int accountId, long startTsMs, long endTsMs, int pageSize) throws JSONException {
        JSONObject range = new JSONObject()
            .put("timestamp", new JSONObject().put("gte", startTsMs).put("lt", endTsMs));
        JSONObject term = new JSONObject().put("accountId", accountId);

        JSONArray must = new JSONArray()
            .put(new JSONObject().put("range", range))
            .put(new JSONObject().put("term", term));

        JSONObject query = new JSONObject().put("bool", new JSONObject().put("must", must));

        return new JSONObject()
            .put("size", pageSize)
            .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "asc"))))
            .put("query", query);
    }

    private JSONArray extractHits(JSONObject response) {
        JSONObject outer = response.optJSONObject("hits");
        if (outer == null) return null;
        return outer.optJSONArray("hits");
    }

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
            if (rbody == null) return null;
            return new JSONObject(rbody.string());
        } catch (Exception e) {
            logger.error("ES request error for " + url + ": " + e.getMessage());
            return null;
        }
    }

    private void releaseScroll(String scrollId) {
        try {
            String url = trimTrailingSlash(ES_HOST) + "/_search/scroll";
            JSONObject body = new JSONObject().put("scroll_id", scrollId);
            Request.Builder rb = new Request.Builder()
                .url(url)
                .method("DELETE", RequestBody.create(body.toString(), JSON_MEDIA))
                .addHeader("Content-Type", "application/json");
            addAuthHeader(rb);
            try (Response resp = http.newCall(rb.build()).execute()) {
                // best-effort cleanup; ignore status
            }
        } catch (Exception ignored) {
        }
    }

    private void addAuthHeader(Request.Builder rb) {
        if (ES_API_KEY != null && !ES_API_KEY.isEmpty()) {
            rb.addHeader("Authorization", "ApiKey " + ES_API_KEY);
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
