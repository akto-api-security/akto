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
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SentinelOneExecutor extends AccountJobExecutor {

    public static final SentinelOneExecutor INSTANCE = new SentinelOneExecutor();

    private static final LoggerMaker loggerMaker = new LoggerMaker(SentinelOneExecutor.class, LogDb.DASHBOARD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS  = 30_000;
    private static final int SOCKET_TIMEOUT_MS   = 60_000;
    private static final int DV_POLL_INTERVAL_MS = 3_000;
    private static final int DV_MAX_WAIT_MS      = 60_000;
    private static final int DV_LOOKBACK_HOURS   = 6;

    public static final List<String> APPS_TOOL_LIST = Arrays.asList(
        "Claude",
        "Cursor",
        "Copilot",
        "Windsurf",
        "Antigravity",
        "Codex",
        "Ollama"
    );

    public static final List<String> DV_TOOL_LIST = Arrays.asList(
    "openclaw",
    "gemini",
    "ollama"
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
                    }
                }
            } catch (IOException e) {
                loggerMaker.error("SentinelOneExecutor: DV query failed — " + e.getMessage(), LogDb.DASHBOARD);
            }
        }

        // ── Pass 3: MCP Discovery via RemoteOps ───────────────────────────────
        try {
            discoverMCPConfigsAndSkills(integration);
        } catch (Exception e) {
            loggerMaker.error("SentinelOneExecutor: MCP discovery failed — " + e.getMessage(), LogDb.DASHBOARD);
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
                        entry.put("osType", node.path("osType").asText(""));
                        entry.put("osName", node.path("osName").asText(""));
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
        if (!batch.isEmpty()) {
            sendBatch(batch, ingestUrl);
            loggerMaker.info("SentinelOneExecutor: ingested " + batch.size() + " DV event(s) for " + tool, LogDb.DASHBOARD);
        } else {
            loggerMaker.info("SentinelOneExecutor: no process events to ingest for " + tool + " (filtered out " + events.size() + " non-process events)", LogDb.DASHBOARD);
        }
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
            String endpoint = ingestUrl + "/api/ingestData";
            HttpPost post = new HttpPost(endpoint);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            loggerMaker.info("SentinelOneExecutor: sending batch of " + batch.size() + " record(s) to " + endpoint, LogDb.DASHBOARD);
            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(resp.getEntity());
                if (status != 200) {
                    loggerMaker.error("SentinelOneExecutor: ingestion failed with HTTP " + status + ": " + responseBody, LogDb.DASHBOARD);
                    throw new IOException("Ingestion service returned HTTP " + status + ": " + responseBody);
                }
                loggerMaker.info("SentinelOneExecutor: batch sent successfully, HTTP " + status, LogDb.DASHBOARD);
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

    // ── MCP Configuration & Skill Discovery ───────────────────────────────────

    private static String uploadScriptFromClasspath(String scriptResourcePath, String apiToken, String consoleUrl) {
        String classpathResource = "/sentinelone/" + scriptResourcePath;
        
        try (java.io.InputStream scriptStream = SentinelOneExecutor.class.getResourceAsStream(classpathResource)) {
            if (scriptStream == null) {
                loggerMaker.error("Script not found in classpath: " + classpathResource, LogDb.DASHBOARD);
                return null;
            }

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                // Get site ID
                HttpGet getSite = new HttpGet(consoleUrl + "/web/api/v2.1/sites?limit=1");
                getSite.setHeader("Authorization", "ApiToken " + apiToken);
                String siteId;
                try (CloseableHttpResponse siteResp = httpClient.execute(getSite)) {
                    String siteBody = EntityUtils.toString(siteResp.getEntity());
                    JsonNode siteData = OBJECT_MAPPER.readTree(siteBody).path("data").path("sites");
                    if (!siteData.isArray() || siteData.size() == 0) {
                        loggerMaker.error("No sites found in SentinelOne", LogDb.DASHBOARD);
                        return null;
                    }
                    siteId = siteData.get(0).path("id").asText();
                }

                // Check if script already exists and delete it (overwrite mode)
                HttpGet getScripts = new HttpGet(consoleUrl + "/web/api/v2.1/remote-scripts?scriptName=" + 
                    java.net.URLEncoder.encode(scriptResourcePath, "UTF-8"));
                getScripts.setHeader("Authorization", "ApiToken " + apiToken);
                try (CloseableHttpResponse scriptsResp = httpClient.execute(getScripts)) {
                    String scriptsBody = EntityUtils.toString(scriptsResp.getEntity());
                    JsonNode scriptsData = OBJECT_MAPPER.readTree(scriptsBody).path("data");
                    if (scriptsData.isArray() && scriptsData.size() > 0) {
                        // Script exists, delete it first
                        for (JsonNode script : scriptsData) {
                            String existingScriptId = script.path("id").asText();
                            if (!existingScriptId.isEmpty()) {
                                loggerMaker.info("Deleting existing script: " + scriptResourcePath + " (ID: " + existingScriptId + ")", LogDb.DASHBOARD);
                                
                                Map<String, Object> deleteBody = new HashMap<>();
                                Map<String, Object> deleteFilter = new HashMap<>();
                                deleteFilter.put("ids", java.util.Collections.singletonList(existingScriptId));
                                deleteBody.put("filter", deleteFilter);
                                
                                HttpPost deletePost = new HttpPost(consoleUrl + "/web/api/v2.1/remote-scripts/delete");
                                deletePost.setHeader("Authorization", "ApiToken " + apiToken);
                                deletePost.setHeader("Content-Type", "application/json");
                                deletePost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(deleteBody), ContentType.APPLICATION_JSON));
                                
                                try (CloseableHttpResponse deleteResp = httpClient.execute(deletePost)) {
                                    int deleteStatus = deleteResp.getStatusLine().getStatusCode();
                                    if (deleteStatus != 200 && deleteStatus != 204) {
                                        loggerMaker.error("Failed to delete existing script: HTTP " + deleteStatus, LogDb.DASHBOARD);
                                    }
                                }
                            }
                        }
                    }
                }

                // Determine OS types based on script extension
                String osTypesValue = scriptResourcePath.endsWith(".ps1") ? "windows" : "linux,macos";

                // Upload script (multipart form-data)
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("scriptName", scriptResourcePath);
                builder.addTextBody("scriptType", "action");
                builder.addTextBody("scopeLevel", "site");
                builder.addTextBody("scopeId", siteId);
                builder.addTextBody("inputRequired", "false");
                builder.addTextBody("osTypes", osTypesValue);
                builder.addBinaryBody("file", scriptStream, ContentType.TEXT_PLAIN, scriptResourcePath);

                HttpPost post = new HttpPost(consoleUrl + "/web/api/v2.1/remote-scripts");
                post.setHeader("Authorization", "ApiToken " + apiToken);
                post.setEntity(builder.build());

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());
                    
                    if (statusCode != 200 && statusCode != 201) {
                        loggerMaker.error("Upload script failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                        return null;
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    String scriptId = json.path("data").path("id").asText();
                    loggerMaker.info("Script uploaded successfully: " + scriptResourcePath + " (ID: " + scriptId + ")", LogDb.DASHBOARD);
                    return scriptId;
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Failed to upload script: " + scriptResourcePath + " - " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        }
    }

    private static String executeRemoteScript(String scriptId, List<String> agentIds, String apiToken, String consoleUrl) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("scriptId", scriptId);
            data.put("outputDestination", "SentinelCloud");
            data.put("taskDescription", "MCP/Skill Discovery");

            Map<String, Object> filter = new HashMap<>();
            filter.put("ids", agentIds);

            Map<String, Object> body = new HashMap<>();
            body.put("data", data);
            body.put("filter", filter);

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpPost post = new HttpPost(consoleUrl + "/web/api/v2.1/remote-scripts/execute");
                post.setHeader("Authorization", "ApiToken " + apiToken);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());
                    
                    if (statusCode != 200 && statusCode != 201) {
                        loggerMaker.error("Execute script failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                        return null;
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    String parentTaskId = json.path("data").path("parentTaskId").asText();
                    loggerMaker.info("Script execution started: parentTaskId=" + parentTaskId, LogDb.DASHBOARD);
                    return parentTaskId;
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Failed to execute script: " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        }
    }

    private static Map<String, String> pollTaskStatus(String parentTaskId, String apiToken, String consoleUrl, int maxWaitMs) {
        Map<String, String> agentToTaskId = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        loggerMaker.info("Starting to poll task status for parentTaskId=" + parentTaskId, LogDb.DASHBOARD);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            int pollCount = 0;
            while (System.currentTimeMillis() - startTime < maxWaitMs) {
                pollCount++;
                HttpGet get = new HttpGet(consoleUrl + "/web/api/v2.1/remote-scripts/status?parent_task_id=" + parentTaskId);
                get.setHeader("Authorization", "ApiToken " + apiToken);
                
                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    JsonNode tasks = json.path("data");
                    
                    loggerMaker.info("Poll #" + pollCount + " for parentTaskId=" + parentTaskId + 
                        ", tasks returned: " + (tasks.isArray() ? tasks.size() : 0), LogDb.DASHBOARD);
                    
                    if (!tasks.isArray() || tasks.size() == 0) {
                        Thread.sleep(3000);
                        continue;
                    }
                    
                    boolean allCompleted = true;
                    int completedCount = 0;
                    int failedCount = 0;
                    int pendingCount = 0;
                    int runningCount = 0;
                    StringBuilder statusDetails = new StringBuilder();
                    
                    for (JsonNode task : tasks) {
                        String status = task.path("status").asText("");
                        String agentId = task.path("agentId").asText("");
                        String taskId = task.path("id").asText("");
                        String agentName = task.path("agentComputerName").asText("unknown");
                        
                        statusDetails.append(agentName).append("=").append(status).append("; ");
                        
                        if ("completed".equalsIgnoreCase(status)) {
                            completedCount++;
                        } else if ("failed".equalsIgnoreCase(status)) {
                            failedCount++;
                        } else if ("pending".equalsIgnoreCase(status)) {
                            pendingCount++;
                        } else if ("running".equalsIgnoreCase(status)) {
                            runningCount++;
                        }
                        
                        if (!"completed".equalsIgnoreCase(status) && !"failed".equalsIgnoreCase(status)) {
                            allCompleted = false;
                        }
                        
                        if ("completed".equalsIgnoreCase(status) && !agentId.isEmpty() && !taskId.isEmpty()) {
                            agentToTaskId.put(agentId, taskId);
                        }
                    }
                    
                    loggerMaker.info("Task status: completed=" + completedCount + ", failed=" + failedCount + 
                        ", pending=" + pendingCount + ", running=" + runningCount + 
                        ", allCompleted=" + allCompleted + " | Details: " + statusDetails.toString(), LogDb.DASHBOARD);
                    
                    if (allCompleted) {
                        loggerMaker.info("All tasks completed for parentTaskId=" + parentTaskId + 
                            ", returning " + agentToTaskId.size() + " task IDs", LogDb.DASHBOARD);
                        return agentToTaskId;
                    }
                }
                
                Thread.sleep(3000);
            }
            
            loggerMaker.error("Task polling timeout for parentTaskId=" + parentTaskId, LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.error("Failed to poll task status: " + e.getMessage(), LogDb.DASHBOARD);
        }
        
        return agentToTaskId;
    }

    private static Map<String, JsonNode> fetchScriptOutputsWithAgentId(Map<String, String> agentToTaskId, String apiToken, String consoleUrl) {
        Map<String, JsonNode> outputs = new HashMap<>();
        
        loggerMaker.info("Fetching script outputs for " + agentToTaskId.size() + " agent(s)", LogDb.DASHBOARD);
        loggerMaker.info("Agent-to-Task mapping: " + agentToTaskId.toString(), LogDb.DASHBOARD);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            Map<String, Object> data = new HashMap<>();
            data.put("taskIds", new ArrayList<>(agentToTaskId.values()));
            
            Map<String, Object> body = new HashMap<>();
            body.put("data", data);
            
            HttpPost post = new HttpPost(consoleUrl + "/web/api/v2.1/remote-scripts/fetch-files");
            post.setHeader("Authorization", "ApiToken " + apiToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                loggerMaker.info("fetch-files response: HTTP " + statusCode, LogDb.DASHBOARD);
                
                if (statusCode != 200) {
                    loggerMaker.error("fetch-files failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    return outputs;
                }
                
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode downloadLinks = json.path("data").path("download_links");
                
                if (!downloadLinks.isArray()) {
                    loggerMaker.error("No download links found in fetch-files response", LogDb.DASHBOARD);
                    return outputs;
                }
                
                loggerMaker.info("Found " + downloadLinks.size() + " download link(s)", LogDb.DASHBOARD);
                
                // Create reverse map: taskId -> agentId
                Map<String, String> taskToAgent = new HashMap<>();
                for (Map.Entry<String, String> entry : agentToTaskId.entrySet()) {
                    taskToAgent.put(entry.getValue(), entry.getKey());
                }
                
                // Download and parse each output
                int linkCount = 0;
                for (JsonNode linkNode : downloadLinks) {
                    linkCount++;
                    String url = linkNode.path("downloadUrl").asText("");
                    String taskId = linkNode.path("taskId").asText("");
                    String agentId = taskToAgent.get(taskId);
                    
                    if (url.isEmpty()) {
                        loggerMaker.info("Skipping empty download link #" + linkCount, LogDb.DASHBOARD);
                        continue;
                    }
                    
                    loggerMaker.info("Downloading output #" + linkCount + " from S3 for agentId=" + agentId, LogDb.DASHBOARD);
                    
                    HttpGet get = new HttpGet(url);
                    try (CloseableHttpResponse dlResp = httpClient.execute(get)) {
                        byte[] zipData = EntityUtils.toByteArray(dlResp.getEntity());
                        loggerMaker.info("Downloaded ZIP size: " + zipData.length + " bytes for agentId=" + agentId, LogDb.DASHBOARD);
                        
                        // Extract stdout from ZIP
                        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(zipData);
                             java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(bais)) {
                            
                            String stdoutContent = null;
                            String stderrContent = null;
                            
                            java.util.zip.ZipEntry entry;
                            while ((entry = zis.getNextEntry()) != null) {
                                String entryName = entry.getName();
                                loggerMaker.info("ZIP entry found: " + entryName + " for agentId=" + agentId, LogDb.DASHBOARD);
                                
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    baos.write(buffer, 0, len);
                                }
                                String content = new String(baos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                                
                                // Match stdout or stdout_*.txt (SentinelOne adds timestamps)
                                if (entryName.equals("stdout") || entryName.startsWith("stdout_") || entryName.startsWith("/stdout")) {
                                    stdoutContent = content;
                                    loggerMaker.info("Found stdout entry: " + entryName + ", length: " + content.length() + " bytes", LogDb.DASHBOARD);
                                } else if (entryName.equals("stderr") || entryName.startsWith("stderr_") || entryName.startsWith("/stderr")) {
                                    stderrContent = content;
                                    loggerMaker.info("Found stderr entry: " + entryName + ", length: " + content.length() + " bytes", LogDb.DASHBOARD);
                                }
                            }
                            
                            // Process stdout if found
                            if (stdoutContent != null && !stdoutContent.isEmpty()) {
                                loggerMaker.info("Processing stdout for agentId=" + agentId, LogDb.DASHBOARD);
                                try {
                                    JsonNode output = OBJECT_MAPPER.readTree(stdoutContent);
                                    if (agentId != null) {
                                        outputs.put(agentId, output);
                                    }
                                    loggerMaker.info("Successfully parsed stdout as JSON for agentId=" + agentId, LogDb.DASHBOARD);
                                } catch (Exception e) {
                                    loggerMaker.error("Failed to parse stdout as JSON: " + e.getMessage(), LogDb.DASHBOARD);
                                    loggerMaker.error("Full stdout content: " + stdoutContent, LogDb.DASHBOARD);
                                }
                            } else {
                                loggerMaker.error("No stdout found in ZIP for agentId=" + agentId, LogDb.DASHBOARD);
                                if (stderrContent != null && !stderrContent.isEmpty()) {
                                    loggerMaker.error("Stderr content: " + stderrContent, LogDb.DASHBOARD);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.error("Failed to fetch script outputs: " + e.getMessage(), LogDb.DASHBOARD);
        }
        
        return outputs;
    }


    public static Map<String, Object> discoverMCPConfigsAndSkills(SentinelOneIntegration integration) {
        Map<String, Object> results = new HashMap<>();
        results.put("mcp_configs", new ArrayList<>());
        results.put("skills", new ArrayList<>());
        
        String consoleUrl = normalizeUrl(integration.getConsoleUrl());
        String ingestUrl = normalizeUrl(integration.getDataIngestionUrl());
        String consoleDomain = extractDomain(integration.getConsoleUrl());
        String apiToken = integration.getApiToken();
        
        // Fetch all agents
        List<Map<String, Object>> agentList;
        try {
            agentList = fetchAgentList(apiToken, consoleUrl);
            if (agentList.isEmpty()) {
                loggerMaker.error("No agents found for MCP/skill discovery", LogDb.DASHBOARD);
                return results;
            }
        } catch (IOException e) {
            loggerMaker.error("Failed to fetch agents: " + e.getMessage(), LogDb.DASHBOARD);
            return results;
        }
        
        // Separate agents by OS type
        List<String> unixAgentIds = new ArrayList<>();
        List<String> windowsAgentIds = new ArrayList<>();
        
        for (Map<String, Object> agent : agentList) {
            String agentId = agent.get("id").toString();
            String agentName = getStringOrDefault(agent, "computerName", "unknown");
            String osType = getStringOrDefault(agent, "osType", "").toLowerCase();
            String osName = getStringOrDefault(agent, "osName", "").toLowerCase();
            
            loggerMaker.info("Agent OS detection: name=" + agentName + ", osType=" + osType + ", osName=" + osName, LogDb.DASHBOARD);
            
            if (osType.contains("windows") || osName.contains("windows")) {
                windowsAgentIds.add(agentId);
                loggerMaker.info("Classified as Windows: " + agentName, LogDb.DASHBOARD);
            } else {
                unixAgentIds.add(agentId);
                loggerMaker.info("Classified as Unix/Linux/macOS: " + agentName, LogDb.DASHBOARD);
            }
        }
        
        loggerMaker.info("Starting MCP/skill discovery: " + unixAgentIds.size() + " Unix/Linux/macOS agents, " + 
            windowsAgentIds.size() + " Windows agents", LogDb.DASHBOARD);
        
        // 30 minute timeout (cron runs every hour)
        int timeoutMs = 30 * 60 * 1000;
        
        // Batch size for large fleets
        int batchSize = 150;
        
        // Execute MCP config discovery
        Map<String, JsonNode> allMcpOutputs = new HashMap<>();
        
        // Unix agents with .sh script
        if (!unixAgentIds.isEmpty()) {
            String mcpScriptIdSh = uploadScriptFromClasspath("scan_mcp_configs.sh", apiToken, consoleUrl);
            if (mcpScriptIdSh != null) {
                Map<String, JsonNode> unixMcpOutputs = executeBatchedDiscovery(
                    mcpScriptIdSh, unixAgentIds, batchSize, timeoutMs, 
                    "MCP Config (Unix)", apiToken, consoleUrl, ingestUrl, consoleDomain, true);
                allMcpOutputs.putAll(unixMcpOutputs);
            }
        }
        
        // Windows agents with .ps1 script
        if (!windowsAgentIds.isEmpty()) {
            String mcpScriptIdPs1 = uploadScriptFromClasspath("scan_mcp_configs.ps1", apiToken, consoleUrl);
            if (mcpScriptIdPs1 != null) {
                Map<String, JsonNode> windowsMcpOutputs = executeBatchedDiscovery(
                    mcpScriptIdPs1, windowsAgentIds, batchSize, timeoutMs, 
                    "MCP Config (Windows)", apiToken, consoleUrl, ingestUrl, consoleDomain, true);
                allMcpOutputs.putAll(windowsMcpOutputs);
            }
        }
        
        if (!allMcpOutputs.isEmpty()) {
            ingestMCPDiscoveries(allMcpOutputs, ingestUrl, consoleDomain);
            results.put("mcp_configs", new ArrayList<>(allMcpOutputs.values()));
            loggerMaker.info("MCP config discovery completed: " + allMcpOutputs.size() + " total results", LogDb.DASHBOARD);
        }
        
        // Execute skill discovery
        Map<String, JsonNode> allSkillOutputs = new HashMap<>();
        
        // Unix agents with .sh script
        if (!unixAgentIds.isEmpty()) {
            String skillScriptIdSh = uploadScriptFromClasspath("scan_skills.sh", apiToken, consoleUrl);
            if (skillScriptIdSh != null) {
                Map<String, JsonNode> unixSkillOutputs = executeBatchedDiscovery(
                    skillScriptIdSh, unixAgentIds, batchSize, timeoutMs, 
                    "Skills (Unix)", apiToken, consoleUrl, ingestUrl, consoleDomain, false);
                allSkillOutputs.putAll(unixSkillOutputs);
            }
        }
        
        // Windows agents with .ps1 script
        if (!windowsAgentIds.isEmpty()) {
            String skillScriptIdPs1 = uploadScriptFromClasspath("scan_skills.ps1", apiToken, consoleUrl);
            if (skillScriptIdPs1 != null) {
                Map<String, JsonNode> windowsSkillOutputs = executeBatchedDiscovery(
                    skillScriptIdPs1, windowsAgentIds, batchSize, timeoutMs, 
                    "Skills (Windows)", apiToken, consoleUrl, ingestUrl, consoleDomain, false);
                allSkillOutputs.putAll(windowsSkillOutputs);
            }
        }
        
        if (!allSkillOutputs.isEmpty()) {
            ingestSkillDiscoveries(allSkillOutputs, ingestUrl, consoleDomain);
            results.put("skills", new ArrayList<>(allSkillOutputs.values()));
            loggerMaker.info("Skill discovery completed: " + allSkillOutputs.size() + " total results", LogDb.DASHBOARD);
        }
        
        return results;
    }

    private static Map<String, JsonNode> executeBatchedDiscovery(
            String scriptId, 
            List<String> agentIds, 
            int batchSize, 
            int timeoutMs,
            String description,
            String apiToken, 
            String consoleUrl,
            String ingestUrl,
            String consoleDomain,
            boolean isMcpConfig) {
        
        Map<String, JsonNode> allOutputs = new HashMap<>();
        int totalBatches = (int) Math.ceil((double) agentIds.size() / batchSize);
        
        loggerMaker.info(description + ": Processing " + agentIds.size() + " agents in " + totalBatches + " batch(es)", LogDb.DASHBOARD);
        
        for (int i = 0; i < agentIds.size(); i += batchSize) {
            int batchNum = (i / batchSize) + 1;
            List<String> batch = agentIds.subList(i, Math.min(i + batchSize, agentIds.size()));
            
            loggerMaker.info(description + " - Batch " + batchNum + "/" + totalBatches + 
                ": Executing on " + batch.size() + " agents", LogDb.DASHBOARD);
            
            String taskId = executeRemoteScript(scriptId, batch, apiToken, consoleUrl);
            if (taskId != null) {
                Map<String, String> tasks = pollTaskStatus(taskId, apiToken, consoleUrl, timeoutMs);
                if (!tasks.isEmpty()) {
                    Map<String, JsonNode> batchOutputs = fetchScriptOutputsWithAgentId(tasks, apiToken, consoleUrl);
                    allOutputs.putAll(batchOutputs);
                    
                    loggerMaker.info(description + " - Batch " + batchNum + "/" + totalBatches + 
                        ": Collected " + batchOutputs.size() + " results", LogDb.DASHBOARD);
                } else {
                    loggerMaker.error(description + " - Batch " + batchNum + "/" + totalBatches + 
                        ": No results returned (timeout or failure)", LogDb.DASHBOARD);
                }
            } else {
                loggerMaker.error(description + " - Batch " + batchNum + "/" + totalBatches + 
                    ": Failed to execute script", LogDb.DASHBOARD);
            }
        }
        
        loggerMaker.info(description + ": Completed all batches, total outputs: " + allOutputs.size(), LogDb.DASHBOARD);
        return allOutputs;
    }

    private static void ingestMCPDiscoveries(Map<String, JsonNode> discoveriesByAgent, String ingestUrl, String consoleDomain) {
        loggerMaker.info("Starting MCP discovery ingestion for " + discoveriesByAgent.size() + " agent(s)", LogDb.DASHBOARD);
        
        List<Map<String, Object>> batchData = new ArrayList<>();
        Map<String, ServerCollectionInfo> serverCollections = new HashMap<>();
        
        for (Map.Entry<String, JsonNode> entry : discoveriesByAgent.entrySet()) {
            String agentId = entry.getKey();
            JsonNode discovery = entry.getValue();
            
            String hostname = discovery.path("hostname").asText("unknown");
            String os = discovery.path("os").asText("unknown");
            String user = discovery.path("user").asText("unknown");
            JsonNode configsFound = discovery.path("configs_found");
            
            loggerMaker.info("Processing discovery from agentId=" + agentId + ", hostname=" + hostname + ", os=" + os, LogDb.DASHBOARD);
            
            if (!configsFound.isArray()) {
                loggerMaker.info("No configs_found array in discovery from " + hostname, LogDb.DASHBOARD);
                continue;
            }
            
            loggerMaker.info("Found " + configsFound.size() + " config file(s) from " + hostname, LogDb.DASHBOARD);
            
            for (JsonNode config : configsFound) {
                String path = config.path("path").asText("");
                String client = config.path("client").asText("unknown");
                long size = config.path("size").asLong(0);
                long modified = config.path("modified").asLong(0);
                JsonNode servers = config.path("servers");
                
                if (path.isEmpty()) continue;
                
                // Process each MCP server in this config file
                if (servers.isArray() && servers.size() > 0) {
                    for (JsonNode server : servers) {
                        String serverName = server.path("name").asText("");
                        String serverType = server.path("type").asText("unknown");
                        String command = server.path("command").asText("");
                        String url = server.path("url").asText("");
                        
                        if (serverName.isEmpty()) continue;
                        
                        String collectionName = client + "." + serverName;
                        collectionName = collectionName.toLowerCase();
                        
                        // Track server for collection creation
                        if (!serverCollections.containsKey(collectionName)) {
                            ServerCollectionInfo info = new ServerCollectionInfo();
                            info.collectionName = collectionName;
                            info.serverName = serverName;
                            info.clientType = client;
                            info.timestamp = modified;
                            info.command = command;
                            info.url = url;
                            info.type = serverType;
                            serverCollections.put(collectionName, info);
                        }
                        
                        // Build synthetic host for ingestion (matches collection name)
                        String syntheticHost = client + "." + serverName;
                        
                        // Create request headers (JSON string)
                        Map<String, String> reqHeaders = new HashMap<>();
                        reqHeaders.put("content-type", "application/json");
                        reqHeaders.put("host", syntheticHost);
                        reqHeaders.put("x-transport", "DISCOVERY");
                        reqHeaders.put("x-discovery-type", "mcp-config");
                        
                        // Create request payload (discovery metadata)
                        Map<String, Object> reqPayload = new HashMap<>();
                        reqPayload.put("file_path", path);
                        reqPayload.put("client_type", client);
                        reqPayload.put("server_name", serverName);
                        reqPayload.put("server_type", serverType);
                        reqPayload.put("command", command);
                        reqPayload.put("url", url);
                        reqPayload.put("file_size", size);
                        reqPayload.put("modified_time", modified);
                        reqPayload.put("os", os);
                        reqPayload.put("user", user);
                        
                        // Create tag JSON string with source=ENDPOINT and mcp-server label
                        Map<String, String> tagMap = new HashMap<>();
                        tagMap.put("source", "ENDPOINT");
                        tagMap.put("mcp-server", "MCP Server");
                        tagMap.put("bot-name", agentId);
                        
                        try {
                            String reqHeadersJson = OBJECT_MAPPER.writeValueAsString(reqHeaders);
                            String reqPayloadJson = OBJECT_MAPPER.writeValueAsString(reqPayload);
                            String tagJson = OBJECT_MAPPER.writeValueAsString(tagMap);
                            
                            Map<String, Object> batch = new HashMap<>();
                            batch.put("path", "https://" + syntheticHost + "/mcp");
                            batch.put("requestHeaders", reqHeadersJson);
                            batch.put("responseHeaders", "{}");
                            batch.put("method", "POST");
                            batch.put("requestPayload", reqPayloadJson);
                            batch.put("responsePayload", "{}");
                            batch.put("ip", "127.0.0.1");
                            batch.put("time", String.valueOf(modified));
                            batch.put("statusCode", "200");
                            batch.put("status", "OK");
                            batch.put("akto_account_id", "1000000");
                            batch.put("akto_vxlan_id", "0");
                            batch.put("is_pending", "false");
                            batch.put("source", "MIRRORING");
                            batch.put("tag", tagJson);
                            
                            batchData.add(batch);
                        } catch (Exception e) {
                            loggerMaker.error("Failed to serialize MCP server discovery: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }
            }
        }
        
        // Create collections for all discovered MCP servers
        if (!serverCollections.isEmpty()) {
            createMCPServerCollections(serverCollections, ingestUrl);
        }
        
        // Ingest discovery data
        if (!batchData.isEmpty()) {
            try {
                sendBatch(batchData, ingestUrl);
                loggerMaker.info("Ingested " + batchData.size() + " MCP server discoveries", LogDb.DASHBOARD);
            } catch (IOException e) {
                loggerMaker.error("Failed to ingest MCP discoveries: " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    private static class ServerCollectionInfo {
        String collectionName;
        String serverName;
        String clientType;
        long timestamp;
        String command;
        String url;
        String type;
    }

    private static void createMCPServerCollections(Map<String, ServerCollectionInfo> serverCollections, String ingestUrl) {
        loggerMaker.info("Creating collections for " + serverCollections.size() + " MCP server(s)", LogDb.DASHBOARD);
        
        // Build database abstractor URL from environment or use ingest URL base
        String dbAbstractorUrl;
        String dbAbstractorEnv = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (dbAbstractorEnv != null && !dbAbstractorEnv.isEmpty()) {
            dbAbstractorUrl = dbAbstractorEnv.endsWith("/") 
                ? dbAbstractorEnv.substring(0, dbAbstractorEnv.length() - 1) 
                : dbAbstractorEnv;
            dbAbstractorUrl += "/api/createCollectionForHostAndVpc";
            loggerMaker.info("Using DATABASE_ABSTRACTOR_SERVICE_URL: " + dbAbstractorUrl, LogDb.DASHBOARD);
        } else {
            // Fallback: derive from ingest URL
            dbAbstractorUrl = ingestUrl.replaceAll("/api/ingestData.*", "") + "/api/createCollectionForHostAndVpc";
        }
        
        loggerMaker.info("Creating collections for " + serverCollections.size() + " MCP server(s)", LogDb.DASHBOARD);
        
        for (ServerCollectionInfo info : serverCollections.values()) {
            try {
                // Generate unique collection ID (hash of collection name + timestamp)
                int colId = generateCollectionId(info.collectionName, info.timestamp);
                
                // Build tags list
                List<Map<String, Object>> tagsList = new ArrayList<>();
                
                // Add mcp-server tag
                Map<String, Object> mcpServerTag = new HashMap<>();
                mcpServerTag.put("lastUpdatedTs", info.timestamp);
                mcpServerTag.put("keyName", "mcp-server");
                mcpServerTag.put("value", "MCP Server");
                mcpServerTag.put("source", "KUBERNETES");
                tagsList.add(mcpServerTag);
                
                // Add source tag
                Map<String, Object> sourceTag = new HashMap<>();
                sourceTag.put("lastUpdatedTs", info.timestamp);
                sourceTag.put("keyName", "source");
                sourceTag.put("value", "ENDPOINT");
                sourceTag.put("source", "KUBERNETES");
                tagsList.add(sourceTag);
                
                // Add mcp-client tag
                if (info.clientType != null && !info.clientType.isEmpty()) {
                    Map<String, Object> clientTag = new HashMap<>();
                    clientTag.put("lastUpdatedTs", info.timestamp);
                    clientTag.put("keyName", "mcp-client");
                    clientTag.put("value", info.clientType);
                    clientTag.put("source", "KUBERNETES");
                    tagsList.add(clientTag);
                }
                
                // Build request
                Map<String, Object> request = new HashMap<>();
                request.put("colId", colId);
                request.put("host", info.collectionName);
                request.put("tagsList", tagsList);
                
                String requestJson = OBJECT_MAPPER.writeValueAsString(request);
                
                // Send request
                RequestConfig cfg = RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                    .setSocketTimeout(SOCKET_TIMEOUT_MS)
                    .build();
                
                try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                    HttpPost post = new HttpPost(dbAbstractorUrl);
                    post.setHeader("Content-Type", "application/json");
                    
                    String aktoApiToken = System.getenv("AKTO_API_TOKEN");
                    if (aktoApiToken != null && !aktoApiToken.isEmpty()) {
                        post.setHeader("Authorization", aktoApiToken);
                    }
                    
                    post.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
                    
                    try (CloseableHttpResponse resp = client.execute(post)) {
                        int status = resp.getStatusLine().getStatusCode();
                        String responseBody = EntityUtils.toString(resp.getEntity());
                        
                        if (status == 200) {
                            loggerMaker.info("Created collection for MCP server: " + info.collectionName, LogDb.DASHBOARD);
                        } else {
                            loggerMaker.error("Failed to create collection: HTTP " + status + " - " + responseBody, LogDb.DASHBOARD);
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.error("Failed to create collection for " + info.collectionName + ": " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    private static int generateCollectionId(String collectionName, long timestamp) {
        String combined = collectionName + timestamp;
        int hash = combined.hashCode();
        return Math.abs(hash);
    }

    private static void ingestSkillDiscoveries(Map<String, JsonNode> discoveriesByAgent, String ingestUrl, String consoleDomain) {
        List<Map<String, Object>> batchData = new ArrayList<>();
        Set<SkillCollectionInfo> skillCollections = new HashSet<>();
        
        loggerMaker.info("Starting skill discovery ingestion for " + discoveriesByAgent.size() + " agent(s)", LogDb.DASHBOARD);
        
        for (Map.Entry<String, JsonNode> entry : discoveriesByAgent.entrySet()) {
            String agentId = entry.getKey();
            JsonNode discovery = entry.getValue();
            String hostname = discovery.path("hostname").asText("unknown");
            String os = discovery.path("os").asText("unknown");
            String user = discovery.path("user").asText("unknown");
            JsonNode skillsFound = discovery.path("skills_found");
            
            if (!skillsFound.isArray()) continue;
            
            loggerMaker.info("Processing " + skillsFound.size() + " skill(s) from agentId=" + agentId + ", hostname=" + hostname, LogDb.DASHBOARD);
            
            for (JsonNode skill : skillsFound) {
                String path = skill.path("path").asText("");
                String agent = skill.path("agent").asText("unknown");
                long size = skill.path("size").asLong(0);
                long modified = skill.path("modified").asLong(0);
                
                if (path.isEmpty()) continue;
                
                String skillName = extractSkillName(path);
                String collectionName = hostname + "." + agent + "." + skillName;
                collectionName = collectionName.toLowerCase();
                
                skillCollections.add(new SkillCollectionInfo(collectionName, agent, System.currentTimeMillis()));
                
                String syntheticHost = collectionName;
                
                Map<String, String> reqHeaders = new HashMap<>();
                reqHeaders.put("content-type", "text/markdown");
                reqHeaders.put("host", syntheticHost);
                reqHeaders.put("x-transport", "SKILL");
                reqHeaders.put("x-skill-name", skillName);
                reqHeaders.put("x-agent-type", agent);
                
                Map<String, String> tagMap = new HashMap<>();
                tagMap.put("source", "ENDPOINT");
                tagMap.put("bot-name", agentId);
                tagMap.put("mcp-server", "MCP Server");
                tagMap.put("mcp-client", agent);
                
                try {
                    String reqHeadersJson = OBJECT_MAPPER.writeValueAsString(reqHeaders);
                    String tagJson = OBJECT_MAPPER.writeValueAsString(tagMap);
                    
                    Map<String, Object> batch = new HashMap<>();
                    batch.put("path", "https://" + syntheticHost + "/skill/" + skillName);
                    batch.put("requestHeaders", reqHeadersJson);
                    batch.put("responseHeaders", "{}");
                    batch.put("method", "GET");
                    batch.put("requestPayload", "");
                    batch.put("responsePayload", "Skill file discovered at: " + path);
                    batch.put("ip", "127.0.0.1");
                    batch.put("time", String.valueOf(System.currentTimeMillis() / 1000));
                    batch.put("statusCode", "200");
                    batch.put("status", "OK");
                    batch.put("akto_account_id", "1000000");
                    batch.put("akto_vxlan_id", "0");
                    batch.put("is_pending", "false");
                    batch.put("source", "MIRRORING");
                    batch.put("tag", tagJson);
                    
                    batchData.add(batch);
                } catch (Exception e) {
                    loggerMaker.error("Failed to serialize skill discovery: " + e.getMessage(), LogDb.DASHBOARD);
                }
            }
        }
        
        if (!batchData.isEmpty()) {
            try {
                sendBatch(batchData, ingestUrl);
                loggerMaker.info("Ingested " + batchData.size() + " skill discoveries", LogDb.DASHBOARD);
            } catch (IOException e) {
                loggerMaker.error("Failed to ingest skill discoveries: " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
        
        if (!skillCollections.isEmpty()) {
            createSkillCollections(skillCollections);
        }
    }
    
    private static String extractSkillName(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        fileName = fileName.substring(filePath.lastIndexOf('\\') + 1);
        
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }
        
        fileName = fileName.toLowerCase()
            .replaceAll("[\\s_]+", "-")
            .replaceAll("[^a-z0-9-]", "");
        
        return fileName;
    }
    
    private static void createSkillCollections(Set<SkillCollectionInfo> skillCollections) {
        String dbAbstractorUrl = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (dbAbstractorUrl == null || dbAbstractorUrl.isEmpty()) {
            dbAbstractorUrl = "http://localhost:5678";
        }
        dbAbstractorUrl = normalizeUrl(dbAbstractorUrl) + "/api/createCollectionForHostAndVpc";
        
        loggerMaker.info("Using DATABASE_ABSTRACTOR_SERVICE_URL: " + dbAbstractorUrl, LogDb.DASHBOARD);
        loggerMaker.info("Creating collections for " + skillCollections.size() + " skill(s)", LogDb.DASHBOARD);
        
        for (SkillCollectionInfo info : skillCollections) {
            try {
                List<Map<String, Object>> tagsList = new ArrayList<>();
                
                Map<String, Object> mcpServerTag = new HashMap<>();
                mcpServerTag.put("lastUpdatedTs", info.timestamp);
                mcpServerTag.put("keyName", "mcp-server");
                mcpServerTag.put("value", "MCP Server");
                mcpServerTag.put("source", "KUBERNETES");
                tagsList.add(mcpServerTag);
                
                Map<String, Object> sourceTag = new HashMap<>();
                sourceTag.put("lastUpdatedTs", info.timestamp);
                sourceTag.put("keyName", "source");
                sourceTag.put("value", "ENDPOINT");
                sourceTag.put("source", "KUBERNETES");
                tagsList.add(sourceTag);
                
                if (info.agentType != null && !info.agentType.isEmpty()) {
                    Map<String, Object> clientTag = new HashMap<>();
                    clientTag.put("lastUpdatedTs", info.timestamp);
                    clientTag.put("keyName", "mcp-client");
                    clientTag.put("value", info.agentType);
                    clientTag.put("source", "KUBERNETES");
                    tagsList.add(clientTag);
                }
                
                Map<String, Object> request = new HashMap<>();
                request.put("colId", generateCollectionId(info.collectionName, info.timestamp));
                request.put("host", info.collectionName);
                request.put("tagsList", tagsList);
                
                String requestJson = OBJECT_MAPPER.writeValueAsString(request);
                
                RequestConfig cfg = RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                    .setSocketTimeout(SOCKET_TIMEOUT_MS)
                    .build();
                
                try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                    HttpPost post = new HttpPost(dbAbstractorUrl);
                    post.setHeader("Content-Type", "application/json");
                    
                    String aktoApiToken = System.getenv("AKTO_API_TOKEN");
                    if (aktoApiToken != null && !aktoApiToken.isEmpty()) {
                        post.setHeader("Authorization", aktoApiToken);
                    }
                    
                    post.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
                    
                    try (CloseableHttpResponse resp = client.execute(post)) {
                        int status = resp.getStatusLine().getStatusCode();
                        String responseBody = EntityUtils.toString(resp.getEntity());
                        
                        if (status == 200) {
                            loggerMaker.info("Created collection for skill: " + info.collectionName, LogDb.DASHBOARD);
                        } else {
                            loggerMaker.error("Failed to create collection: HTTP " + status + " - " + responseBody, LogDb.DASHBOARD);
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.error("Failed to create collection for " + info.collectionName + ": " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }
    
    private static class SkillCollectionInfo {
        String collectionName;
        String agentType;
        long timestamp;
        
        SkillCollectionInfo(String collectionName, String agentType, long timestamp) {
            this.collectionName = collectionName;
            this.agentType = agentType;
            this.timestamp = timestamp;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SkillCollectionInfo that = (SkillCollectionInfo) o;
            return collectionName.equals(that.collectionName);
        }
        
        @Override
        public int hashCode() {
            return collectionName.hashCode();
        }
    }
}
