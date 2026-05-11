package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.exception.RetryableJobException;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MicrosoftDefenderExecutor extends AccountJobExecutor {

    public static final MicrosoftDefenderExecutor INSTANCE = new MicrosoftDefenderExecutor();

    private static final LoggerMaker logger = new LoggerMaker(MicrosoftDefenderExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CURSOR_KEY = "lastQueriedAt";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS = 60_000;
    private static final int BATCH_SIZE = 500;
    private static final int LIVE_RESPONSE_POLL_INTERVAL_MS = 5_000;
    private static final int LIVE_RESPONSE_MAX_WAIT_MS = 30 * 60 * 1000; // 30 minutes
    private static final int CANCEL_RETRY_WAIT_MS = 10_000;

    public static final List<String> APPS_TOOL_LIST = Arrays.asList(
        "Claude", "Cursor", "Copilot", "Windsurf", "Antigravity", "Codex", "Ollama"
    );

    public static final List<String> PROCESS_TOOL_LIST = Arrays.asList(
        "openclaw", "clawdbot", "moltbot", "gateway", "claude"
    );

    private static final String SOFTWARE_INVENTORY_QUERY =
        "DeviceTvmSoftwareInventory" +
        "| where SoftwareName has_any (\"Claude\", \"Cursor\", \"Copilot\", \"Windsurf\", \"Antigravity\", \"Codex\", \"Ollama\")" +
        "| project DeviceId, DeviceName, SoftwareName, SoftwareVersion, SoftwareVendor" +
        "| order by DeviceName asc";

    private MicrosoftDefenderExecutor() {}

    @Override
    protected void runJob(AccountJob job) throws Exception {
        logger.info("Executing Microsoft Defender job: jobId={}, subType={}", job.getId(), job.getSubType());

        Map<String, Object> jobConfig = job.getConfig();
        if (jobConfig == null || jobConfig.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        String tenantId = getRequiredString(jobConfig, "tenantId");
        String clientId = getRequiredString(jobConfig, "clientId");
        String clientSecret = getRequiredString(jobConfig, "clientSecret");
        String dataIngestionUrl = getRequiredString(jobConfig, "dataIngestionUrl");

        String normalizedUrl = dataIngestionUrl.endsWith("/")
            ? dataIngestionUrl.substring(0, dataIngestionUrl.length() - 1)
            : dataIngestionUrl;

        String accessToken = fetchAccessToken(tenantId, clientId, clientSecret);
        Instant now = Instant.now();

        // Phase 1: Installed software inventory
        try {
            runSoftwareInventoryPhase(accessToken, normalizedUrl, job);
        } catch (Exception e) {
            logger.error("Defender software inventory phase failed for jobId={}: {}", job.getId(), e.getMessage());
        }

        // Phase 2: Process events (with cursor)
        Object cursorVal = jobConfig.get(CURSOR_KEY);
        String lastQueriedAt = cursorVal != null ? cursorVal.toString() : null;
        try {
            runProcessEventsPhase(accessToken, normalizedUrl, job, lastQueriedAt);
        } catch (Exception e) {
            logger.error("Defender process events phase failed for jobId={}: {}", job.getId(), e.getMessage());
        }

        // Phase 3: MCP config + skills discovery via Live Response
        try {
            discoverMCPConfigsAndSkills(accessToken, normalizedUrl, job);
        } catch (Exception e) {
            logger.error("Defender MCP discovery phase failed for jobId={}: {}", job.getId(), e.getMessage());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("config." + CURSOR_KEY, now.toString());
        CyborgApiClient.updateJob(job.getId(), updates);

        logger.info("Microsoft Defender job completed: jobId={}", job.getId());
    }

    // ── Phase 1: Software inventory ───────────────────────────────────────────

    private void runSoftwareInventoryPhase(String accessToken, String normalizedUrl, AccountJob job)
            throws IOException, RetryableJobException {
        List<Map<String, Object>> results = runHuntingQuery(accessToken, SOFTWARE_INVENTORY_QUERY);
        logger.info("Defender software inventory returned {} results for jobId={}", results.size(), job.getId());

        List<Map<String, Object>> batch = new ArrayList<>();
        int batchNum = 0;
        for (Map<String, Object> row : results) {
            batch.add(toSoftwareIngestionRecord(row, job.getAccountId()));
            if (batch.size() >= BATCH_SIZE) {
                postBatch(normalizedUrl, batch, ++batchNum, job);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            postBatch(normalizedUrl, batch, ++batchNum, job);
        }
    }

    private Map<String, Object> toSoftwareIngestionRecord(Map<String, Object> row, int accountId) throws IOException {
        Map<String, Object> record = new HashMap<>();

        String deviceName = getStringOrDefault(row, "DeviceName", "unknown-device");
        String softwareName = getStringOrDefault(row, "SoftwareName", "unknown");
        String slug = toolSlug(softwareName);
        String host = deviceName + "." + slug + ".defender.microsoft.com";

        record.put("path", "/defender/software-inventory/" + softwareName + "/" + deviceName);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");
        record.put("requestPayload", "{}");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(row));

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", host);
        requestHeaders.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(requestHeaders));
        record.put("responseHeaders", "{\"content-type\":\"application/json\"}");

        record.put("time", String.valueOf(System.currentTimeMillis()));
        record.put("source", "MIRRORING");
        record.put("akto_account_id", String.valueOf(accountId));
        record.put("akto_vxlan_id", "");
        record.put("is_pending", "false");
        record.put("ip", "");
        record.put("destIp", "");
        record.put("direction", "");
        record.put("process_id", "");
        record.put("socket_id", "");
        record.put("daemonset_id", "");
        record.put("enabled_graph", "false");

        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("gen-ai", "Gen AI");
        tagMap.put("source", "ENDPOINT");
        tagMap.put("connector", "MICROSOFT_DEFENDER");
        tagMap.put("ai-agent", slug);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    // ── Phase 2: Process events ───────────────────────────────────────────────

    private void runProcessEventsPhase(String accessToken, String normalizedUrl, AccountJob job, String lastQueriedAt)
            throws IOException, RetryableJobException {
        String kqlQuery = buildKqlQuery(lastQueriedAt);
        logger.debug("Defender process events KQL for jobId={}: {}", job.getId(), kqlQuery);

        List<Map<String, Object>> results = runHuntingQuery(accessToken, kqlQuery);
        logger.info("Defender process events returned {} results for jobId={}", results.size(), job.getId());

        List<Map<String, Object>> batch = new ArrayList<>();
        int batchNum = 0;
        for (Map<String, Object> row : results) {
            batch.add(toProcessIngestionRecord(row, job.getAccountId()));
            if (batch.size() >= BATCH_SIZE) {
                postBatch(normalizedUrl, batch, ++batchNum, job);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            postBatch(normalizedUrl, batch, ++batchNum, job);
        }
    }

    private Map<String, Object> toProcessIngestionRecord(Map<String, Object> row, int accountId) throws IOException {
        Map<String, Object> record = new HashMap<>();

        String deviceName = getStringOrDefault(row, "DeviceName", "unknown-device");
        String processCommandLine = getStringOrDefault(row, "ProcessCommandLine", "");
        String fileName = getStringOrDefault(row, "FileName", "");
        String folderPath = getStringOrDefault(row, "FolderPath", "");
        String initiatingCmdLine = getStringOrDefault(row, "InitiatingProcessCommandLine", "");
        String initiatingFileName = getStringOrDefault(row, "InitiatingProcessFileName", "");
        // Rank fields by signal authority: parent process > self file/path > self command line.
        // ProcessCommandLine can contain incidental matches (e.g. tasklist patterns), so it's the
        // weakest signal and only used as a last resort.
        String tool = detectToolFirst(initiatingFileName, initiatingCmdLine, fileName, folderPath, processCommandLine);
        String host = deviceName + "." + tool + ".defender.microsoft.com";

        record.put("path", "/defender/process-events/" + tool + "/" + deviceName);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");
        record.put("requestPayload", processCommandLine);
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(row));

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", host);
        requestHeaders.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(requestHeaders));
        record.put("responseHeaders", "{\"content-type\":\"application/json\"}");

        Object tsVal = row.get("Timestamp");
        record.put("time", tsVal != null ? tsVal.toString() : String.valueOf(System.currentTimeMillis()));
        record.put("source", "MIRRORING");
        record.put("akto_account_id", String.valueOf(accountId));
        record.put("akto_vxlan_id", "");
        record.put("is_pending", "false");
        record.put("ip", "");
        record.put("destIp", "");
        record.put("direction", "");
        record.put("process_id", "");
        record.put("socket_id", "");
        record.put("daemonset_id", "");
        record.put("enabled_graph", "false");

        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("gen-ai", "Gen AI");
        tagMap.put("source", "ENDPOINT");
        tagMap.put("connector", "MICROSOFT_DEFENDER");
        tagMap.put("ai-agent", tool);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    // ── Phase 3: MCP + Skills discovery via Live Response ────────────────────

    private void discoverMCPConfigsAndSkills(String accessToken, String normalizedUrl, AccountJob job) throws Exception {
        List<Map<String, Object>> devices = fetchDeviceList(accessToken);
        if (devices.isEmpty()) {
            logger.info("No devices found for MCP discovery, jobId={}", job.getId());
            return;
        }

        List<String> unixDeviceIds = new ArrayList<>();
        List<String> windowsDeviceIds = new ArrayList<>();
        Map<String, String> deviceNames = new HashMap<>();

        for (Map<String, Object> device : devices) {
            String deviceId = getStringOrDefault(device, "id", "");
            String deviceName = getStringOrDefault(device, "computerDnsName", "unknown");
            String osPlatform = getStringOrDefault(device, "osPlatform", "").toLowerCase();
            if (deviceId.isEmpty()) continue;

            deviceNames.put(deviceId, deviceName);
            if (osPlatform.contains("windows")) {
                windowsDeviceIds.add(deviceId);
            } else {
                unixDeviceIds.add(deviceId);
            }
        }

        logger.info("Defender MCP discovery: {} Unix, {} Windows devices, jobId={}",
            unixDeviceIds.size(), windowsDeviceIds.size(), job.getId());

        updateJobHeartbeat(job);

        final String mcpShScript   = uploadScript(accessToken, "scan_mcp_configs.sh");
        final String mcpPs1Script  = uploadScript(accessToken, "scan_mcp_configs.ps1");
        final String skillsShScript  = uploadScript(accessToken, "scan_skills.sh");
        final String skillsPs1Script = uploadScript(accessToken, "scan_skills.ps1");

        final Map<String, JsonNode> allMcpOutputs = new ConcurrentHashMap<>();
        final Map<String, JsonNode> allSkillOutputs = new ConcurrentHashMap<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> mcpUnixFuture = (mcpShScript == null || unixDeviceIds.isEmpty()) ? null : pool.submit(new Runnable() {
            @Override
            public void run() {
                allMcpOutputs.putAll(runScriptOnDevices(accessToken, mcpShScript, unixDeviceIds, deviceNames, "MCP Config (Unix)", job));
            }
        });

        Future<?> mcpWindowsFuture = (mcpPs1Script == null || windowsDeviceIds.isEmpty()) ? null : pool.submit(new Runnable() {
            @Override
            public void run() {
                allMcpOutputs.putAll(runScriptOnDevices(accessToken, mcpPs1Script, windowsDeviceIds, deviceNames, "MCP Config (Windows)", job));
            }
        });

        waitForFuture(mcpUnixFuture, "MCP Config (Unix)");
        waitForFuture(mcpWindowsFuture, "MCP Config (Windows)");

        Future<?> skillsUnixFuture = (skillsShScript == null || unixDeviceIds.isEmpty()) ? null : pool.submit(new Runnable() {
            @Override
            public void run() {
                allSkillOutputs.putAll(runScriptOnDevices(accessToken, skillsShScript, unixDeviceIds, deviceNames, "Skills (Unix)", job));
            }
        });

        Future<?> skillsWindowsFuture = (skillsPs1Script == null || windowsDeviceIds.isEmpty()) ? null : pool.submit(new Runnable() {
            @Override
            public void run() {
                allSkillOutputs.putAll(runScriptOnDevices(accessToken, skillsPs1Script, windowsDeviceIds, deviceNames, "Skills (Windows)", job));
            }
        });

        waitForFuture(skillsUnixFuture, "Skills (Unix)");
        waitForFuture(skillsWindowsFuture, "Skills (Windows)");
        pool.shutdown();

        if (!allMcpOutputs.isEmpty()) {
            ingestMCPDiscoveries(allMcpOutputs, normalizedUrl, deviceNames);
        }
        if (!allSkillOutputs.isEmpty()) {
            ingestSkillDiscoveries(allSkillOutputs, normalizedUrl, deviceNames);
        }
    }

    private Map<String, JsonNode> runScriptOnDevices(String accessToken, String scriptName,
            List<String> deviceIds, Map<String, String> deviceNames, String description, AccountJob job) {
        Map<String, JsonNode> outputs = new HashMap<>();

        for (String deviceId : deviceIds) {
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            try {
                updateJobHeartbeat(job);

                RequestConfig cfg = RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                    .setSocketTimeout(SOCKET_TIMEOUT_MS)
                    .build();

                try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                    String actionId = submitLiveResponseAction(httpClient, accessToken, deviceId, scriptName);
                    if (actionId == null) {
                        String existingId = getActiveActionId(httpClient, accessToken, deviceId);
                        if (existingId != null) {
                            cancelLiveResponseAction(httpClient, accessToken, existingId);
                            try { Thread.sleep(CANCEL_RETRY_WAIT_MS); } catch (InterruptedException ignored) {}
                        }
                        actionId = submitLiveResponseAction(httpClient, accessToken, deviceId, scriptName);
                    }
                    if (actionId == null) {
                        logger.error("{}: Could not submit Live Response action on device {}", description, deviceName);
                        continue;
                    }

                    Map<String, Object> finalStatus = pollActionUntilDone(httpClient, accessToken, actionId);
                    String status = finalStatus.getOrDefault("status", "unknown").toString();
                    if (!"Succeeded".equals(status)) {
                        logger.error("{}: Action on device {} finished with status {}", description, deviceName, status);
                        continue;
                    }

                    String downloadUrl = getActionOutputDownloadLink(httpClient, accessToken, actionId);
                    if (downloadUrl == null) {
                        logger.error("{}: No download link for actionId={} on device {}", description, actionId, deviceName);
                        continue;
                    }

                    JsonNode output = downloadAndParseOutput(httpClient, downloadUrl);
                    if (output != null) {
                        outputs.put(deviceId, output);
                        logger.info("{}: Collected output from device {}", description, deviceName);
                    }
                }
            } catch (Exception e) {
                logger.error("{}: Error processing device {}: {}", description, deviceName, e.getMessage());
            }
        }
        return outputs;
    }

    // ── MCP ingestion ─────────────────────────────────────────────────────────

    private void ingestMCPDiscoveries(Map<String, JsonNode> discoveriesByDevice, String normalizedUrl,
            Map<String, String> deviceNames) {
        logger.info("Starting Defender MCP ingestion for {} device(s)", discoveriesByDevice.size());

        List<Map<String, Object>> batchData = new ArrayList<>();
        Map<String, ServerCollectionInfo> serverCollections = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : discoveriesByDevice.entrySet()) {
            String deviceId = entry.getKey();
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            JsonNode discovery = entry.getValue();

            String hostname = discovery.path("hostname").asText("unknown");
            String os = discovery.path("os").asText("unknown");
            String user = discovery.path("user").asText("unknown");
            JsonNode configsFound = discovery.path("configs_found");

            if (!configsFound.isArray()) continue;

            for (JsonNode config : configsFound) {
                String configPath = config.path("path").asText("");
                String client = config.path("client").asText("unknown");
                long size = config.path("size").asLong(0);
                long modified = config.path("modified").asLong(0);
                JsonNode servers = config.path("servers");

                if (configPath.isEmpty()) continue;

                String clientSlug = clientTypeSlug(client);
                String syntheticHostBase = deviceName + "." + clientSlug + ".defender.microsoft.com";

                if (!servers.isArray() || servers.size() == 0) continue;

                for (JsonNode server : servers) {
                    String serverName = server.path("name").asText("");
                    String serverType = server.path("type").asText("unknown");
                    String command = server.path("command").asText("");
                    String url = server.path("url").asText("");

                    if (serverName.isEmpty()) continue;

                    String collectionName = serverName.toLowerCase();

                    if (!serverCollections.containsKey(collectionName)) {
                        ServerCollectionInfo info = new ServerCollectionInfo();
                        info.collectionName = collectionName;
                        info.serverName = serverName;
                        info.clientType = clientSlug;
                        info.timestamp = modified;
                        info.command = command;
                        info.url = url;
                        info.type = serverType;
                        serverCollections.put(collectionName, info);
                    }

                    Map<String, String> reqHeaders = new HashMap<>();
                    reqHeaders.put("content-type", "application/json");
                    reqHeaders.put("host", syntheticHostBase);
                    reqHeaders.put("x-transport", "DISCOVERY");
                    reqHeaders.put("x-discovery-type", "mcp-config");

                    Map<String, Object> reqPayload = new HashMap<>();
                    reqPayload.put("file_path", configPath);
                    reqPayload.put("client_type", clientSlug);
                    reqPayload.put("server_name", serverName);
                    reqPayload.put("server_type", serverType);
                    reqPayload.put("command", command);
                    reqPayload.put("url", url);
                    reqPayload.put("file_size", size);
                    reqPayload.put("modified_time", modified);
                    reqPayload.put("os", os);
                    reqPayload.put("user", user);

                    Map<String, String> tagMap = new HashMap<>();
                    tagMap.put("source", "ENDPOINT");
                    tagMap.put("connector", "MICROSOFT_DEFENDER");
                    tagMap.put("mcp-server", "MCP Server");
                    tagMap.put("ai-agent", clientSlug);

                    try {
                        Map<String, Object> batch = new HashMap<>();
                        batch.put("path", "https://" + syntheticHostBase + "/mcp/" + serverName);
                        batch.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqHeaders));
                        batch.put("responseHeaders", "{}");
                        batch.put("method", "POST");
                        batch.put("requestPayload", OBJECT_MAPPER.writeValueAsString(reqPayload));
                        batch.put("responsePayload", "{}");
                        batch.put("ip", "");
                        batch.put("time", String.valueOf(modified));
                        batch.put("statusCode", "200");
                        batch.put("status", "OK");
                        batch.put("akto_account_id", "1000000");
                        batch.put("akto_vxlan_id", "0");
                        batch.put("is_pending", "false");
                        batch.put("source", "MIRRORING");
                        batch.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));
                        batchData.add(batch);
                    } catch (Exception e) {
                        logger.error("Failed to serialize MCP server discovery: {}", e.getMessage());
                    }
                }
            }
        }

        if (!serverCollections.isEmpty()) {
            createMCPServerCollections(serverCollections, normalizedUrl);
        }

        if (!batchData.isEmpty()) {
            try {
                sendBatch(batchData, normalizedUrl);
                logger.info("Ingested {} Defender MCP server discoveries", batchData.size());
            } catch (IOException e) {
                logger.error("Failed to ingest Defender MCP discoveries: {}", e.getMessage());
            }
        }
    }

    // ── Skills ingestion ──────────────────────────────────────────────────────

    private void ingestSkillDiscoveries(Map<String, JsonNode> discoveriesByDevice, String normalizedUrl,
            Map<String, String> deviceNames) {
        logger.info("Starting Defender skill ingestion for {} device(s)", discoveriesByDevice.size());

        List<Map<String, Object>> batchData = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : discoveriesByDevice.entrySet()) {
            String deviceId = entry.getKey();
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            JsonNode discovery = entry.getValue();

            JsonNode skillsFound = discovery.path("skills_found");
            if (!skillsFound.isArray()) continue;

            for (JsonNode skill : skillsFound) {
                String path = skill.path("path").asText("");
                String agent = skill.path("agent").asText("unknown");
                long size = skill.path("size").asLong(0);
                long modified = skill.path("modified").asLong(0);
                String skillContent = skill.path("skill_content").asText("");

                if (path.isEmpty()) continue;

                String skillNameFromScript = skill.path("skill_name").asText("");
                String skillName = !skillNameFromScript.isEmpty() ? skillNameFromScript : extractSkillName(path);

                String syntheticHost = deviceName + "." + agent + ".defender.microsoft.com";

                Map<String, String> reqHeaders = new HashMap<>();
                reqHeaders.put("content-type", "text/markdown");
                reqHeaders.put("host", syntheticHost);
                reqHeaders.put("x-transport", "SKILL");
                reqHeaders.put("x-skill-name", skillName);
                reqHeaders.put("x-agent-type", agent);

                Map<String, String> tagMap = new HashMap<>();
                tagMap.put("source", "ENDPOINT");
                tagMap.put("connector", "MICROSOFT_DEFENDER");
                tagMap.put("mcp-server", "MCP Server");
                tagMap.put("mcp-client", agent);
                tagMap.put("skill", skillName);

                try {
                    Map<String, Object> reqPayload = new HashMap<>();
                    reqPayload.put("path", path);
                    reqPayload.put("skill_content", skillContent);

                    Map<String, Object> batch = new HashMap<>();
                    batch.put("path", "https://" + syntheticHost + "/skill/" + skillName);
                    batch.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqHeaders));
                    batch.put("responseHeaders", "{}");
                    batch.put("method", "POST");
                    batch.put("type", "HTTP/1.1");
                    batch.put("requestPayload", OBJECT_MAPPER.writeValueAsString(reqPayload));
                    batch.put("responsePayload", "{\"status\":\"ok\"}");
                    batch.put("ip", "");
                    batch.put("time", String.valueOf(System.currentTimeMillis()));
                    batch.put("statusCode", "200");
                    batch.put("status", "OK");
                    batch.put("akto_account_id", "1000000");
                    batch.put("akto_vxlan_id", "");
                    batch.put("is_pending", "false");
                    batch.put("source", "MIRRORING");
                    batch.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));
                    batch.put("publishToGuardrails", true);
                    batchData.add(batch);
                } catch (Exception e) {
                    logger.error("Failed to serialize skill discovery: {}", e.getMessage());
                }
            }
        }

        if (!batchData.isEmpty()) {
            try {
                sendBatch(batchData, normalizedUrl);
                logger.info("Ingested {} Defender skill discoveries", batchData.size());
            } catch (IOException e) {
                logger.error("Failed to ingest Defender skill discoveries: {}", e.getMessage());
            }
        }
    }

    // ── Collection creation ───────────────────────────────────────────────────

    private static class ServerCollectionInfo {
        String collectionName;
        String serverName;
        String clientType;
        long timestamp;
        String command;
        String url;
        String type;
    }

    private void createMCPServerCollections(Map<String, ServerCollectionInfo> serverCollections, String normalizedUrl) {
        String dbAbstractorEnv = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        String dbAbstractorUrl = (dbAbstractorEnv != null && !dbAbstractorEnv.isEmpty())
            ? (dbAbstractorEnv.endsWith("/") ? dbAbstractorEnv.substring(0, dbAbstractorEnv.length() - 1) : dbAbstractorEnv) + "/api/createCollectionForHostAndVpc"
            : normalizedUrl.replaceAll("/api/ingestData.*", "") + "/api/createCollectionForHostAndVpc";

        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        for (ServerCollectionInfo info : serverCollections.values()) {
            try {
                int colId = generateCollectionId(info.collectionName, info.timestamp);

                List<Map<String, Object>> tagsList = new ArrayList<>();
                addTag(tagsList, "mcp-server", "MCP Server", info.timestamp);
                addTag(tagsList, "source", "ENDPOINT", info.timestamp);
                addTag(tagsList, "connector", "MICROSOFT_DEFENDER", info.timestamp);
                if (info.clientType != null && !info.clientType.isEmpty()) {
                    addTag(tagsList, "mcp-client", info.clientType, info.timestamp);
                }

                Map<String, Object> request = new HashMap<>();
                request.put("colId", colId);
                request.put("host", info.collectionName);
                request.put("tagsList", tagsList);

                try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                    HttpPost post = new HttpPost(dbAbstractorUrl);
                    post.setHeader("Content-Type", "application/json");
                    String aktoToken = System.getenv("DATABASE_ABSTRACTOR_TOKEN");
                    if (aktoToken != null && !aktoToken.isEmpty()) {
                        post.setHeader("Authorization", aktoToken);
                    }
                    post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(request), ContentType.APPLICATION_JSON));

                    try (CloseableHttpResponse resp = client.execute(post)) {
                        int status = resp.getStatusLine().getStatusCode();
                        EntityUtils.consumeQuietly(resp.getEntity());
                        if (status == 200) {
                            logger.info("Created collection for MCP server: {}", info.collectionName);
                        } else {
                            logger.error("Failed to create collection for {}: HTTP {}", info.collectionName, status);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to create collection for {}: {}", info.collectionName, e.getMessage());
            }
        }
    }

    private static void addTag(List<Map<String, Object>> tagsList, String key, String value, long ts) {
        Map<String, Object> tag = new HashMap<>();
        tag.put("lastUpdatedTs", ts);
        tag.put("keyName", key);
        tag.put("value", value);
        tag.put("source", "KUBERNETES");
        tagsList.add(tag);
    }

    // ── Live Response helpers ─────────────────────────────────────────────────

    private List<Map<String, Object>> fetchDeviceList(String accessToken) throws IOException {
        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpGet get = new HttpGet(
                "https://api.securitycenter.microsoft.com/api/machines?$select=id,computerDnsName,osPlatform&$top=1000");
            get.setHeader("Authorization", "Bearer " + accessToken);
            get.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse resp = client.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status != 200) {
                    throw new IOException("Failed to fetch device list: HTTP " + status + " - " + body);
                }
                JsonNode json = OBJECT_MAPPER.readTree(body);
                JsonNode value = json.path("value");
                List<Map<String, Object>> devices = new ArrayList<>();
                if (value.isArray()) {
                    for (JsonNode node : value) {
                        devices.add(OBJECT_MAPPER.convertValue(node, Map.class));
                    }
                }
                return devices;
            }
        }
    }

    private String uploadScript(String accessToken, String scriptResourcePath) {
        String classpathResource = "/scripts/" + scriptResourcePath;
        try (InputStream scriptStream = MicrosoftDefenderExecutor.class.getResourceAsStream(classpathResource)) {
            if (scriptStream == null) {
                logger.error("Script not found in classpath: {}", classpathResource);
                return null;
            }
            byte[] scriptBytes = readAllBytes(scriptStream);

            RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/libraryfiles");
                post.setHeader("Authorization", "Bearer " + accessToken);

                org.apache.http.HttpEntity multipart = MultipartEntityBuilder.create()
                    .addTextBody("FileName", scriptResourcePath, ContentType.TEXT_PLAIN)
                    .addTextBody("OverrideIfExists", "true", ContentType.TEXT_PLAIN)
                    .addBinaryBody("file", scriptBytes, ContentType.APPLICATION_OCTET_STREAM, scriptResourcePath)
                    .build();
                post.setEntity(multipart);

                try (CloseableHttpResponse resp = client.execute(post)) {
                    int status = resp.getStatusLine().getStatusCode();
                    EntityUtils.consumeQuietly(resp.getEntity());
                    if (status != 200 && status != 201) {
                        logger.error("Failed to upload script {} to Defender library: HTTP {}", scriptResourcePath, status);
                        return null;
                    }
                    logger.info("Script {} uploaded to Defender library", scriptResourcePath);
                    return scriptResourcePath;
                }
            }
        } catch (IOException e) {
            logger.error("Error uploading script {}: {}", scriptResourcePath, e.getMessage());
            return null;
        }
    }

    private String submitLiveResponseAction(CloseableHttpClient httpClient, String accessToken,
            String deviceId, String scriptName) throws IOException {
        Map<String, Object> scriptNameParam = new HashMap<>();
        scriptNameParam.put("key", "ScriptName");
        scriptNameParam.put("value", scriptName);

        Map<String, Object> runScriptCommand = new HashMap<>();
        runScriptCommand.put("type", "RunScript");
        runScriptCommand.put("params", new Object[]{scriptNameParam});

        Map<String, Object> body = new HashMap<>();
        body.put("Commands", new Object[]{runScriptCommand});
        body.put("Comment", "Akto MCP/Skill discovery");

        HttpPost post = new HttpPost(
            "https://api.securitycenter.microsoft.com/api/machines/" + deviceId + "/runliveresponse");
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse resp = httpClient.execute(post)) {
            int status = resp.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(resp.getEntity());
            if (status == 201) {
                return OBJECT_MAPPER.readTree(responseBody).path("id").asText(null);
            }
            if (status == 400) {
                String errorCode = OBJECT_MAPPER.readTree(responseBody).path("error").path("code").asText("");
                if ("ActiveRequestAlreadyExists".equals(errorCode)) {
                    return null;
                }
            }
            throw new IOException("runliveresponse returned HTTP " + status + ": " + responseBody);
        }
    }

    private String getActiveActionId(CloseableHttpClient httpClient, String accessToken, String deviceId)
            throws IOException {
        try {
            java.net.URI uri = new URIBuilder("https://api.securitycenter.microsoft.com/api/machineactions")
                .addParameter("$filter", "machineId eq '" + deviceId + "' and (status eq 'Pending' or status eq 'InProgress')")
                .addParameter("$orderby", "creationDateTimeUtc desc")
                .addParameter("$top", "1")
                .build();
            HttpGet get = new HttpGet(uri);
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                if (resp.getStatusLine().getStatusCode() != 200) return null;
                JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                JsonNode value = json.path("value");
                if (value.isArray() && value.size() > 0) {
                    return value.get(0).path("id").asText(null);
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Failed to build URI for getActiveActionId", e);
        }
        return null;
    }

    private void cancelLiveResponseAction(CloseableHttpClient httpClient, String accessToken, String actionId)
            throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("Comment", "Cancelled by Akto before retry");
        HttpPost post = new HttpPost(
            "https://api.securitycenter.microsoft.com/api/machineactions/" + actionId + "/cancel");
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse resp = httpClient.execute(post)) {
            logger.info("Cancel action {} returned HTTP {}", actionId, resp.getStatusLine().getStatusCode());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pollActionUntilDone(CloseableHttpClient httpClient, String accessToken, String actionId)
            throws IOException {
        long deadline = System.currentTimeMillis() + LIVE_RESPONSE_MAX_WAIT_MS;

        while (System.currentTimeMillis() < deadline) {
            HttpGet get = new HttpGet(
                "https://api.securitycenter.microsoft.com/api/machineactions/" + actionId);
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status != 200) {
                    throw new IOException("Polling action " + actionId + " returned HTTP " + status);
                }
                JsonNode json = OBJECT_MAPPER.readTree(body);
                String actionStatus = json.path("status").asText("unknown");
                if ("Succeeded".equals(actionStatus) || "Failed".equals(actionStatus)
                        || "Cancelled".equals(actionStatus) || "TimeOut".equals(actionStatus)) {
                    return OBJECT_MAPPER.convertValue(json, Map.class);
                }
            }

            try { Thread.sleep(LIVE_RESPONSE_POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
        }

        Map<String, Object> timeout = new HashMap<>();
        timeout.put("status", "TimedOut");
        timeout.put("actionId", actionId);
        return timeout;
    }

    private String getActionOutputDownloadLink(CloseableHttpClient httpClient, String accessToken, String actionId)
            throws IOException {
        HttpGet get = new HttpGet(
            "https://api.securitycenter.microsoft.com/api/machineactions/" + actionId +
            "/GetLiveResponseResultDownloadLink(index=0)");
        get.setHeader("Authorization", "Bearer " + accessToken);

        try (CloseableHttpResponse resp = httpClient.execute(get)) {
            int status = resp.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(resp.getEntity());
            if (status != 200) {
                logger.error("GetLiveResponseResultDownloadLink returned HTTP {} for actionId={}", status, actionId);
                return null;
            }
            return OBJECT_MAPPER.readTree(body).path("value").asText(null);
        }
    }

    private JsonNode downloadAndParseOutput(CloseableHttpClient httpClient, String downloadUrl) {
        try {
            HttpGet get = new HttpGet(downloadUrl);
            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                byte[] data = EntityUtils.toByteArray(resp.getEntity());

                // Try extracting stdout from ZIP
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                     ZipInputStream zis = new ZipInputStream(bais)) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        String baseName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                        if (baseName.equals("stdout") || baseName.startsWith("stdout_")) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = zis.read(buf)) > 0) baos.write(buf, 0, len);
                            String stdout = sanitizeJson(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                            return OBJECT_MAPPER.readTree(stdout);
                        }
                    }
                } catch (Exception zipEx) {
                    // Not a ZIP — try parsing raw content as JSON
                    String content = sanitizeJson(new String(data, StandardCharsets.UTF_8));
                    return OBJECT_MAPPER.readTree(content);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to download/parse script output: {}", e.getMessage());
        }
        return null;
    }

    // ── KQL helpers ───────────────────────────────────────────────────────────

    private String buildKqlQuery(String lastQueriedAt) {
        String toolFilter = buildProcessToolFilter();
        String matchClause =
            "| where ProcessCommandLine has_any (" + toolFilter + ")" +
            "    or FileName has_any (" + toolFilter + ")" +
            "    or FolderPath has_any (" + toolFilter + ")" +
            "    or InitiatingProcessCommandLine has_any (" + toolFilter + ")" +
            "    or InitiatingProcessFileName has_any (" + toolFilter + ")";
        String projectClause =
            "| project Timestamp, DeviceId, DeviceName, AccountName, AccountDomain, FileName, FolderPath, ProcessCommandLine, InitiatingProcessFileName, InitiatingProcessCommandLine" +
            "| order by Timestamp desc";

        if (lastQueriedAt != null && !lastQueriedAt.isEmpty()) {
            return "DeviceProcessEvents" +
                "| where Timestamp > datetime(" + lastQueriedAt + ")" +
                matchClause +
                projectClause;
        }
        return "DeviceProcessEvents" + matchClause + projectClause;
    }

    private static String buildProcessToolFilter() {
        return PROCESS_TOOL_LIST.stream()
            .map(t -> "\"" + t + "\"")
            .collect(Collectors.joining(", "));
    }

    private String detectTool(String processCommandLine) {
        if (processCommandLine == null) return PROCESS_TOOL_LIST.get(0);
        String lower = processCommandLine.toLowerCase();
        for (String tool : PROCESS_TOOL_LIST) {
            if (lower.contains(tool.toLowerCase())) return tool;
        }
        return PROCESS_TOOL_LIST.get(0);
    }

    private String detectToolFirst(String... fieldsInPriorityOrder) {
        for (String field : fieldsInPriorityOrder) {
            if (field == null || field.isEmpty()) continue;
            String lower = field.toLowerCase();
            for (String tool : PROCESS_TOOL_LIST) {
                if (lower.contains(tool.toLowerCase())) return tool;
            }
        }
        return PROCESS_TOOL_LIST.get(0);
    }

    // ── Transport ─────────────────────────────────────────────────────────────

    private void postBatch(String normalizedUrl, List<Map<String, Object>> records, int batchNum, AccountJob job)
            throws IOException, RetryableJobException {
        updateJobHeartbeat(job);

        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", records);
        String jsonPayload = OBJECT_MAPPER.writeValueAsString(payload);

        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(normalizedUrl + "/api/ingestData");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = httpClient.execute(post)) {
                int statusCode = resp.getStatusLine().getStatusCode();
                EntityUtils.consumeQuietly(resp.getEntity());
                if (statusCode == 429 || statusCode >= 500) {
                    throw new RetryableJobException("Ingestion service returned " + statusCode + " on batch " + batchNum);
                }
                if (statusCode != 200) {
                    throw new IOException("Ingestion service returned HTTP " + statusCode + " on batch " + batchNum);
                }
                logger.info("Batch {} ingested successfully ({} records)", batchNum, records.size());
            }
        }
    }

    private void sendBatch(List<Map<String, Object>> batch, String normalizedUrl) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", batch);

        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(normalizedUrl + "/api/ingestData");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(payload), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                EntityUtils.consumeQuietly(resp.getEntity());
                if (status != 200) {
                    throw new IOException("Ingestion service returned HTTP " + status);
                }
            }
        }
    }

    // ── Azure AD token ────────────────────────────────────────────────────────

    private String fetchAccessToken(String tenantId, String clientId, String clientSecret)
            throws IOException, RetryableJobException {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            String body = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&scope=https%3A%2F%2Fapi.securitycenter.microsoft.com%2F.default";

            HttpPost post = new HttpPost(tokenUrl);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

            try (CloseableHttpResponse resp = httpClient.execute(post)) {
                int statusCode = resp.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(resp.getEntity());

                if (statusCode >= 500) {
                    throw new RetryableJobException("Azure AD token endpoint returned " + statusCode);
                }
                if (statusCode != 200) {
                    throw new IOException("Failed to fetch Azure AD token: HTTP " + statusCode + " - " + responseBody);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode tokenNode = json.get("access_token");
                if (tokenNode == null || tokenNode.isNull()) {
                    throw new IOException("No access_token in Azure AD response: " + responseBody);
                }
                return tokenNode.asText();
            }
        }
    }

    // ── Advanced Hunting query ────────────────────────────────────────────────

    private List<Map<String, Object>> runHuntingQuery(String accessToken, String kqlQuery)
            throws IOException, RetryableJobException {
        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("Query", kqlQuery);

            HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/advancedqueries/run");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = httpClient.execute(post)) {
                int statusCode = resp.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(resp.getEntity());

                if (statusCode == 429 || statusCode >= 500) {
                    throw new RetryableJobException("Defender Advanced Hunting returned " + statusCode);
                }
                if (statusCode != 200) {
                    throw new IOException("Defender Advanced Hunting returned HTTP " + statusCode + " - " + responseBody);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode resultsNode = json.get("Results");
                if (resultsNode == null || !resultsNode.isArray()) return new ArrayList<>();

                List<Map<String, Object>> rows = new ArrayList<>();
                for (JsonNode rowNode : resultsNode) {
                    rows.add(OBJECT_MAPPER.convertValue(rowNode, Map.class));
                }
                return rows;
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String toolSlug(String softwareName) {
        if (softwareName == null) return "unknown";
        switch (softwareName.toLowerCase()) {
            case "claude": return "claude-cli";
            case "cursor": return "cursor";
            case "copilot": return "copilot";
            case "windsurf": return "windsurf";
            case "antigravity": return "antigravity";
            case "codex": return "codex";
            case "ollama": return "ollama";
            default: return softwareName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        }
    }

    private static String clientTypeSlug(String clientType) {
        if (clientType == null) return "unknown";
        switch (clientType.toLowerCase()) {
            case "claude-cli-user":
            case "claude-cli-project":
            case "claude-cli-local":
            case "claude-cli-enterprise":
            case "claude-plugin":
                return "claude-cli";
            case "claude-desktop": return "claude-desktop";
            case "cursor": return "cursor";
            case "windsurf": return "windsurf";
            case "vscode": return "vscode";
            case "github-cli": return "github-copilot";
            case "antigravity": return "antigravity";
            case "container": return "container";
            default: return clientType.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        }
    }

    private static int generateCollectionId(String collectionName, long timestamp) {
        return Math.abs((collectionName + timestamp).hashCode());
    }

    private static String extractSkillName(String filePath) {
        String fileName = filePath.substring(Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) fileName = fileName.substring(0, dot);
        return fileName.toLowerCase().replaceAll("[\\s_]+", "-").replaceAll("[^a-z0-9-]", "");
    }

    private static String sanitizeJson(String content) {
        return content.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) baos.write(buf, 0, len);
        return baos.toByteArray();
    }

    private void waitForFuture(Future<?> future, String label) {
        if (future == null) return;
        try {
            future.get();
        } catch (Exception e) {
            logger.error("{}: thread interrupted or failed — {}", label, e.getMessage());
        }
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private String getRequiredString(Map<String, Object> config, String key) {
        Object val = config.get(key);
        if (val == null || val.toString().isEmpty()) {
            throw new IllegalArgumentException("Missing required config key: " + key);
        }
        return val.toString();
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }
}
