package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.sentinelone_integration.SentinelOneIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SentinelOneExecutor extends AccountJobExecutor {

    public static final SentinelOneExecutor INSTANCE = new SentinelOneExecutor();

    private static final LoggerMaker loggerMaker = new LoggerMaker(SentinelOneExecutor.class, LogDb.DASHBOARD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS  = 30_000;
    private static final int SOCKET_TIMEOUT_MS   = 60_000;
    private static final int DV_POLL_INTERVAL_MS = 3_000;
    private static final int DV_MAX_WAIT_MS      = 60_000;
    private static final int DV_LOOKBACK_HOURS   = 6;

    /**
     * Tools to look up in the installed-app inventory (case-insensitive contains match).
     * Add any new AI coding tools here.
     */
    public static final List<String> APPS_TOOL_LIST = Arrays.asList(
        "Claude",
        "Cursor",
        "Copilot",
        "Windsurf",
        "Antigravity",
        "Codex"
    );

    /**
     * Tools to query for via Deep Visibility process events. (case sensitive)
     * Matched against processCmd and processName (exact string contains).
     * Add new tools here when they appear as process names.
     */
    public static final List<String> DV_TOOL_LIST = Arrays.asList(
    "openclaw",
    "gemini"
    );

    // ── Executor implementation ───────────────────────────────────────────────

    private SentinelOneExecutor() {}

    @Override
    protected void runJob(AccountJob job) throws Exception {
        Map<String, Object> jobConfig = job.getConfig();
        if (jobConfig == null || jobConfig.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }
        
        // Extract integration data from job config
        SentinelOneIntegration integration = OBJECT_MAPPER.convertValue(jobConfig, SentinelOneIntegration.class);
        
        if (integration.getApiToken() == null || integration.getApiToken().isEmpty()) {
            loggerMaker.error("SentinelOneExecutor: API token is null or empty in job config", LogDb.DASHBOARD);
            throw new IllegalStateException("SentinelOne API token not configured");
        }
        
        execute(integration);
    }

    // ── Main execution logic ──────────────────────────────────────────────────

    /**
     * Main entry point – runs the full apps + DV pipeline for every tool in the respective lists.
     *
     * @param integration fully-populated SentinelOneIntegration (with apiToken)
     */
    private static void execute(SentinelOneIntegration integration) {
        if (integration == null) {
            loggerMaker.error("SentinelOneExecutor.execute called with null integration", LogDb.DASHBOARD);
            return;
        }

        String consoleUrl    = normalizeUrl(integration.getConsoleUrl());
        String ingestUrl     = normalizeUrl(integration.getDataIngestionUrl());
        String consoleDomain = extractDomain(integration.getConsoleUrl());
        String apiToken      = integration.getApiToken();

        // Fetch agent list once — reused across the apps pass
        List<Map<String, Object>> agentList;
        try {
            agentList = fetchAgentList(apiToken, consoleUrl);
            loggerMaker.info("SentinelOneExecutor: fetched " + agentList.size() + " agent(s)", LogDb.DASHBOARD);
        } catch (IOException e) {
            loggerMaker.error("SentinelOneExecutor: failed to fetch agents — " + e.getMessage(), LogDb.DASHBOARD);
            return;
        }

        // ── Pass 1: Installed Apps ────────────────────────────────────────────
        for (String tool : APPS_TOOL_LIST) {
            try {
                List<Map<String, Object>> apps = fetchAppsForTool(tool, agentList, apiToken, consoleUrl);
                if (apps.isEmpty()) {
                    loggerMaker.info("SentinelOneExecutor: no apps found for tool: " + tool, LogDb.DASHBOARD);
                    continue;
                }
                String toolDomain = tool.toLowerCase() + ".sentinel";
                ingestApps(apps, tool, toolDomain, ingestUrl, consoleDomain);
                loggerMaker.info("SentinelOneExecutor: ingested " + apps.size() + " app(s) for " + tool, LogDb.DASHBOARD);
            } catch (IOException e) {
                loggerMaker.error("SentinelOneExecutor: apps error for " + tool + " — " + e.getMessage(), LogDb.DASHBOARD);
            }
        }

        // ── Pass 2: Deep Visibility (process events) ──────────────────────────
        // Make ONE combined query for all DV tools to avoid rate limiting
        if (!DV_TOOL_LIST.isEmpty()) {
            try {
                List<Map<String, Object>> allEvents = runDvQueryForAllTools(DV_TOOL_LIST, apiToken, consoleUrl, DV_LOOKBACK_HOURS);
                if (allEvents.isEmpty()) {
                    loggerMaker.info("SentinelOneExecutor: no DV events found for any tool", LogDb.DASHBOARD);
                } else {
                    // Group and ingest events by detected tool
                    Map<String, List<Map<String, Object>>> eventsByTool = groupEventsByTool(allEvents, DV_TOOL_LIST);
                    for (Map.Entry<String, List<Map<String, Object>>> entry : eventsByTool.entrySet()) {
                        String tool = entry.getKey();
                        List<Map<String, Object>> events = entry.getValue();
                        String toolDomain = tool.toLowerCase() + ".sentinel";
                        ingestDvEvents(events, tool, toolDomain, ingestUrl, consoleDomain);
                        loggerMaker.info("SentinelOneExecutor: ingested " + events.size() + " DV event(s) for " + tool, LogDb.DASHBOARD);
                    }
                }
            } catch (IOException e) {
                loggerMaker.error("SentinelOneExecutor: DV query failed — " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    // ── Agent helpers ─────────────────────────────────────────────────────────

    static List<Map<String, Object>> fetchAgentList(String apiToken, String consoleUrl) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpGet get = new HttpGet(consoleUrl + "/web/api/v2.1/agents?limit=1000");
            get.setHeader("Authorization", "ApiToken " + apiToken);
            get.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = client.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());
                if (status != 200) {
                    throw new IOException("Failed to fetch agents: HTTP " + status + " — " + body);
                }
                JsonNode data = OBJECT_MAPPER.readTree(body).path("data");
                if (data.isArray()) {
                    for (JsonNode node : data) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("id", node.path("id").asText(""));
                        entry.put("computerName", node.path("computerName").asText(""));
                        result.add(entry);
                    }
                }
            }
        }
        return result;
    }

    // ── Installed Apps ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fetchAppsForTool(
            String tool,
            List<Map<String, Object>> agentList,
            String apiToken,
            String consoleUrl) throws IOException {

        List<Map<String, Object>> result = new ArrayList<>();
        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            for (Map<String, Object> agent : agentList) {
                String agentId      = (String) agent.getOrDefault("id", "");
                String computerName = (String) agent.getOrDefault("computerName", "");
                if (agentId.isEmpty()) continue;

                HttpGet get = new HttpGet(consoleUrl + "/web/api/v2.1/agents/applications?ids=" + agentId);
                get.setHeader("Authorization", "ApiToken " + apiToken);
                get.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = client.execute(get)) {
                    int status = response.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(response.getEntity());
                    if (status != 200) {
                        loggerMaker.error("SentinelOneExecutor: failed to fetch apps for agent " + agentId
                            + ": HTTP " + status, LogDb.DASHBOARD);
                        continue;
                    }
                    JsonNode data = OBJECT_MAPPER.readTree(body).path("data");
                    if (data.isArray()) {
                        for (JsonNode appNode : data) {
                            String appName = appNode.path("name").asText("");
                            if (appName.toLowerCase().contains(tool.toLowerCase())) {
                                Map<String, Object> app = OBJECT_MAPPER.convertValue(appNode, Map.class);
                                app.put("agentId", agentId);
                                app.put("computerName", computerName);
                                result.add(app);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void ingestApps(
            List<Map<String, Object>> apps,
            String tool,
            String toolDomain,
            String ingestUrl,
            String consoleDomain) throws IOException {

        List<Map<String, Object>> batch = new ArrayList<>();
        for (Map<String, Object> app : apps) {
            batch.add(toAppIngestionRecord(app, tool, toolDomain, consoleDomain));
        }
        sendBatch(batch, ingestUrl);
    }

    private static Map<String, Object> toAppIngestionRecord(
            Map<String, Object> app,
            String tool,
            String toolDomain,
            String consoleDomain) throws IOException {

        Map<String, Object> record = new HashMap<>();
        String computerName = getStringOrDefault(app, "computerName", null);
        String agentId      = getStringOrDefault(app, "agentId", null);
        String appName      = getStringOrDefault(app, "name", "unknown-app");

        String botName   = agentId != null ? agentId : (computerName != null ? computerName : "unknown-device");
        String deviceSeg = (computerName != null ? computerName : (agentId != null ? agentId : "unknown-device")).replaceAll("[^a-zA-Z0-9_\\-]+", "-");
        String appSeg    = appName.toLowerCase().replaceAll("[^a-z0-9_\\-]+", "-");

        record.put("path", "/sentinelone/apps/" + deviceSeg + "/" + appSeg);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");
        record.put("requestPayload", "{}");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(app));

        Map<String, String> reqH = new HashMap<>();
        reqH.put("host", toolDomain);
        reqH.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqH));

        Map<String, String> resH = new HashMap<>();
        resH.put("content-type", "application/json");
        record.put("responseHeaders", OBJECT_MAPPER.writeValueAsString(resH));

        record.put("time", String.valueOf(System.currentTimeMillis()));
        record.put("source", "MIRRORING");
        record.put("akto_account_id", String.valueOf(Context.accountId.get()));
        record.put("akto_vxlan_id", "");
        record.put("is_pending", "false");
        record.put("ip", ""); record.put("destIp", ""); record.put("direction", "");
        record.put("process_id", ""); record.put("socket_id", ""); record.put("daemonset_id", "");
        record.put("enabled_graph", "false");

        Map<String, String> tags = new HashMap<>();
        tags.put("gen-ai", "Gen AI");
        tags.put("source", "ENDPOINT");
        tags.put("connector", "SENTINELONE");
        tags.put("ai-agent", tool);
        tags.put("bot-name", botName);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tags));

        return record;
    }

    // ── Deep Visibility ───────────────────────────────────────────────────────

    /**
     * Run a SINGLE combined DV query for all tools to avoid rate limiting.
     * Query format: (processCmd contains "tool1" OR processName contains "tool1") OR 
     *               (processCmd contains "tool2" OR processName contains "tool2") ...
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> runDvQueryForAllTools(
            List<String> tools,
            String apiToken,
            String consoleUrl,
            int lookbackHours) throws IOException {

        // Build combined S1QL query for all tools
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 0; i < tools.size(); i++) {
            String tool = tools.get(i);
            if (i > 0) {
                queryBuilder.append(" OR ");
            }
            queryBuilder.append("(processCmd contains \"").append(tool).append("\"");
            queryBuilder.append(" OR processName contains \"").append(tool).append("\")");
        }
        String query = queryBuilder.toString();
        String toDate = java.time.Instant.now().toString();
        String fromDate = java.time.Instant.now().minus(lookbackHours, java.time.temporal.ChronoUnit.HOURS).toString();
        
        loggerMaker.info("SentinelOneExecutor: DV query: " + query, LogDb.DASHBOARD);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {

            // Step 1: init-query
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("fromDate", fromDate);
            body.put("toDate", toDate);
            body.put("limit", 1000);

            HttpPost post = new HttpPost(consoleUrl + "/web/api/v2.1/dv/init-query");
            post.setHeader("Authorization", "ApiToken " + apiToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            String queryId;
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    loggerMaker.error("DV init-query failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    throw new IOException("DV init-query failed: HTTP " + statusCode + " - " + responseBody);
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                queryId = json.path("data").path("queryId").asText(null);
                if (queryId == null || queryId.isEmpty()) {
                    throw new IOException("No queryId returned from SentinelOne.");
                }
            }

            // Step 2: poll query-status until FINISHED or terminal
            long deadline = System.currentTimeMillis() + DV_MAX_WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                HttpGet statusGet = new HttpGet(consoleUrl + "/web/api/v2.1/dv/query-status?queryId=" + queryId);
                statusGet.setHeader("Authorization", "ApiToken " + apiToken);
                try (CloseableHttpResponse response = httpClient.execute(statusGet)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    String responseState = json.path("data").path("responseState").asText("");
                    if ("FINISHED".equalsIgnoreCase(responseState) || "EMPTY_RESULTS".equalsIgnoreCase(responseState)) break;
                    if ("FAILED".equalsIgnoreCase(responseState) || "FAILED_CLIENT".equalsIgnoreCase(responseState)
                            || "TIMED_OUT".equalsIgnoreCase(responseState) || "QUERY_EXPIRED".equalsIgnoreCase(responseState)) {
                        throw new IOException("Query ended with state: " + responseState);
                    }
                }
                try { Thread.sleep(DV_POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            // Step 3: fetch events
            List<Map<String, Object>> sdlResults = new ArrayList<>();
            HttpGet eventsGet = new HttpGet(consoleUrl + "/web/api/v2.1/dv/events?queryId=" + queryId + "&limit=1000");
            eventsGet.setHeader("Authorization", "ApiToken " + apiToken);
            try (CloseableHttpResponse response = httpClient.execute(eventsGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    loggerMaker.error("DV events fetch failed: HTTP " + statusCode, LogDb.DASHBOARD);
                    throw new IOException("Failed to fetch events: HTTP " + statusCode);
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode data = json.path("data");
                if (data.isArray()) {
                    for (JsonNode event : data) {
                        sdlResults.add(OBJECT_MAPPER.convertValue(event, Map.class));
                    }
                }
            }

            loggerMaker.info("SentinelOneExecutor: DV query returned " + sdlResults.size() + " total event(s)", LogDb.DASHBOARD);
            return sdlResults;

        }
    }

    /**
     * Group events by which tool was detected in the process command/name.
     * An event can match multiple tools - we'll assign it to the first match.
     */
    private static Map<String, List<Map<String, Object>>> groupEventsByTool(
            List<Map<String, Object>> events,
            List<String> tools) {
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (String tool : tools) {
            result.put(tool, new ArrayList<>());
        }
        
        for (Map<String, Object> event : events) {
            String processCmd = getStringOrDefault(event, "processCmd", "").toLowerCase();
            String processName = getStringOrDefault(event, "processName", "").toLowerCase();
            
            // Assign to first matching tool
            boolean assigned = false;
            for (String tool : tools) {
                String toolLower = tool.toLowerCase();
                if (processCmd.contains(toolLower) || processName.contains(toolLower)) {
                    result.get(tool).add(event);
                    assigned = true;
                    break;
                }
            }
            
            // If no tool matched (shouldn't happen), assign to first tool
            if (!assigned && !tools.isEmpty()) {
                result.get(tools.get(0)).add(event);
            }
        }
        
        return result;
    }

    private static void ingestDvEvents(
            List<Map<String, Object>> events,
            String tool,
            String toolDomain,
            String ingestUrl,
            String consoleDomain) throws IOException {

        List<Map<String, Object>> batch = new ArrayList<>();
        for (Map<String, Object> event : events) {
            // Skip non-process events
            Object objType = event.get("objectType");
            if (objType != null && !"process".equalsIgnoreCase(objType.toString())) continue;
            batch.add(toDvIngestionRecord(event, tool, toolDomain, consoleDomain));
        }
        if (!batch.isEmpty()) sendBatch(batch, ingestUrl);
    }

    private static Map<String, Object> toDvIngestionRecord(
            Map<String, Object> event,
            String tool,
            String toolDomain,
            String consoleDomain) throws IOException {

        Map<String, Object> record = new HashMap<>();

        // Try common device field names used by SentinelOne
        String deviceName = getStringOrDefault(event, "endpointName", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "agentComputerName", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "computerName", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "hostname", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "srcIp", "unknown-device");

        String agentIdVal = getStringOrDefault(event, "agentId", null);
        if (agentIdVal == null) agentIdVal = getStringOrDefault(event, "id", null);
        String botName = agentIdVal != null ? agentIdVal : deviceName;
        String pathSeg = deviceName.replaceAll("[^a-zA-Z0-9_\\-]+", "-");

        record.put("path", "/sentinelone/dv-events/" + pathSeg);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");
        record.put("requestPayload", "{}");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(event));

        Map<String, String> reqH = new HashMap<>();
        reqH.put("host", toolDomain);
        reqH.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqH));

        Map<String, String> resH = new HashMap<>();
        resH.put("content-type", "application/json");
        record.put("responseHeaders", OBJECT_MAPPER.writeValueAsString(resH));

        Object ts = event.get("eventTime");
        if (ts == null) ts = event.get("timestamp");
        record.put("time", ts != null ? ts.toString() : String.valueOf(System.currentTimeMillis()));

        record.put("source", "MIRRORING");
        record.put("akto_account_id", String.valueOf(Context.accountId.get()));
        record.put("akto_vxlan_id", "");
        record.put("is_pending", "false");
        record.put("ip", ""); record.put("destIp", ""); record.put("direction", "");
        record.put("process_id", ""); record.put("socket_id", ""); record.put("daemonset_id", "");
        record.put("enabled_graph", "false");

        Map<String, String> tags = new HashMap<>();
        tags.put("gen-ai", "Gen AI");
        tags.put("source", "ENDPOINT");
        tags.put("connector", "SENTINELONE");
        tags.put("ai-agent", tool);
        tags.put("bot-name", botName);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tags));

        return record;
    }

    // ── Ingestion transport ───────────────────────────────────────────────────

    private static void sendBatch(List<Map<String, Object>> batch, String ingestUrl) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", batch);
        String json = OBJECT_MAPPER.writeValueAsString(payload);

        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(ingestUrl + "/api/ingestData");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                EntityUtils.consumeQuietly(resp.getEntity());
                if (status != 200) {
                    throw new IOException("Ingestion service returned HTTP " + status);
                }
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    static String normalizeUrl(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "sentinelone.com";
        try {
            java.net.URI uri = new java.net.URI(normalizeUrl(url));
            String host = uri.getHost();
            return host != null ? host : "sentinelone.com";
        } catch (Exception e) {
            return "sentinelone.com";
        }
    }

    private static String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
