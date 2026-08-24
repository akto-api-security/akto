package com.akto.utils.elasticsearch;

import com.akto.agent_risk.AgentRiskScore;
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
    private static final String ES_INDEX_RISK = envOrDefault("ES_INDEX_AGENT_RISK", "agent-risk-scores");

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

    public boolean isRiskIndexConfigured() {
        return ES_HOST != null && !ES_HOST.isEmpty()
            && ES_INDEX_RISK != null && !ES_INDEX_RISK.isEmpty();
    }

    /**
     * Bulk-indexes a batch of agent query records into ES.
     * Maps timeStampMs → timestamp at write time. Fire-and-forget; errors are logged only.
     */
    public void bulkIndexAgentQueryRecords(List<AgentQueryRecord> records) {
        logger.info("Bulk indexing agent query records: " + records.size());
        if (!isConfigured()) {
            logger.error("ElasticSearchClient not configured, skipping bulk index. ES_HOST set: "
                + (ES_HOST != null && !ES_HOST.isEmpty()) + ", ES_INDEX_AGENT_QUERY set: "
                + (ES_INDEX != null && !ES_INDEX.isEmpty()));
            return;
        }
        if (records == null || records.isEmpty()) return;

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
                if (r.getApiCollectionId() != 0) {
                    doc.put("apiCollectionId", r.getApiCollectionId());
                }

                // Guardrail result: whether a policy was hit, which policy and rule, what was done
                // about it, and why. Saved on the trace so an investigation does not have to look up
                // the threat event separately. Written only when guardrails actually ran - otherwise
                // the trace would claim a clean result for a prompt that was never checked.
                if (r.getGuardrailViolated() != null) {
                    doc.put("guardrailViolated", r.getGuardrailViolated())
                       .put("guardrailAction",   emptyIfNull(r.getGuardrailAction()))
                       .put("guardrailPolicy",   emptyIfNull(r.getGuardrailPolicy()))
                       .put("guardrailRule",     emptyIfNull(r.getGuardrailRule()))
                       .put("guardrailReason",   emptyIfNull(r.getGuardrailReason()))
                       .put("guardrailSeverity", emptyIfNull(r.getGuardrailSeverity()));
                }
                ndjson.append(doc).append("\n");
            } catch (JSONException e) {
                logger.error("Failed to serialize AgentQueryRecord: " + e.getMessage());
            }
        }

        String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_bulk";
        logger.info("ES bulk index request: url=" + url + ", docs=" + records.size());
        Request.Builder rb = new Request.Builder()
            .url(url)
            .method("POST", RequestBody.create(ndjson.toString(), NDJSON_MEDIA))
            .addHeader("Content-Type", "application/x-ndjson");
        addAuthHeader(rb);

        try (Response resp = http.newCall(rb.build()).execute()) {
            ResponseBody respBody = resp.body();
            String bodyText = respBody != null ? respBody.string() : "";

            if (!resp.isSuccessful()) {
                logger.error("ES bulk index failed (" + resp.code() + ") for " + url + ": " + bodyText);
                return;
            }

            try {
                JSONObject bulkResponse = new JSONObject(bodyText);
                if (bulkResponse.optBoolean("errors", false)) {
                    logger.error("ES bulk index had per-item errors for " + url + ": " + bodyText);
                } else {
                    logger.info("ES bulk index successful for " + url + ", took=" + bulkResponse.optInt("took", -1) + "ms");
                }
            } catch (JSONException e) {
                logger.error("ES bulk index response was not valid JSON for " + url + ": " + bodyText);
            }
        } catch (Exception e) {
            logger.error("ES bulk index error for " + url + ": " + e, e);
        }
    }

    /**
     * Persist scored agent traces. Mapping can be created out of band.
     * Errors are logged; callers must not fail the Kafka consume on persist miss.
     */
    public void bulkIndexAgentRiskScores(List<AgentRiskScore> scores) {
        if (!isRiskIndexConfigured() || scores == null || scores.isEmpty()) {
            return;
        }
        StringBuilder ndjson = new StringBuilder();
        for (AgentRiskScore s : scores) {
            if (s == null) {
                continue;
            }
            ndjson.append("{\"index\":{}}\n");
            try {
                JSONObject doc = new JSONObject()
                    .put("accountId", s.getAccountId())
                    .put("hash", emptyIfNull(s.getHash()))
                    .put("neighborId", emptyIfNull(s.getNeighborId()))
                    .put("source", s.getSource() == null ? "" : s.getSource().name())
                    .put("composite", s.getComposite())
                    .put("dataRisk", s.getDataRisk())
                    .put("toolRisk", s.getToolRisk())
                    .put("dataClassMax", s.getDataClassMax())
                    .put("agentKey", emptyIfNull(s.getAgentKey()))
                    .put("toolFingerprint", emptyIfNull(s.getToolFingerprint()))
                    .put("privilegeClass", emptyIfNull(s.getPrivilegeClass()))
                    .put("hardConstraintsMatched", s.isHardConstraintsMatched())
                    .put("traceId", emptyIfNull(s.getTraceId()))
                    .put("spanId", emptyIfNull(s.getSpanId()))
                    .put("timestamp", s.getTimestamp());
                if (s.getApiCollectionId() != null && s.getApiCollectionId() != 0) {
                    doc.put("apiCollectionId", s.getApiCollectionId());
                }
                if (s.getEmbedding() != null && !s.getEmbedding().isEmpty()) {
                    JSONArray vec = new JSONArray();
                    for (Double d : s.getEmbedding()) {
                        vec.put(d);
                    }
                    doc.put("dense_vector", vec);
                }
                ndjson.append(doc).append("\n");
            } catch (JSONException e) {
                logger.error("Failed to serialize AgentRiskScore: " + e.getMessage());
            }
        }
        if (ndjson.length() == 0) {
            return;
        }

        String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX_RISK + "/_bulk";
        Request.Builder rb = new Request.Builder()
            .url(url)
            .method("POST", RequestBody.create(ndjson.toString(), NDJSON_MEDIA))
            .addHeader("Content-Type", "application/x-ndjson");
        addAuthHeader(rb);

        try (Response resp = http.newCall(rb.build()).execute()) {
            ResponseBody respBody = resp.body();
            String bodyText = respBody != null ? respBody.string() : "";
            if (!resp.isSuccessful()) {
                logger.error("ES agent-risk bulk index failed (" + resp.code() + ") for " + url + ": " + bodyText);
                return;
            }
            try {
                JSONObject bulkResponse = new JSONObject(bodyText);
                if (bulkResponse.optBoolean("errors", false)) {
                    logger.error("ES agent-risk bulk index had per-item errors for " + url);
                }
            } catch (JSONException ignored) {
                // body already consumed; do not fail the consumer
            }
        } catch (Exception e) {
            logger.error("ES agent-risk bulk index error for " + url + ": " + e.getMessage());
        }
    }

    /**
     * Cheap batch update: mark original traces processed. Composite lives on the
     * risk index; this only flips topicProcessed so we do one query per account.
     */
    public void markAgentQueryTopicProcessed(List<AgentRiskScore> scores) {
        if (!isConfigured() || scores == null || scores.isEmpty()) {
            return;
        }
        Map<Integer, JSONArray> tracesByAccount = new HashMap<>();
        for (AgentRiskScore s : scores) {
            if (s == null || s.getTraceId() == null || s.getTraceId().isEmpty()) {
                continue;
            }
            tracesByAccount.computeIfAbsent(s.getAccountId(), k -> new JSONArray()).put(s.getTraceId());
        }
        for (Map.Entry<Integer, JSONArray> e : tracesByAccount.entrySet()) {
            try {
                JSONObject query = new JSONObject().put("bool", new JSONObject().put("must", new JSONArray()
                    .put(new JSONObject().put("term", new JSONObject().put("accountId", e.getKey())))
                    .put(new JSONObject().put("terms", new JSONObject().put("traceId.keyword", e.getValue())))));
                JSONObject body = new JSONObject()
                    .put("query", query)
                    .put("script", new JSONObject()
                        .put("source", "ctx._source.topicProcessed = true;")
                        .put("lang", "painless"));
                String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX + "/_update_by_query?conflicts=proceed";
                httpPost(url, body.toString());
            } catch (Exception ex) {
                logger.error("ES topicProcessed update failed for accountId " + e.getKey() + ": " + ex.getMessage());
            }
        }
    }

    /**
     * kNN on agent-risk-scores.dense_vector. Fail-open: null on any error or miss.
     * Distance is 1 - _score, matching guardcache cosine distance.
     */
    public KnnHit knnSearchAgentRiskScores(List<Double> vector, int accountId, String agentKey) {
        if (!isRiskIndexConfigured() || vector == null || vector.isEmpty()) {
            return null;
        }
        try {
            JSONArray queryVector = new JSONArray();
            for (Double d : vector) {
                queryVector.put(d);
            }
            JSONArray must = new JSONArray()
                .put(new JSONObject().put("term", new JSONObject().put("accountId", accountId)));
            if (agentKey != null && !agentKey.isEmpty()) {
                must.put(new JSONObject().put("term", new JSONObject().put("agentKey.keyword", agentKey)));
            }
            JSONObject knn = new JSONObject()
                .put("field", "dense_vector")
                .put("query_vector", queryVector)
                .put("k", 1)
                .put("num_candidates", 20)
                .put("filter", new JSONObject().put("bool", new JSONObject().put("must", must)));
            JSONObject body = new JSONObject().put("knn", knn).put("size", 1);
            String url = trimTrailingSlash(ES_HOST) + "/" + ES_INDEX_RISK + "/_search";
            JSONObject response = httpPost(url, body.toString());
            if (response == null) {
                return null;
            }
            JSONArray hits = extractHits(response);
            if (hits == null || hits.length() == 0) {
                return null;
            }
            JSONObject hit = hits.getJSONObject(0);
            JSONObject source = hit.optJSONObject("_source");
            if (source == null) {
                return null;
            }
            AgentRiskScore neighbor = new AgentRiskScore();
            neighbor.setHash(source.optString("hash", ""));
            neighbor.setNeighborId(neighbor.getHash());
            neighbor.setAccountId(source.optInt("accountId", 0));
            neighbor.setAgentKey(source.optString("agentKey", ""));
            neighbor.setToolFingerprint(source.optString("toolFingerprint", ""));
            neighbor.setPrivilegeClass(source.optString("privilegeClass", ""));
            neighbor.setComposite(source.optInt("composite", 0));
            neighbor.setDataRisk(source.optInt("dataRisk", 0));
            neighbor.setToolRisk(source.optInt("toolRisk", 0));
            neighbor.setDataClassMax(source.optInt("dataClassMax", 0));
            neighbor.setSource(AgentRiskScore.Source.REUSED);
            double similarity = hit.optDouble("_score", 0d);
            double distance = Math.max(0d, 1.0d - similarity);
            neighbor.setKnnDistance(distance);
            return new KnnHit(neighbor, distance);
        } catch (Exception e) {
            logger.error("ES knn search failed: " + e.getMessage());
            return null;
        }
    }

    public static final class KnnHit {
        public final AgentRiskScore neighbor;
        public final double distance;

        public KnnHit(AgentRiskScore neighbor, double distance) {
            this.neighbor = neighbor;
            this.distance = distance;
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
            .put(new JSONObject().put("term", new JSONObject().put("spanId.keyword", spanId)));

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

        AgentQueryRecord rec = new AgentQueryRecord();
        rec.setDocId(hit.optString("_id", ""));
        rec.setAccountId(source.optInt("accountId", 0));
        rec.setServiceId(source.optString("serviceId", ""));
        rec.setDeviceId(source.optString("deviceId", ""));
        rec.setUserName(source.optString("userName", ""));
        rec.setSessionIdentifier(source.optString("sessionIdentifier", ""));
        rec.setQueryPayload(queryPayload);
        rec.setResponsePayload(source.optString("responsePayload", ""));
        rec.setTimeStampMs(source.optLong("timestamp", 0L));
        rec.setInputTokens(source.optInt("inputTokens", 0));
        rec.setOutputTokens(source.optInt("outputTokens", 0));
        rec.setTraceId(source.optString("traceId", ""));
        rec.setSpanId(source.optString("spanId", ""));
        rec.setIsAtlasTraffic(source.optBoolean("isAtlasTraffic", false));
        rec.setTopicProcessed(source.optBoolean("topicProcessed", false));
        if (source.has("guardrailViolated")) {
            rec.setGuardrailViolated(source.optBoolean("guardrailViolated"));
        }
        rec.setGuardrailAction(source.optString("guardrailAction", ""));
        rec.setGuardrailPolicy(source.optString("guardrailPolicy", ""));
        rec.setGuardrailRule(source.optString("guardrailRule", ""));
        rec.setGuardrailReason(source.optString("guardrailReason", ""));
        rec.setGuardrailSeverity(source.optString("guardrailSeverity", ""));
        if (source.has("apiCollectionId")) {
            rec.setApiCollectionId(source.optInt("apiCollectionId"));
        }
        return rec;
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

    private static String envOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    // org.json throws on a null value, so missing guardrail fields have to be saved as "".
    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
