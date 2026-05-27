package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.type.URLMethods.Method;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
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
    private static final int LIVE_RESPONSE_MAX_WAIT_MS = 5 * 60 * 1000; // 5 minutes per action (was 30 min)
    private static final int CANCEL_RETRY_WAIT_MS = 10_000;
    private static final int LIVE_RESPONSE_RATE_LIMIT_PER_MINUTE = 10;
    private static final long MIN_REQUEST_INTERVAL_MS = 60_000L / LIVE_RESPONSE_RATE_LIMIT_PER_MINUTE; // 6000ms
    private static final int RATE_LIMIT_MAX_RETRIES = 3;
    private static long lastScriptUploadMs = 0;
    private static final Set<String> uploadedScripts = Collections.synchronizedSet(new HashSet<>());

    // Shared rate limiter for Live Response submissions across all phases (Defender allows 10/min/tenant)
    private static final Object SUBMIT_LOCK = new Object();
    private static long sharedLastSubmitMs = 0;
    // Per-device locks: Defender allows only ONE active Live Response action per device at a time.
    // When MCP and Skills phases run concurrently, they must serialize per-device.
    private final ConcurrentHashMap<String, Object> deviceLocks = new ConcurrentHashMap<>();
    // Track created MCP server collections within a job to avoid duplicate API calls
    private final Set<String> createdCollectionIds = ConcurrentHashMap.newKeySet();

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
        // Host format matches mcp-endpoint-shield's GenerateIngestDataV2 (deviceLabel.ai-agent.agentName)
        // so Defender-discovered AI agents land in the same collection as gateway-discovered ones.
        String host = deviceName + ".ai-agent." + slug;

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
        // Host: deviceLabel.ai-agent.toolName — matches mcp-endpoint-shield convention
        String host = deviceName + ".ai-agent." + tool;

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
        // Reset per-job state so deduplication doesn't carry over from prior runs of the same singleton
        createdCollectionIds.clear();
        deviceLocks.clear();

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

        final String mcpShScript   = uploadScriptWithRateLimit(accessToken, "scan_mcp_configs.sh");
        final String mcpPs1Script  = uploadScriptWithRateLimit(accessToken, "scan_mcp_configs.ps1");
        final String skillsShScript  = uploadScriptWithRateLimit(accessToken, "scan_skills.sh");
        final String skillsPs1Script = uploadScriptWithRateLimit(accessToken, "scan_skills.ps1");

        // Background heartbeat thread: keeps job alive without blocking polling/work threads
        final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "defender-heartbeat-" + job.getId());
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                updateJobHeartbeat(job);
            } catch (Exception e) {
                logger.error("Background heartbeat failed: {}", e.getMessage());
            }
        }, 0, 4, TimeUnit.SECONDS);

        // Per-device ingestion callbacks: ingest the moment a device's output is collected
        BiConsumer<String, JsonNode> mcpIngestCallback = (deviceId, output) -> {
            try {
                ingestMCPDeviceDiscovery(deviceId, deviceNames.getOrDefault(deviceId, deviceId), output, normalizedUrl);
            } catch (Exception e) {
                logger.error("Per-device MCP ingestion failed for {}: {}", deviceId, e.getMessage());
            }
        };
        BiConsumer<String, JsonNode> skillsIngestCallback = (deviceId, output) -> {
            try {
                ingestSkillDeviceDiscovery(deviceId, deviceNames.getOrDefault(deviceId, deviceId), output, normalizedUrl);
            } catch (Exception e) {
                logger.error("Per-device skill ingestion failed for {}: {}", deviceId, e.getMessage());
            }
        };

        // Run all 4 sub-phases concurrently — each phase processes its devices in parallel
        ExecutorService phasePool = Executors.newFixedThreadPool(4);
        List<Future<?>> phaseFutures = new ArrayList<>();

        if (mcpShScript != null && !unixDeviceIds.isEmpty()) {
            phaseFutures.add(phasePool.submit(() ->
                runScriptOnDevices(accessToken, mcpShScript, unixDeviceIds, deviceNames, "MCP Config (Unix)", job, mcpIngestCallback)));
        }
        if (mcpPs1Script != null && !windowsDeviceIds.isEmpty()) {
            phaseFutures.add(phasePool.submit(() ->
                runScriptOnDevices(accessToken, mcpPs1Script, windowsDeviceIds, deviceNames, "MCP Config (Windows)", job, mcpIngestCallback)));
        }
        if (skillsShScript != null && !unixDeviceIds.isEmpty()) {
            phaseFutures.add(phasePool.submit(() ->
                runScriptOnDevices(accessToken, skillsShScript, unixDeviceIds, deviceNames, "Skills (Unix)", job, skillsIngestCallback)));
        }
        if (skillsPs1Script != null && !windowsDeviceIds.isEmpty()) {
            phaseFutures.add(phasePool.submit(() ->
                runScriptOnDevices(accessToken, skillsPs1Script, windowsDeviceIds, deviceNames, "Skills (Windows)", job, skillsIngestCallback)));
        }

        for (Future<?> f : phaseFutures) {
            waitForFuture(f, "Phase 3 sub-phase");
        }
        phasePool.shutdown();
        heartbeatExecutor.shutdownNow();

        logger.info("Phase 3 complete (per-device ingestion already done): jobId={}", job.getId());
    }

    private void runScriptOnDevices(String accessToken, String scriptName,
            List<String> deviceIds, Map<String, String> deviceNames, String description, AccountJob job,
            BiConsumer<String, JsonNode> ingestCallback) {
        if (deviceIds.isEmpty()) return;

        // Process devices in parallel within this phase. Submission is gated by shared rate limit;
        // polling/downloading/ingesting runs concurrently per device.
        int poolSize = Math.min(deviceIds.size(), 8);
        ExecutorService devicePool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "defender-" + description.replaceAll("[^a-zA-Z0-9]", "-"));
            t.setDaemon(true);
            return t;
        });
        List<Future<?>> futures = new ArrayList<>();

        for (String deviceId : deviceIds) {
            futures.add(devicePool.submit(() ->
                processSingleDevice(accessToken, scriptName, deviceId, deviceNames, description, ingestCallback)));
        }

        devicePool.shutdown();
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { logger.warn("{}: Device future error: {}", description, e.getMessage()); }
        }
    }

    private void processSingleDevice(String accessToken, String scriptName, String deviceId,
            Map<String, String> deviceNames, String description, BiConsumer<String, JsonNode> ingestCallback) {
        String deviceName = deviceNames.getOrDefault(deviceId, deviceId);

        // Acquire per-device lock so only one phase runs on this device at a time
        // (Defender allows only ONE active Live Response action per device).
        Object deviceLock = deviceLocks.computeIfAbsent(deviceId, k -> new Object());
        synchronized (deviceLock) {

        for (int attempt = 0; attempt < RATE_LIMIT_MAX_RETRIES; attempt++) {
            try {
                // Shared throttle: at least 6s between any submission across all phases
                synchronized (SUBMIT_LOCK) {
                    long elapsed = System.currentTimeMillis() - sharedLastSubmitMs;
                    if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                        try { Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed); } catch (InterruptedException ignored) {}
                    }
                    sharedLastSubmitMs = System.currentTimeMillis();
                }

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
                        return;
                    }

                    Map<String, Object> finalStatus = pollActionUntilDone(httpClient, accessToken, actionId, null);
                    String status = finalStatus.getOrDefault("status", "unknown").toString();
                    if (!"Succeeded".equals(status)) {
                        logger.error("{}: Action on device {} finished with status {}", description, deviceName, status);
                        return;
                    }

                    String downloadUrl = getActionOutputDownloadLink(httpClient, accessToken, actionId);
                    if (downloadUrl == null) {
                        logger.error("{}: No download link for actionId={} on device {}", description, actionId, deviceName);
                        return;
                    }

                    JsonNode output = downloadAndParseOutput(httpClient, downloadUrl);
                    if (output != null) {
                        logger.info("{}: Collected output from device {}", description, deviceName);
                        // Per-device ingestion: results visible immediately, no waiting for stuck peers
                        if (ingestCallback != null) {
                            ingestCallback.accept(deviceId, output);
                        }
                    }
                    return; // success
                }
            } catch (PermanentSkipException pse) {
                logger.warn("{}: Skipping device {} — {} (permanent, no retry)",
                    description, deviceName, pse.getMessage());
                return;
            } catch (RateLimitException rle) {
                if (attempt < RATE_LIMIT_MAX_RETRIES - 1) {
                    logger.warn("{}: Rate limited on device {}, sleeping {}s before retry (attempt {}/{})",
                        description, deviceName, rle.getRetryAfterSeconds(), attempt + 1, RATE_LIMIT_MAX_RETRIES);
                    try { Thread.sleep(rle.getRetryAfterSeconds() * 1_000L); } catch (InterruptedException ignored) {}
                } else {
                    logger.error("{}: Rate limited on device {} after {} attempts, giving up",
                        description, deviceName, RATE_LIMIT_MAX_RETRIES);
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: Error processing device {}: {}", description, deviceName, e.getMessage());
                return;
            }
        }
        } // end synchronized (deviceLock)
    }

    // ── MCP ingestion ─────────────────────────────────────────────────────────

    // Per-device ingestion: ingest one device's MCP discovery immediately upon collection
    private void ingestMCPDeviceDiscovery(String deviceId, String deviceName, JsonNode discovery, String normalizedUrl) {
        JsonNode configsFound = discovery.path("configs_found");
        int configCount = configsFound.isArray() ? configsFound.size() : 0;
        logger.info("MCP per-device ingestion: device={}, configs_found_count={}, raw_content_preview={}",
            deviceName, configCount,
            discovery.toString().length() > 500 ? discovery.toString().substring(0, 500) + "..." : discovery.toString());
        Map<String, JsonNode> singleDevice = new HashMap<>();
        singleDevice.put(deviceId, discovery);
        Map<String, String> singleName = new HashMap<>();
        singleName.put(deviceId, deviceName);
        ingestMCPDiscoveries(singleDevice, normalizedUrl, singleName);
    }

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

                if (!servers.isArray() || servers.size() == 0) continue;

                for (JsonNode server : servers) {
                    String serverName = server.path("name").asText("");
                    String serverType = server.path("type").asText("unknown");
                    String command = server.path("command").asText("");
                    String url = server.path("url").asText("");

                    if (serverName.isEmpty()) continue;

                    // MCP server host matches mcp-endpoint-shield's generateMcpServerCollectionName:
                    //   HTTP server: <deviceLabel>.<clientType>.<urlHost>
                    //   STDIO server: <deviceLabel>.<clientType>.<serverName>
                    // Each MCP server gets its OWN collection (not grouped by AI agent client).
                    String mcpServerHost;
                    if (!url.isEmpty()) {
                        String urlHost = serverName;
                        try {
                            java.net.URI parsedUri = new java.net.URI(url);
                            if (parsedUri.getHost() != null && !parsedUri.getHost().isEmpty()) {
                                urlHost = parsedUri.getHost();
                            }
                        } catch (Exception ignored) { }
                        mcpServerHost = (deviceName + "." + clientSlug + "." + urlHost).toLowerCase();
                    } else {
                        mcpServerHost = (deviceName + "." + clientSlug + "." + serverName).toLowerCase();
                    }

                    String collectionName = mcpServerHost;

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
                    reqHeaders.put("host", mcpServerHost);
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
                    tagMap.put("mcp-client", clientSlug);

                    try {
                        Map<String, Object> batch = new HashMap<>();
                        // Path mirrors shield's STDIO format: just /mcp (server identity is in the host)
                        batch.put("path", "https://" + mcpServerHost + "/mcp");
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

    // Per-device ingestion: ingest one device's skill discovery immediately upon collection
    private void ingestSkillDeviceDiscovery(String deviceId, String deviceName, JsonNode discovery, String normalizedUrl) {
        JsonNode skillsFound = discovery.path("skills_found");
        int skillCount = skillsFound.isArray() ? skillsFound.size() : 0;
        logger.info("Skill per-device ingestion: device={}, skills_found_count={}, raw_content_preview={}",
            deviceName, skillCount,
            discovery.toString().length() > 500 ? discovery.toString().substring(0, 500) + "..." : discovery.toString());
        Map<String, JsonNode> singleDevice = new HashMap<>();
        singleDevice.put(deviceId, discovery);
        Map<String, String> singleName = new HashMap<>();
        singleName.put(deviceId, deviceName);
        ingestSkillDiscoveries(singleDevice, normalizedUrl, singleName);
    }

    private void ingestSkillDiscoveries(Map<String, JsonNode> discoveriesByDevice, String normalizedUrl,
            Map<String, String> deviceNames) {
        logger.info("Starting Defender skill ingestion for {} device(s)", discoveriesByDevice.size());

        List<Map<String, Object>> batchData = new ArrayList<>();
        // Accumulate skill names per agent collection so we can register them on the collection
        // afterwards. Without this, skills never appear on the Agentic Assets > Skills tab (which
        // reads ApiCollection.skills[], not endpoint paths).
        Map<String, Set<String>> skillsByCollection = new HashMap<>();
        Map<String, String> agentByCollection = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : discoveriesByDevice.entrySet()) {
            String deviceId = entry.getKey();
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            JsonNode discovery = entry.getValue();

            JsonNode skillsFound = discovery.path("skills_found");
            if (!skillsFound.isArray()) continue;

            for (JsonNode skill : skillsFound) {
                String path = skill.path("path").asText("");
                String agent = skill.path("agent").asText("unknown");
                String rawContent = skill.path("skill_content").asText("");

                if (path.isEmpty()) continue;

                // Parse YAML frontmatter so guardrail policies can match on description.
                SkillMetadata meta = extractSkillMetadata(rawContent);

                // Prefer script-provided name → frontmatter name → parent-dir → filename.
                String skillNameFromScript = skill.path("skill_name").asText("");
                String skillName = !skillNameFromScript.isEmpty()
                    ? normalizeSlug(skillNameFromScript)
                    : deriveSkillName(meta, path);

                // Host: deviceLabel.ai-agent.agentName — matches mcp-endpoint-shield's skill ingestion
                // so a skill on the same device+agent lands in the same collection as the gateway's.
                String syntheticHost = deviceName + ".ai-agent." + agent;

                Map<String, String> reqHeaders = new HashMap<>();
                reqHeaders.put("content-type", "application/json");
                reqHeaders.put("host", syntheticHost);
                reqHeaders.put("x-transport", "STDIO");
                reqHeaders.put("x-skill-name", skillName);
                reqHeaders.put("x-agent-type", agent);

                // Tags mirror mcp-endpoint-shield's skill tagging:
                //   source=ENDPOINT, skill=<name>, mcp-client=<agent>, ai-agent=<agent> (when known)
                // Defender-specific tag retained so the dashboard can filter by connector.
                Map<String, String> tagMap = new HashMap<>();
                tagMap.put("source", "ENDPOINT");
                tagMap.put("connector", "MICROSOFT_DEFENDER");
                tagMap.put("skill", skillName);
                if (!"unknown".equals(agent)) {
                    tagMap.put("mcp-client", agent);
                    tagMap.put("ai-agent", agent);
                }

                try {
                    // Payload mirrors mcp-endpoint-shield/mcp/skill_detector.go:processSkillFileWithMetadata
                    Map<String, Object> reqPayload = new HashMap<>();
                    reqPayload.put("skill_name", skillName);
                    reqPayload.put("skill_description", meta.description);
                    reqPayload.put("skill_content", meta.content.isEmpty() ? rawContent : meta.content);
                    reqPayload.put("agent", agent);
                    reqPayload.put("file_path", path);

                    Map<String, Object> batch = new HashMap<>();
                    // Path uses /skills/<name> (plural) to match the shield endpoint format.
                    batch.put("path", "/skills/" + skillName);
                    batch.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqHeaders));
                    batch.put("responseHeaders", "{}");
                    batch.put("method", Method.POST.name());
                    batch.put("type", "HTTP/1.1");
                    batch.put("requestPayload", OBJECT_MAPPER.writeValueAsString(reqPayload));
                    batch.put("responsePayload", "{}");
                    batch.put("ip", "127.0.0.1");
                    batch.put("time", String.valueOf(System.currentTimeMillis() / 1000));
                    batch.put("statusCode", "200");
                    batch.put("status", "OK");
                    batch.put("akto_account_id", "1000000");
                    batch.put("akto_vxlan_id", "0");
                    batch.put("is_pending", "false");
                    batch.put("source", "MIRRORING");
                    batch.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));
                    batch.put("publishToGuardrails", true);
                    batchData.add(batch);

                    // Track which skills belong to which agent-collection so we can register
                    // them after ingestion (this is what makes them appear on the Agentic
                    // Assets > Skills tab — that tab reads ApiCollection.skills[]).
                    skillsByCollection.computeIfAbsent(syntheticHost, k -> new HashSet<>()).add(skillName);
                    agentByCollection.put(syntheticHost, agent);

                    // Fire cyborg's LLM-based skill validation. This calls /api/validateAndReportSkill
                    // which prompts an LLM with the skill content, populates ComponentRiskAnalysis
                    // (Evidence/IsComponentMalicious) on mcp_audit_info, and reports a threat event
                    // when malicious. Run in background so it doesn't slow per-device ingestion.
                    final String fSkillName = skillName;
                    final String fSkillDescription = meta.description;
                    final String fSkillContent = meta.content.isEmpty() ? rawContent : meta.content;
                    final String fAgent = agent;
                    final String fPath = path;
                    final String fCollectionName = syntheticHost;
                    new Thread(() -> {
                        try {
                            validateAndReportSkill(fSkillName, fSkillDescription, fSkillContent, fAgent, fPath, fCollectionName);
                        } catch (Exception ex) {
                            logger.error("Failed to validate skill {}: {}", fSkillName, ex.getMessage());
                        }
                    }, "skill-validator-" + skillName).start();
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

        // Register skill names on agent collections so they appear on the Agentic Assets > Skills tab.
        // The endpoint merges tags + skills server-side, so calling it multiple times across
        // per-device ingestions is safe.
        for (Map.Entry<String, Set<String>> e : skillsByCollection.entrySet()) {
            String collectionHost = e.getKey();
            Set<String> skills = e.getValue();
            String agent = agentByCollection.get(collectionHost);
            createAgentSkillCollection(collectionHost, agent, skills, normalizedUrl);
        }
    }

    /**
     * Calls cyborg's /api/validateAndReportSkill which runs an LLM-based skill malicious-pattern
     * check (pipe-to-interpreter, credential exfiltration, prompt injection), updates the
     * mcp_audit_info collection with ComponentRiskAnalysis, and reports a threat event when
     * flagged. The endpoint is implemented in akto-cyborg:
     *   apps/database-abstractor/src/main/java/com/akto/action/SkillValidationAction.java
     */
    private void validateAndReportSkill(String skillName, String skillDescription, String skillContent,
            String agentName, String filePath, String collectionName) {
        String dbAbstractorEnv = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (dbAbstractorEnv == null || dbAbstractorEnv.isEmpty()) {
            logger.error("DATABASE_ABSTRACTOR_SERVICE_URL not set — skipping skill validation for {}", skillName);
            return;
        }
        String url = (dbAbstractorEnv.endsWith("/") ? dbAbstractorEnv.substring(0, dbAbstractorEnv.length() - 1) : dbAbstractorEnv)
                + "/api/validateAndReportSkill";

        Map<String, Object> req = new HashMap<>();
        req.put("skillName", skillName);
        req.put("skillDescription", skillDescription == null ? "" : skillDescription);
        req.put("skillContent", skillContent == null ? "" : skillContent);
        req.put("agentName", agentName == null ? "" : agentName);
        req.put("filePath", filePath == null ? "" : filePath);
        req.put("collectionName", collectionName == null ? "" : collectionName);
        // Use ENDPOINT (matches mcp-endpoint-shield's ContextSourceEndpoint) so the verdict
        // surfaces under Atlas. "AGENTIC" would route it to Argus instead.
        req.put("contextSource", "ENDPOINT");
        req.put("source", "MICROSOFT_DEFENDER");
        req.put("reportThreat", true);

        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            if (token != null && !token.isEmpty()) {
                // Cyborg auth filter expects raw JWT in Authorization header (no "Bearer " prefix)
                post.setHeader("Authorization", token);
            }
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(req), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status == 200) {
                    JsonNode result = OBJECT_MAPPER.readTree(body);
                    boolean isMalicious = result.path("isMalicious").asBoolean(false);
                    double maliciousScore = result.path("maliciousMatchScore").asDouble(0.0);
                    String reason = result.path("reason").asText("");
                    if (isMalicious) {
                        logger.warn("Skill {} flagged as malicious (score={}, reason={})", skillName, maliciousScore, reason);
                    } else {
                        logger.info("Skill {} validated as safe (score={})", skillName, maliciousScore);
                    }
                } else {
                    logger.error("Skill validation API returned HTTP {} for {}: {}", status, skillName, body);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to call validateAndReportSkill for {}: {}", skillName, e.getMessage());
        }
    }

    /**
     * Registers an agent-level collection with skill names so the Agentic Assets > Skills tab
     * (which reads ApiCollection.skills[]) lights up. Mirrors mcp-endpoint-shield's
     * CreateCollectionForAgentSkills — hits the same /api/createCollectionForHostAndVpc endpoint
     * with a "skills" array in the request body.
     */
    private void createAgentSkillCollection(String collectionHost, String agent, Set<String> skillNames, String normalizedUrl) {
        if (skillNames == null || skillNames.isEmpty()) return;

        String dbAbstractorEnv = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        String dbAbstractorUrl = (dbAbstractorEnv != null && !dbAbstractorEnv.isEmpty())
            ? (dbAbstractorEnv.endsWith("/") ? dbAbstractorEnv.substring(0, dbAbstractorEnv.length() - 1) : dbAbstractorEnv) + "/api/createCollectionForHostAndVpc"
            : normalizedUrl.replaceAll("/api/ingestData.*", "") + "/api/createCollectionForHostAndVpc";

        long ts = System.currentTimeMillis() / 1000;
        try {
            int colId = generateCollectionId(collectionHost, ts);

            List<Map<String, Object>> tagsList = new ArrayList<>();
            addTag(tagsList, "source", "ENDPOINT", ts);
            // `gen-ai: Gen AI` tells the dashboard's groupCollectionsByService to treat this as an
            // AI-agent / skill collection (NOT MCP Servers). Required to match shield's baseTagsAgent.
            addTag(tagsList, "gen-ai", "Gen AI", ts);
            addTag(tagsList, "connector", "MICROSOFT_DEFENDER", ts);
            if (agent != null && !"unknown".equals(agent)) {
                addTag(tagsList, "mcp-client", agent, ts);
                addTag(tagsList, "ai-agent", agent, ts);
            }

            Map<String, Object> request = new HashMap<>();
            request.put("colId", colId);
            request.put("host", collectionHost);
            request.put("tagsList", tagsList);
            request.put("skills", new ArrayList<>(skillNames));

            RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                HttpPost post = new HttpPost(dbAbstractorUrl);
                post.setHeader("Content-Type", "application/json");
                String aktoToken = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
                if (aktoToken != null && !aktoToken.isEmpty()) {
                    post.setHeader("Authorization", aktoToken);
                }
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(request), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse resp = client.execute(post)) {
                    int status = resp.getStatusLine().getStatusCode();
                    EntityUtils.consumeQuietly(resp.getEntity());
                    if (status == 200) {
                        logger.info("Registered {} skill(s) on collection {}: {}", skillNames.size(), collectionHost, skillNames);
                    } else {
                        logger.error("Failed to register skills on collection {}: HTTP {}", collectionHost, status);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to register skills on collection {}: {}", collectionHost, ex.getMessage());
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
            // Skip if this collection was already created in this job run (per-device ingestion can hit duplicates)
            if (!createdCollectionIds.add(info.collectionName)) {
                continue;
            }
            try {
                int colId = generateCollectionId(info.collectionName, info.timestamp);

                List<Map<String, Object>> tagsList = new ArrayList<>();
                addTag(tagsList, "mcp-server", "MCP Server", info.timestamp);
                addTag(tagsList, "source", "ENDPOINT", info.timestamp);
                addTag(tagsList, "connector", "MICROSOFT_DEFENDER", info.timestamp);
                if (info.clientType != null && !info.clientType.isEmpty()) {
                    addTag(tagsList, "mcp-client", info.clientType, info.timestamp);
                }
                // Match shield's CreateCollectionForMCPServer: STDIO servers (no URL) carry the
                // `local-mcp-server: <serverName>` tag so the dashboard knows the binary identity.
                if ((info.url == null || info.url.isEmpty()) && info.serverName != null && !info.serverName.isEmpty()) {
                    addTag(tagsList, "local-mcp-server", info.serverName, info.timestamp);
                }

                Map<String, Object> request = new HashMap<>();
                request.put("colId", colId);
                request.put("host", info.collectionName);
                request.put("tagsList", tagsList);

                try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                    HttpPost post = new HttpPost(dbAbstractorUrl);
                    post.setHeader("Content-Type", "application/json");
                    String aktoToken = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
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

        List<Map<String, Object>> devices = new ArrayList<>();
        String nextUrl = null;
        try {
            nextUrl = new URIBuilder("https://api.securitycenter.microsoft.com/api/machines")
                .addParameter("$select", "id,computerDnsName,osPlatform,onboardingStatus,healthStatus")
                .addParameter("$filter", "onboardingStatus eq 'Onboarded' and healthStatus eq 'Active'")
                .addParameter("$top", "10000")
                .build()
                .toString();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to build URI for device list query", e);
        }

        int pageCount = 0;
        final int MAX_PAGES = 50; // safety cap: 50 × 10000 = 500,000 devices max

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            while (nextUrl != null && pageCount < MAX_PAGES) {
                HttpGet get = new HttpGet(nextUrl);
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
                    if (value.isArray()) {
                        for (JsonNode node : value) {
                            devices.add(OBJECT_MAPPER.convertValue(node, Map.class));
                        }
                    }
                    JsonNode nextLink = json.path("@odata.nextLink");
                    nextUrl = nextLink.isMissingNode() || nextLink.isNull() ? null : nextLink.asText(null);
                    pageCount++;
                    logger.info("Fetched page {} with {} devices (total so far: {})", pageCount,
                        (value.isArray() ? value.size() : 0), devices.size());
                }
            }
        }
        return devices;
    }

    private String uploadScript(String accessToken, String scriptResourcePath) throws RateLimitException {
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
                    if (status == 429) {
                        org.apache.http.Header retryAfterHeader = resp.getFirstHeader("Retry-After");
                        long retryAfterSeconds = retryAfterHeader != null ? Long.parseLong(retryAfterHeader.getValue()) : 60L;
                        EntityUtils.consumeQuietly(resp.getEntity());
                        throw new RateLimitException(retryAfterSeconds);
                    }
                    EntityUtils.consumeQuietly(resp.getEntity());
                    if (status != 200 && status != 201) {
                        logger.error("Failed to upload script {} to Defender library: HTTP {}", scriptResourcePath, status);
                        return null;
                    }
                    logger.info("Script {} uploaded to Defender library", scriptResourcePath);
                    return scriptResourcePath;
                }
            }
        } catch (RateLimitException rle) {
            throw rle;
        } catch (IOException e) {
            logger.error("Error uploading script {}: {}", scriptResourcePath, e.getMessage());
            return null;
        }
    }

    private String uploadScriptWithRateLimit(String accessToken, String scriptResourcePath) {
        if (uploadedScripts.contains(scriptResourcePath)) {
            logger.info("Script {} already uploaded, skipping", scriptResourcePath);
            return scriptResourcePath;
        }

        for (int attempt = 0; attempt < RATE_LIMIT_MAX_RETRIES; attempt++) {
            try {
                long elapsed = System.currentTimeMillis() - lastScriptUploadMs;
                if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                    long sleepMs = MIN_REQUEST_INTERVAL_MS - elapsed;
                    try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) {}
                }

                String result = uploadScript(accessToken, scriptResourcePath);
                lastScriptUploadMs = System.currentTimeMillis();
                if (result != null) {
                    uploadedScripts.add(scriptResourcePath);
                }
                return result;
            } catch (RateLimitException rle) {
                // Cap retry-after to 60s to prevent blocking job execution for very long periods
                long retryAfterSeconds = Math.min(rle.getRetryAfterSeconds(), 60L);
                logger.warn("Script upload {} rate limited, sleeping {}s before retry (attempt {}/{})",
                    scriptResourcePath, retryAfterSeconds, attempt + 1, RATE_LIMIT_MAX_RETRIES);
                try { Thread.sleep(retryAfterSeconds * 1_000L); } catch (InterruptedException ignored) {}
            } catch (Exception e) {
                logger.warn("Script upload {} failed (attempt {}/{}): {}",
                    scriptResourcePath, attempt + 1, RATE_LIMIT_MAX_RETRIES, e.getMessage());
                return null;
            }
        }
        logger.error("Failed to upload script {} after {} retries", scriptResourcePath, RATE_LIMIT_MAX_RETRIES);
        return null;
    }

    private String submitLiveResponseAction(CloseableHttpClient httpClient, String accessToken,
            String deviceId, String scriptName) throws IOException, RateLimitException, PermanentSkipException {
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
                String actionId = OBJECT_MAPPER.readTree(responseBody).path("id").asText(null);
                logger.info("Live Response action submitted on device {}: actionId={}", deviceId, actionId);
                return actionId;
            }
            if (status == 429) {
                // Read Retry-After header (seconds), default 60s
                org.apache.http.Header retryAfterHeader = resp.getFirstHeader("Retry-After");
                long retryAfterSeconds = retryAfterHeader != null ? Long.parseLong(retryAfterHeader.getValue()) : 60L;
                throw new RateLimitException(retryAfterSeconds);
            }
            if (status == 400) {
                String errorCode = OBJECT_MAPPER.readTree(responseBody).path("error").path("code").asText("");
                if ("ActiveRequestAlreadyExists".equals(errorCode)) {
                    return null;
                }
                if ("ClientVersionNotSupported".equals(errorCode) || "OsPlatformNotSupported".equals(errorCode)) {
                    throw new PermanentSkipException(errorCode);
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
    private Map<String, Object> pollActionUntilDone(CloseableHttpClient httpClient, String accessToken, String actionId, AccountJob job)
            throws IOException {
        long deadline = System.currentTimeMillis() + LIVE_RESPONSE_MAX_WAIT_MS;
        int pollCount = 0;
        logger.info("Starting to poll action: actionId={}, deadline in {} seconds", actionId, LIVE_RESPONSE_MAX_WAIT_MS / 1000);

        while (System.currentTimeMillis() < deadline) {
            String pollUrl = "https://api.securitycenter.microsoft.com/api/machineactions/" + actionId;
            HttpGet get = new HttpGet(pollUrl);
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status != 200) {
                    logger.error("Polling action {} returned HTTP {} - Response: {}", actionId, status, body);
                    throw new IOException("Polling action " + actionId + " returned HTTP " + status);
                }
                JsonNode json = OBJECT_MAPPER.readTree(body);
                String actionStatus = json.path("status").asText("unknown");

                // Log every 10 polls (every 50 seconds) for visibility
                if (pollCount % 10 == 0) {
                    long secondsRemaining = (deadline - System.currentTimeMillis()) / 1000;
                    logger.info("Action {} poll #{}: status={}, {} seconds remaining", actionId, pollCount, actionStatus, secondsRemaining);
                }

                if ("Succeeded".equals(actionStatus) || "Failed".equals(actionStatus)
                        || "Cancelled".equals(actionStatus) || "TimeOut".equals(actionStatus)) {
                    logger.info("Action {} finished with status: {} (pollCount={})", actionId, actionStatus, pollCount);
                    return OBJECT_MAPPER.convertValue(json, Map.class);
                }
            } catch (Exception e) {
                logger.error("Error polling action {}: {}", actionId, e.getMessage(), e);
                throw new IOException("Error polling action " + actionId, e);
            }

            try { Thread.sleep(LIVE_RESPONSE_POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            pollCount++;
        }

        long timeoutAfter = System.currentTimeMillis() - deadline + LIVE_RESPONSE_MAX_WAIT_MS;
        logger.warn("Action {} TIMEOUT after {} seconds and {} polls - returning TimedOut status", actionId, timeoutAfter / 1000, pollCount);

        Map<String, Object> timeout = new HashMap<>();
        timeout.put("status", "TimedOut");
        timeout.put("actionId", actionId);
        return timeout;
    }

    private String getActionOutputDownloadLink(CloseableHttpClient httpClient, String accessToken, String actionId)
            throws IOException {
        logger.info("Fetching download link for actionId={}", actionId);
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
            String downloadUrl = OBJECT_MAPPER.readTree(body).path("value").asText(null);
            logger.info("Got download link for actionId={}: {}", actionId, downloadUrl);
            return downloadUrl;
        }
    }

    private JsonNode downloadAndParseOutput(CloseableHttpClient httpClient, String downloadUrl) {
        try {
            logger.info("Downloading output from: {}", downloadUrl);
            HttpGet get = new HttpGet(downloadUrl);
            try (CloseableHttpResponse resp = httpClient.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                byte[] data = EntityUtils.toByteArray(resp.getEntity());
                logger.info("Downloaded {} bytes (HTTP {})", data.length, status);

                // Try extracting stdout and stderr from ZIP
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                     ZipInputStream zis = new ZipInputStream(bais)) {
                    ZipEntry entry;
                    JsonNode result = null;
                    int entryCount = 0;
                    while ((entry = zis.getNextEntry()) != null) {
                        entryCount++;
                        String name = entry.getName();
                        String baseName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                        logger.debug("Found ZIP entry: {}", name);

                        // Extract stdout (main JSON output)
                        if (baseName.equals("stdout") || baseName.startsWith("stdout_")) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = zis.read(buf)) > 0) baos.write(buf, 0, len);
                            String stdout = sanitizeJson(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                            result = OBJECT_MAPPER.readTree(stdout);
                        }

                        // Extract stderr (debug logs from script)
                        if (baseName.equals("stderr") || baseName.startsWith("stderr_")) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = zis.read(buf)) > 0) baos.write(buf, 0, len);
                            String stderr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                            if (!stderr.trim().isEmpty()) {
                                logger.info("Script debug output: {}", stderr);
                            }
                        }
                    }
                    logger.info("Processed {} ZIP entries", entryCount);
                    if (result != null) {
                        JsonNode unwrapped = unwrapScriptOutput(result);
                        logger.info("Successfully parsed output from ZIP (unwrapped={})", unwrapped != result);
                        return unwrapped;
                    }
                    if (entryCount == 0) {
                        // Not a ZIP archive, try as raw JSON
                        logger.info("No ZIP entries found, trying raw JSON");
                        String content = sanitizeJson(new String(data, StandardCharsets.UTF_8));
                        JsonNode parsed = OBJECT_MAPPER.readTree(content);
                        // Defender Live Response wraps script output in {"script_output":"<json string>"}.
                        // Unwrap if present.
                        JsonNode unwrapped = unwrapScriptOutput(parsed);
                        logger.info("Successfully parsed as raw JSON (unwrapped={})", unwrapped != parsed);
                        return unwrapped;
                    }
                    logger.warn("No stdout found in ZIP file with {} entries", entryCount);
                } catch (Exception zipEx) {
                    // ZIP parsing or JSON parsing failed, try raw JSON fallback
                    logger.info("Fallback: ZIP parsing/JSON parsing failed: {}", zipEx.getMessage());
                    try {
                        String content = sanitizeJson(new String(data, StandardCharsets.UTF_8));
                        JsonNode parsed = OBJECT_MAPPER.readTree(content);
                        JsonNode unwrapped = unwrapScriptOutput(parsed);
                        logger.info("Successfully parsed raw JSON from fallback (unwrapped={})", unwrapped != parsed);
                        return unwrapped;
                    } catch (Exception e2) {
                        logger.error("Fallback JSON parsing also failed: {}", e2.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to download/parse script output: {}", e.getMessage(), e);
        }
        logger.error("No output was successfully parsed");
        return null;
    }

    /**
     * Defender Live Response wraps script output in a JSON envelope:
     *   { "script_name": "...", "exit_code": 0, "script_output": "<stringified JSON>", "script_errors": "..." }
     * The PowerShell variant prefixes script_output with a "Transcript started..." line and
     * appends null bytes. This helper extracts and parses the inner JSON, returning the
     * original node when no wrapping is detected.
     */
    private JsonNode unwrapScriptOutput(JsonNode parsed) {
        if (parsed == null || !parsed.has("script_output")) {
            return parsed;
        }
        String scriptOutput = parsed.path("script_output").asText("");
        if (scriptOutput.isEmpty()) {
            logger.warn("script_output field is empty; returning wrapper as-is");
            return parsed;
        }
        // Strip PowerShell transcript prefix (if present) and trailing null bytes
        String cleaned = scriptOutput;
        if (cleaned.startsWith("Transcript started")) {
            int nlIdx = cleaned.indexOf('\n');
            if (nlIdx >= 0) cleaned = cleaned.substring(nlIdx + 1);
        }
        // Trim to the JSON braces — most reliable way to isolate the payload
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            logger.warn("Could not locate JSON object in script_output; returning wrapper as-is");
            return parsed;
        }
        cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        try {
            return OBJECT_MAPPER.readTree(cleaned);
        } catch (Exception e) {
            logger.error("Failed to parse inner script_output JSON: {} (preview={})",
                e.getMessage(),
                cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned);
            return parsed;
        }
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

    // Skill metadata extracted from YAML frontmatter (---name:... description:...---) at the
    // top of SKILL.md content. Matches mcp-endpoint-shield's extractSkillMetadata behavior so
    // guardrail policies that key off description match the same way for both sources.
    private static class SkillMetadata {
        String name = "";
        String description = "";
        String content = "";
    }

    /**
     * Extracts name/description from YAML frontmatter at the head of a skill file and the
     * markdown content that follows. Frontmatter is the block between two `---` lines.
     * Mirrors mcp-endpoint-shield/mcp/skill_validator.go:extractSkillMetadata.
     */
    private static SkillMetadata extractSkillMetadata(String fullContent) {
        SkillMetadata meta = new SkillMetadata();
        meta.content = fullContent == null ? "" : fullContent;
        if (fullContent == null || !fullContent.startsWith("---")) {
            return meta;
        }
        String[] lines = fullContent.split("\n", -1);
        int closeIdx = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                closeIdx = i;
                break;
            }
        }
        if (closeIdx <= 0) return meta;

        // Body after frontmatter
        StringBuilder body = new StringBuilder();
        for (int i = closeIdx + 1; i < lines.length; i++) {
            if (i > closeIdx + 1) body.append('\n');
            body.append(lines[i]);
        }
        meta.content = body.toString();

        // Parse frontmatter: simple `key: value` lines (no nested YAML structures expected)
        for (int i = 1; i < closeIdx; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            // Strip surrounding quotes if present
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if ("name".equals(key) && meta.name.isEmpty()) meta.name = value;
            else if ("title".equals(key) && meta.name.isEmpty()) meta.name = value;
            else if ("description".equals(key) && meta.description.isEmpty()) meta.description = value;
        }
        return meta;
    }

    // Returns the canonical skill name: frontmatter name → parent directory → filename.
    // Mirrors mcp-endpoint-shield/mcp/skill_detector.go:deriveSkillName.
    private static String deriveSkillName(SkillMetadata meta, String filePath) {
        if (meta != null && meta.name != null && !meta.name.isEmpty()) {
            return normalizeSlug(meta.name);
        }
        String sep = filePath.contains("\\") ? "\\" : "/";
        int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String parentDir = "";
        if (lastSep > 0) {
            String parentPath = filePath.substring(0, lastSep);
            int parentSep = Math.max(parentPath.lastIndexOf('/'), parentPath.lastIndexOf('\\'));
            parentDir = parentSep >= 0 ? parentPath.substring(parentSep + 1) : parentPath;
        }
        String parentLower = parentDir.toLowerCase();
        boolean genericParent = parentLower.isEmpty() || parentLower.equals(".") || parentLower.equals("skills");
        if (!genericParent) {
            return normalizeSlug(parentDir);
        }
        return extractSkillName(filePath);
    }

    private static String normalizeSlug(String s) {
        return s.toLowerCase().replaceAll("[\\s_]+", "-").replaceAll("[^a-z0-9-]", "");
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

    private static class RateLimitException extends Exception {
        private final long retryAfterSeconds;
        RateLimitException(long retryAfterSeconds) {
            super("Rate limit exceeded, retry after " + retryAfterSeconds + "s");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    private static class PermanentSkipException extends Exception {
        PermanentSkipException(String code) {
            super(code);
        }
    }
}
