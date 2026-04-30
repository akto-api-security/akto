package com.akto.utils.clickup;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.apache.commons.lang3.StringUtils;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fetches ClickUp trace summaries, maps rows to http-proxy ingest JSON, POSTs to DIS.
 * <p>
 * Env: {@code CLICKUP_SYNC_ENABLED}=true, {@code CLICKUP_TEMP_TOKEN}, {@code AKTO_CLICKUP_INGEST_URL} (http-proxy base URL),
 * {@code CLICKUP_WORKSPACE_ID}. Fetch URL: {@code CLICKUP_FETCH_URL} (full URL) <em>or</em> {@code CLICKUP_API_BASE_URL} +
 * workspace path. If {@code CLICKUP_API_BASE_URL} contains {@code auto-auditlog-service} (ClickUp frontdoor audit log),
 * default path is {@code /v1/workspaces/{workspaceId}/trace-summaries} with query params like the browser
 * ({@code pageDirection=before}, {@code pageTimestamp}, {@code pageTimestampEnd}, {@code isAgent=true}). Otherwise
 * default path is {@code /v1/w/{workspaceId}/trace-summaries} with {@code pageDirection=1}. Override path with
 * {@code CLICKUP_TRACE_PATH_TEMPLATE}. Optional: {@code CLICKUP_TRACE_AUDITLOG_QUERIES}, {@code CLICKUP_TRACE_PAGE_DIRECTION},
 * {@code CLICKUP_TRACE_PAGE_TIMESTAMP_MS}, {@code CLICKUP_TRACE_PAGE_TIMESTAMP_END_MS}, {@code CLICKUP_TRACE_WINDOW_MS},
 * {@code CLICKUP_CRON_INTERVAL_SECONDS}, {@code CLICKUP_MAX_INGEST_ROWS_PER_TICK}, {@code CLICKUP_PAGE_ROWS},
 * {@code AKTO_CLICKUP_ACCOUNT_ID}, {@code AKTO_CLICKUP_VXLAN_ID}, {@code CLICKUP_INGEST_HOST_HEADER} (optional override;
 * otherwise host is taken from {@code CLICKUP_FETCH_URL} or {@code CLICKUP_API_BASE_URL}).
 * Ingest {@code requestPayload} is the real list-call body (typically {@code {}} for GET); {@code responsePayload} keeps
 * the wrapped {@code clickupTraceMetadata} shape for mini-runtime.
 */
public final class ClickUpIngestService {

    private static final LoggerMaker logger = new LoggerMaker(ClickUpIngestService.class, LogDb.DASHBOARD);
    private static final Gson GSON = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    /** ClickUp trace list API is GET — synthetic mirror uses this as the real request body. */
    private static final String CLICKUP_TRACE_LIST_REQUEST_BODY = "{}";

    private static final ThreadLocal<Map<String, String>> ENV_OVERLAY = new ThreadLocal<>();

    private ClickUpIngestService() {}

    /**
     * Runs {@code runnable} with {@code overlay} merged over {@link System#getenv} for env reads in this class (same-thread only).
     */
    public static void withEnvOverlay(Map<String, String> overlay, Runnable runnable) {
        Map<String, String> prev = ENV_OVERLAY.get();
        ENV_OVERLAY.set(overlay == null ? Collections.emptyMap() : overlay);
        try {
            runnable.run();
        } finally {
            if (prev == null) {
                ENV_OVERLAY.remove();
            } else {
                ENV_OVERLAY.set(prev);
            }
        }
    }

    public static boolean syncEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("CLICKUP_SYNC_ENABLED", "false"));
    }

    public static void runOneSync() {
        runOneSync(true);
    }

    /**
     * @param requireSyncEnabled when true, no-op unless {@code CLICKUP_SYNC_ENABLED} is true in the environment (ignored for overlay-only local runs).
     */
    public static void runOneSync(boolean requireSyncEnabled) {
        if (requireSyncEnabled && !syncEnabled()) {
            return;
        }
        String token = env("CLICKUP_TEMP_TOKEN");
        String fetchUrl = resolveFetchUrl();
        String ingestBase = env("AKTO_CLICKUP_INGEST_URL");
        String workspaceId = env("CLICKUP_WORKSPACE_ID");
        if (StringUtils.isBlank(token) || StringUtils.isBlank(fetchUrl) || StringUtils.isBlank(ingestBase) || StringUtils.isBlank(workspaceId)) {
            logger.warnAndAddToDb(
                    "ClickUp sync skipped: set CLICKUP_SYNC_ENABLED and CLICKUP_TEMP_TOKEN, CLICKUP_FETCH_URL (or CLICKUP_API_BASE_URL+CLICKUP_WORKSPACE_ID), AKTO_CLICKUP_INGEST_URL",
                    LogDb.DASHBOARD);
            return;
        }
        String accountId = envOrDefault("AKTO_CLICKUP_ACCOUNT_ID", "1000000");
        String vxlan = envOrDefault("AKTO_CLICKUP_VXLAN_ID", "0");
        int maxRows = intEnv("CLICKUP_MAX_INGEST_ROWS_PER_TICK", 50);
        String resolvedHost = ClickUpToHttpProxyMapper.resolveIngestHostHeader(fetchUrl, env("CLICKUP_API_BASE_URL"));
        String hostHeader = envOrDefault("CLICKUP_INGEST_HOST_HEADER", resolvedHost);
        if (StringUtils.isBlank(hostHeader)) {
            logger.warnAndAddToDb(
                    "ClickUp sync skipped: set CLICKUP_INGEST_HOST_HEADER or valid CLICKUP_FETCH_URL / CLICKUP_API_BASE_URL so ingest Host can be resolved",
                    LogDb.DASHBOARD);
            return;
        }

        try {
            String body = httpGet(fetchUrl, token);
            if (StringUtils.isBlank(body)) {
                logger.warnAndAddToDb("ClickUp fetch returned empty body", LogDb.DASHBOARD);
                return;
            }
            List<JsonObject> summaries = extractTraceSummaries(JsonParser.parseString(body));
            int now = (int) (System.currentTimeMillis() / 1000);
            int sent = 0;
            for (JsonObject item : summaries) {
                if (sent >= maxRows) {
                    break;
                }
                String id = pickTraceId(item);
                if (StringUtils.isBlank(id)) {
                    continue;
                }
                String botName = pickAgentViewId(item);
                if (StringUtils.isBlank(botName)) {
                    botName = id;
                }
                String line = ClickUpToHttpProxyMapper.toHttpProxyJsonLine(
                        id,
                        workspaceId,
                        CLICKUP_TRACE_LIST_REQUEST_BODY,
                        GSON.toJson(item),
                        now,
                        accountId,
                        vxlan,
                        hostHeader,
                        botName
                );
                postIngest(ingestBase, line);
                sent++;
            }
            if (sent > 0) {
                logger.infoAndAddToDb("ClickUp ingest posted " + sent + " row(s)", LogDb.DASHBOARD);
            } else {
                logger.debugAndAddToDb("ClickUp ingest: no trace summary rows to post", LogDb.DASHBOARD);
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "ClickUp sync failed: " + e.getMessage());
        }
    }

    static String resolveFetchUrl() {
        String explicit = env("CLICKUP_FETCH_URL");
        if (StringUtils.isNotBlank(explicit)) {
            return explicit.trim();
        }
        String base = env("CLICKUP_API_BASE_URL");
        String ws = env("CLICKUP_WORKSPACE_ID");
        if (StringUtils.isBlank(base) || StringUtils.isBlank(ws)) {
            return null;
        }
        String baseTrim = base.trim();
        boolean auditlogHost = baseTrim.contains("auto-auditlog-service");
        boolean auditlogQueries =
                auditlogHost || Boolean.parseBoolean(envOrDefault("CLICKUP_TRACE_AUDITLOG_QUERIES", "false"));
        String pathTemplate = env("CLICKUP_TRACE_PATH_TEMPLATE");
        if (StringUtils.isBlank(pathTemplate)) {
            pathTemplate =
                    auditlogHost ? "/v1/workspaces/{workspaceId}/trace-summaries" : "/v1/w/{workspaceId}/trace-summaries";
        }
        String path = pathTemplate.replace("{workspaceId}", ws.trim());
        int pageRows = intEnv("CLICKUP_PAGE_ROWS", 25);
        HttpUrl baseUrl = HttpUrl.parse(baseTrim.replaceAll("/+$", ""));
        if (baseUrl == null) {
            return null;
        }
        HttpUrl.Builder b = baseUrl.newBuilder();
        String[] segments = path.startsWith("/") ? path.substring(1).split("/") : path.split("/");
        for (String seg : segments) {
            if (!seg.isEmpty()) {
                b.addPathSegment(seg);
            }
        }
        b.addQueryParameter("pageRows", String.valueOf(pageRows));
        b.addQueryParameter("isAgent", "true");
        if (auditlogQueries) {
            b.addQueryParameter("pageDirection", envOrDefault("CLICKUP_TRACE_PAGE_DIRECTION", "before"));
            long now = System.currentTimeMillis();
            String pageTs = envOrDefault("CLICKUP_TRACE_PAGE_TIMESTAMP_MS", Long.toString(now));
            String pageTsEnd = env("CLICKUP_TRACE_PAGE_TIMESTAMP_END_MS");
            if (StringUtils.isBlank(pageTsEnd)) {
                long windowMs = 14L * 24 * 60 * 60 * 1000;
                try {
                    windowMs = Long.parseLong(envOrDefault("CLICKUP_TRACE_WINDOW_MS", String.valueOf(windowMs)).trim());
                } catch (NumberFormatException ignored) {
                    // keep default window
                }
                pageTsEnd = Long.toString(now - windowMs);
            }
            b.addQueryParameter("pageTimestamp", pageTs);
            b.addQueryParameter("pageTimestampEnd", pageTsEnd);
        } else {
            b.addQueryParameter("pageDirection", envOrDefault("CLICKUP_TRACE_PAGE_DIRECTION", "1"));
        }
        return b.build().toString();
    }

    static List<JsonObject> extractTraceSummaries(JsonElement root) {
        List<JsonObject> out = new ArrayList<>();
        if (root == null || root.isJsonNull()) {
            return out;
        }
        if (root.isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
            return out;
        }
        if (!root.isJsonObject()) {
            return out;
        }
        JsonObject obj = root.getAsJsonObject();
        String[] keys = {"data", "traceSummaries", "traces", "items", "results"};
        for (String k : keys) {
            if (!obj.has(k)) {
                continue;
            }
            JsonElement arrEl = obj.get(k);
            if (arrEl.isJsonArray()) {
                for (JsonElement e : arrEl.getAsJsonArray()) {
                    if (e.isJsonObject()) {
                        out.add(e.getAsJsonObject());
                    }
                }
                return out;
            }
        }
        out.add(obj);
        return out;
    }

    static String readStringishPrimitive(JsonObject item, String key) {
        if (item == null || !item.has(key) || item.get(key).isJsonNull() || !item.get(key).isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = item.getAsJsonPrimitive(key);
        if (p.isString()) {
            return p.getAsString();
        }
        if (p.isNumber()) {
            return p.getAsString();
        }
        return null;
    }

    static String pickTraceId(JsonObject item) {
        String[] idKeys = {"traceSummaryId", "id", "traceId", "summaryId"};
        for (String k : idKeys) {
            String v = readStringishPrimitive(item, k);
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    /** ClickUp trace summary {@code agentViewId} for ingest {@code bot-name} (falls back to trace id when absent). */
    static String pickAgentViewId(JsonObject item) {
        String[] keys = {"agentViewId", "agent_view_id"};
        for (String k : keys) {
            String v = readStringishPrimitive(item, k);
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * DIS / {@code IngestionAction} expects JSON root {@code {"batchData":[ IngestDataBatch, ... ]}} (Struts binds {@code batchData}).
     * The mapper emits a single {@link IngestDataBatch} object; wrap it when callers did not already.
     */
    static String ensureBatchDataWrapper(String jsonBody) {
        String t = jsonBody == null ? "" : jsonBody.trim();
        if (t.isEmpty()) {
            return "{\"batchData\":[]}";
        }
        if (t.startsWith("{\"batchData\"")) {
            return t;
        }
        if (t.startsWith("[")) {
            return "{\"batchData\":" + t + "}";
        }
        return "{\"batchData\":[" + t + "]}";
    }

    static void postIngest(String ingestBaseUrl, String jsonBody) throws IOException {
        HttpUrl base = HttpUrl.parse(ingestBaseUrl.trim());
        if (base == null) {
            throw new IOException("Invalid AKTO_CLICKUP_INGEST_URL");
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("ingest_data", "true")
                .addQueryParameter("response_guardrails", "true")
                .build();
        String body = ensureBatchDataWrapper(jsonBody);
        Request req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .header("Content-Type", "application/json")
                .build();
        try (Response resp = CoreHTTPClient.client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String errBody = resp.body() != null ? resp.body().string() : "";
                logger.warnAndAddToDb("ClickUp ingest POST " + resp.code() + " " + errBody, LogDb.DASHBOARD);
            }
        }
    }

    static String httpGet(String url, String bearerToken) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + bearerToken)
                .get()
                .build();
        try (Response resp = CoreHTTPClient.client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String err = resp.body() != null ? resp.body().string() : "";
                logger.warnAndAddToDb("ClickUp GET " + resp.code() + " " + err, LogDb.DASHBOARD);
                return null;
            }
            return resp.body() != null ? resp.body().string() : null;
        }
    }

    static String env(String k) {
        Map<String, String> overlay = ENV_OVERLAY.get();
        if (overlay != null && overlay.containsKey(k)) {
            String v = overlay.get(k);
            return v != null ? v : "";
        }
        String v = System.getenv(k);
        return v == null ? "" : v;
    }

    static String envOrDefault(String k, String d) {
        Map<String, String> overlay = ENV_OVERLAY.get();
        if (overlay != null && overlay.containsKey(k)) {
            String v = overlay.get(k);
            return StringUtils.isBlank(v) ? d : v;
        }
        String v = System.getenv(k);
        return StringUtils.isBlank(v) ? d : v;
    }

    static int intEnv(String k, int def) {
        try {
            Map<String, String> overlay = ENV_OVERLAY.get();
            if (overlay != null && overlay.containsKey(k)) {
                String v = overlay.get(k);
                if (StringUtils.isBlank(v)) {
                    return def;
                }
                return Integer.parseInt(v.trim());
            }
            String v = System.getenv(k);
            if (StringUtils.isBlank(v)) {
                return def;
            }
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

}
