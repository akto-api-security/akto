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
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    private static final int MAX_QUERY_RESULTS = 500;
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
                    .put("isAtlasTraffic",    r.isAtlasTraffic())
                    .put("traceId",           r.getTraceId())
                    .put("spanId",            r.getSpanId())
                    .put("topicProcessed",    false);
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

    public List<AgentQueryRecord> scrollQueryData(int accountId, String spanId) {
        List<AgentQueryRecord> res = new ArrayList<>();
        if (!isConfigured() || spanId == null || spanId.isEmpty()) return res;

        try {
            String body = buildQuery(accountId, spanId).toString();
            String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_search";
            JSONObject response = httpPost(url, body);
            if (response == null) return res;

            JSONArray hits = extractHits(response);
            if (hits == null) return res;

            for (int i = 0; i < hits.length(); i++) {
                AgentQueryRecord rec = parseHit(hits.getJSONObject(i));
                if (rec != null) {
                    res.add(rec);
                }
            }
        } catch (Exception e) {
            logger.error("Error querying ES for accountId " + accountId + ", spanId " + spanId + ": " + e.getMessage());
        }
        return res;
    }

    private JSONObject buildQuery(int accountId, String spanId) throws JSONException {
        JSONArray must = new JSONArray()
            .put(new JSONObject().put("term", new JSONObject().put("accountId", accountId)))
            .put(new JSONObject().put("term", new JSONObject().put("spanId", spanId)));

        JSONObject query = new JSONObject().put("bool", new JSONObject().put("must", must));

        return new JSONObject()
            .put("size", MAX_QUERY_RESULTS)
            .put("sort", new JSONArray().put(new JSONObject().put("timestamp", new JSONObject().put("order", "desc"))))
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
            source.optBoolean("isAtlasTraffic", false),
            source.optBoolean("topicProcessed", false)

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
