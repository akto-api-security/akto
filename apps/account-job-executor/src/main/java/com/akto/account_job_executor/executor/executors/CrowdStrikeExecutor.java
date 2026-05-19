package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.crowdstrike_integration.CrowdStrikeIntegration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executor for CrowdStrike Falcon integration.
 *
 * Three phases:
 *   1. Installed software inventory (Falcon Discover API)
 *   2. Process events (Event Streams / RTR ps command)
 *   3. MCP config + skills discovery via Real-Time Response (RTR)
 *
 * Authentication: OAuth2 client-credentials — POST /oauth2/token
 * Script execution: RTR admin runscript with -CloudFile flag (same bash/ps1 scripts as SentinelOne/Defender)
 */
public class CrowdStrikeExecutor extends AccountJobExecutor {

    public static final CrowdStrikeExecutor INSTANCE = new CrowdStrikeExecutor();

    private static final LoggerMaker loggerMaker = new LoggerMaker(CrowdStrikeExecutor.class, LogDb.DASHBOARD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS  = 60_000;
    private static final int RTR_POLL_INTERVAL_MS = 5_000;
    private static final int RTR_MAX_WAIT_MS      = 5 * 60 * 1000; // 5 minutes
    private static final int RTR_SESSION_TIMEOUT_S = 600;           // 10 minute RTR session

    private static final String DEFAULT_BASE_URL = "https://api.crowdstrike.com";

    // Shared script cache: scriptName -> CrowdStrike script ID (per executor lifetime)
    private final ConcurrentHashMap<String, String> uploadedScripts = new ConcurrentHashMap<>();
    // Deduplicate MCP collections created within a single job run
    private final Set<String> createdCollectionIds = ConcurrentHashMap.newKeySet();

    public static final List<String> APPS_TOOL_LIST = Arrays.asList(
        "Claude", "Cursor", "Copilot", "Windsurf", "Antigravity", "Codex", "Ollama, VSCode"
    );

    private CrowdStrikeExecutor() {}

    // ── Executor interface ────────────────────────────────────────────────────

    @Override
    protected void runJob(AccountJob job) throws Exception {
        Map<String, Object> jobConfig = job.getConfig();
        if (jobConfig == null || jobConfig.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        CrowdStrikeIntegration integration = OBJECT_MAPPER.convertValue(jobConfig, CrowdStrikeIntegration.class);

        if (integration.getClientId() == null || integration.getClientId().isEmpty()) {
            throw new IllegalStateException("CrowdStrike clientId not configured");
        }
        if (integration.getClientSecret() == null || integration.getClientSecret().isEmpty()) {
            throw new IllegalStateException("CrowdStrike clientSecret not configured");
        }

        // Reset per-job deduplication state
        createdCollectionIds.clear();

        String baseUrl = normalizeUrl(
            integration.getBaseUrl() != null && !integration.getBaseUrl().isEmpty()
                ? integration.getBaseUrl()
                : DEFAULT_BASE_URL
        );
        String ingestUrl  = normalizeUrl(integration.getDataIngestionUrl());
        String accessToken = fetchAccessToken(integration.getClientId(), integration.getClientSecret(), baseUrl);

        // Phase 1: Installed software via Falcon Discover
        try {
            runSoftwareInventoryPhase(accessToken, baseUrl, ingestUrl, job);
        } catch (Exception e) {
            loggerMaker.error("CrowdStrike software inventory phase failed for jobId=" + job.getId() + ": " + e.getMessage(), LogDb.DASHBOARD);
        }

        // Phase 2: MCP config + skills discovery via RTR
        try {
            discoverMCPConfigsAndSkills(accessToken, baseUrl, ingestUrl, job);
        } catch (Exception e) {
            loggerMaker.error("CrowdStrike MCP discovery phase failed for jobId=" + job.getId() + ": " + e.getMessage(), LogDb.DASHBOARD);
        }

        loggerMaker.info("CrowdStrike job completed: jobId=" + job.getId(), LogDb.DASHBOARD);
    }

    // ── Phase 1: Installed software inventory ────────────────────────────────

    private void runSoftwareInventoryPhase(String accessToken, String baseUrl, String ingestUrl, AccountJob job) throws IOException {
        loggerMaker.info("CrowdStrike: starting software inventory phase for jobId=" + job.getId(), LogDb.DASHBOARD);

        for (String tool : APPS_TOOL_LIST) {
            try {
                List<Map<String, Object>> apps = fetchApplicationsForTool(tool, accessToken, baseUrl);
                if (apps.isEmpty()) {
                    loggerMaker.info("CrowdStrike: no apps found for tool: " + tool, LogDb.DASHBOARD);
                    continue;
                }
                List<Map<String, Object>> batch = new ArrayList<>();
                for (Map<String, Object> app : apps) {
                    batch.add(toSoftwareIngestionRecord(app, tool, job.getAccountId()));
                }
                sendBatch(batch, ingestUrl);
                loggerMaker.info("CrowdStrike: ingested " + batch.size() + " app record(s) for " + tool, LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.error("CrowdStrike: software inventory error for " + tool + ": " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    /**
     * Uses Falcon Discover /discover/combined/applications/v1 with an FQL name filter.
     * Returns all matching application records across all hosts (cursor-paginated).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchApplicationsForTool(String tool, String accessToken, String baseUrl) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        RequestConfig cfg = buildRequestConfig();
        String after = null;

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            do {
                StringBuilder url = new StringBuilder(baseUrl)
                    .append("/discover/combined/applications/v1?limit=500")
                    .append("&filter=name%3A%22*").append(urlEncode(tool)).append("*%22");
                if (after != null) {
                    url.append("&after=").append(urlEncode(after));
                }

                HttpGet get = new HttpGet(url.toString());
                get.setHeader("Authorization", "Bearer " + accessToken);
                get.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse resp = client.execute(get)) {
                    int status = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity());
                    if (status != 200) {
                        loggerMaker.error("CrowdStrike Discover API error for tool " + tool + ": HTTP " + status, LogDb.DASHBOARD);
                        break;
                    }
                    JsonNode json = OBJECT_MAPPER.readTree(body);
                    JsonNode resources = json.path("resources");
                    if (resources.isArray()) {
                        for (JsonNode node : resources) {
                            result.add(OBJECT_MAPPER.convertValue(node, Map.class));
                        }
                    }
                    after = json.path("meta").path("pagination").path("after").asText(null);
                    if (after != null && after.isEmpty()) after = null;
                }
            } while (after != null);
        }
        return result;
    }

    private Map<String, Object> toSoftwareIngestionRecord(Map<String, Object> app, String tool, int accountId) throws IOException {
        Map<String, Object> record = new HashMap<>();

        String deviceName = "unknown-device";
        JsonNode hostNode = OBJECT_MAPPER.convertValue(app.get("host"), JsonNode.class);
        if (hostNode != null) {
            deviceName = hostNode.path("hostname").asText("unknown-device");
        }
        String appName = getStringOrDefault(app, "name", "unknown");
        String slug = toolSlug(tool);
        String host = deviceName + ".ai-agent." + slug;

        record.put("path", "/crowdstrike/software-inventory/" + appName + "/" + deviceName);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");
        record.put("requestPayload", "{}");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(app));

        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("host", host);
        reqHeaders.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqHeaders));
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
        tagMap.put("connector", "CROWDSTRIKE");
        tagMap.put("ai-agent", slug);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    // ── Phase 2: MCP config + skills discovery via RTR ───────────────────────

    private void discoverMCPConfigsAndSkills(String accessToken, String baseUrl, String ingestUrl, AccountJob job) throws Exception {
        List<Map<String, Object>> devices = fetchDeviceList(accessToken, baseUrl);
        if (devices.isEmpty()) {
            loggerMaker.info("CrowdStrike: no devices found for MCP discovery, jobId=" + job.getId(), LogDb.DASHBOARD);
            return;
        }

        List<String> unixDeviceIds    = new ArrayList<>();
        List<String> windowsDeviceIds = new ArrayList<>();
        Map<String, String> deviceNames = new HashMap<>();

        for (Map<String, Object> device : devices) {
            String deviceId   = getStringOrDefault(device, "device_id", "");
            String deviceName = getStringOrDefault(device, "hostname", "unknown");
            String platform   = getStringOrDefault(device, "platform_name", "").toLowerCase();
            if (deviceId.isEmpty()) continue;
            deviceNames.put(deviceId, deviceName);
            if (platform.contains("windows")) {
                windowsDeviceIds.add(deviceId);
            } else {
                unixDeviceIds.add(deviceId);
            }
        }

        loggerMaker.info("CrowdStrike MCP discovery: " + unixDeviceIds.size() + " Unix, "
            + windowsDeviceIds.size() + " Windows devices, jobId=" + job.getId(), LogDb.DASHBOARD);

        // Upload scripts once — CrowdStrike script library persists across sessions
        final String mcpShId  = uploadScriptIfNeeded("scan_mcp_configs.sh",  accessToken, baseUrl);
        final String mcpPs1Id = uploadScriptIfNeeded("scan_mcp_configs.ps1", accessToken, baseUrl);
        final String sklShId  = uploadScriptIfNeeded("scan_skills.sh",       accessToken, baseUrl);
        final String sklPs1Id = uploadScriptIfNeeded("scan_skills.ps1",      accessToken, baseUrl);

        // Run all 4 sub-phases concurrently
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        if (mcpShId != null && !unixDeviceIds.isEmpty()) {
            futures.add(pool.submit(() ->
                runScriptOnDevices(accessToken, baseUrl, mcpShId, "scan_mcp_configs.sh",
                    unixDeviceIds, deviceNames, "MCP Config (Unix)", ingestUrl, true, job)));
        }
        if (mcpPs1Id != null && !windowsDeviceIds.isEmpty()) {
            futures.add(pool.submit(() ->
                runScriptOnDevices(accessToken, baseUrl, mcpPs1Id, "scan_mcp_configs.ps1",
                    windowsDeviceIds, deviceNames, "MCP Config (Windows)", ingestUrl, true, job)));
        }
        if (sklShId != null && !unixDeviceIds.isEmpty()) {
            futures.add(pool.submit(() ->
                runScriptOnDevices(accessToken, baseUrl, sklShId, "scan_skills.sh",
                    unixDeviceIds, deviceNames, "Skills (Unix)", ingestUrl, false, job)));
        }
        if (sklPs1Id != null && !windowsDeviceIds.isEmpty()) {
            futures.add(pool.submit(() ->
                runScriptOnDevices(accessToken, baseUrl, sklPs1Id, "scan_skills.ps1",
                    windowsDeviceIds, deviceNames, "Skills (Windows)", ingestUrl, false, job)));
        }

        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) {
                loggerMaker.error("CrowdStrike: phase future error: " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
        pool.shutdown();

        loggerMaker.info("CrowdStrike: MCP discovery complete, jobId=" + job.getId(), LogDb.DASHBOARD);
    }

    private void runScriptOnDevices(String accessToken, String baseUrl, String scriptId, String scriptName,
            List<String> deviceIds, Map<String, String> deviceNames, String description,
            String ingestUrl, boolean isMcpConfig, AccountJob job) {
        for (String deviceId : deviceIds) {
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            try {
                JsonNode output = runScriptOnDevice(accessToken, baseUrl, deviceId, scriptName, job);
                if (output == null) {
                    loggerMaker.error(description + ": no output from device " + deviceName, LogDb.DASHBOARD);
                    continue;
                }
                loggerMaker.info(description + ": collected output from device " + deviceName, LogDb.DASHBOARD);
                Map<String, JsonNode> singleDevice = new HashMap<>();
                singleDevice.put(deviceId, output);
                Map<String, String> singleName = new HashMap<>();
                singleName.put(deviceId, deviceName);
                if (isMcpConfig) {
                    ingestMCPDiscoveries(singleDevice, ingestUrl, singleName);
                } else {
                    ingestSkillDiscoveries(singleDevice, ingestUrl, singleName);
                }
            } catch (Exception e) {
                loggerMaker.error(description + ": error on device " + deviceName + ": " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    /**
     * Runs a script on a single device via CrowdStrike RTR:
     *  1. Init RTR session
     *  2. runscript -CloudFile='<name>'
     *  3. Poll until complete
     *  4. Parse stdout JSON
     *  5. Delete session
     */
    private JsonNode runScriptOnDevice(String accessToken, String baseUrl, String deviceId, String scriptName, AccountJob job) {
        String sessionId = null;
        try {
            sessionId = initRtrSession(accessToken, baseUrl, deviceId);
            if (sessionId == null) {
                loggerMaker.error("CrowdStrike: could not init RTR session for device " + deviceId, LogDb.DASHBOARD);
                return null;
            }

            String cloudRequestId = submitRunScript(accessToken, baseUrl, sessionId, deviceId, scriptName);
            if (cloudRequestId == null) {
                loggerMaker.error("CrowdStrike: could not submit runscript for device " + deviceId, LogDb.DASHBOARD);
                return null;
            }

            // Heartbeat while polling
            Map<String, Object> commandResult = pollCommandUntilDone(accessToken, baseUrl, cloudRequestId, job);
            if (commandResult == null) {
                loggerMaker.error("CrowdStrike: command timed out or failed for device " + deviceId, LogDb.DASHBOARD);
                return null;
            }

            String stdout = getStringOrDefault(commandResult, "stdout", "");
            String stderr = getStringOrDefault(commandResult, "stderr", "");

            if (stdout.isEmpty()) {
                if (!stderr.isEmpty()) {
                    loggerMaker.error("CrowdStrike: script stderr for device " + deviceId + ": " + stderr, LogDb.DASHBOARD);
                }
                return null;
            }

            String sanitized = sanitizeJson(stdout);
            // Trim to first JSON object in case PowerShell adds transcript headers
            int firstBrace = sanitized.indexOf('{');
            int lastBrace  = sanitized.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                sanitized = sanitized.substring(firstBrace, lastBrace + 1);
            }
            return OBJECT_MAPPER.readTree(sanitized);

        } catch (Exception e) {
            loggerMaker.error("CrowdStrike: runScriptOnDevice error for device " + deviceId + ": " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        } finally {
            if (sessionId != null) {
                deleteRtrSession(accessToken, baseUrl, sessionId);
            }
        }
    }

    // ── RTR helpers ───────────────────────────────────────────────────────────

    private String initRtrSession(String accessToken, String baseUrl, String deviceId) throws IOException {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            Map<String, Object> body = new HashMap<>();
            body.put("device_id", deviceId);
            body.put("queue_offline", false);
            body.put("timeout", RTR_SESSION_TIMEOUT_S);
            body.put("timeout_duration", RTR_SESSION_TIMEOUT_S + "s");

            HttpPost post = new HttpPost(baseUrl + "/real-time-response/entities/sessions/v1");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String respBody = EntityUtils.toString(resp.getEntity());
                if (status != 200 && status != 201) {
                    loggerMaker.error("CrowdStrike: init RTR session HTTP " + status + " for device " + deviceId + ": " + respBody, LogDb.DASHBOARD);
                    return null;
                }
                JsonNode json = OBJECT_MAPPER.readTree(respBody);
                JsonNode resources = json.path("resources");
                if (resources.isArray() && resources.size() > 0) {
                    return resources.get(0).path("session_id").asText(null);
                }
                return null;
            }
        }
    }

    /**
     * Submits runscript -CloudFile='<scriptName>' via the admin-command endpoint.
     * Returns cloud_request_id for polling.
     */
    private String submitRunScript(String accessToken, String baseUrl, String sessionId, String deviceId, String scriptName) throws IOException {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            Map<String, Object> body = new HashMap<>();
            body.put("session_id", sessionId);
            body.put("device_id", deviceId);
            body.put("base_command", "runscript");
            body.put("command_string", "runscript -CloudFile='" + scriptName + "'");
            body.put("persist", false);
            body.put("id", 0);

            HttpPost post = new HttpPost(baseUrl + "/real-time-response/entities/admin-command/v1");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String respBody = EntityUtils.toString(resp.getEntity());
                if (status != 200 && status != 201) {
                    loggerMaker.error("CrowdStrike: submit runscript HTTP " + status + " device=" + deviceId + ": " + respBody, LogDb.DASHBOARD);
                    return null;
                }
                JsonNode json = OBJECT_MAPPER.readTree(respBody);
                JsonNode resources = json.path("resources");
                if (resources.isArray() && resources.size() > 0) {
                    return resources.get(0).path("cloud_request_id").asText(null);
                }
                return null;
            }
        }
    }

    /**
     * Polls GET /real-time-response/entities/admin-command/v1?cloud_request_id=<id>&sequence_id=0
     * until complete=true or timeout.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pollCommandUntilDone(String accessToken, String baseUrl, String cloudRequestId, AccountJob job) {
        long deadline = System.currentTimeMillis() + RTR_MAX_WAIT_MS;
        int pollCount = 0;
        RequestConfig cfg = buildRequestConfig();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            while (System.currentTimeMillis() < deadline) {
                if (job != null && pollCount % 10 == 0) {
                    try { CyborgApiClient.updateJobHeartbeat(job.getId()); } catch (Exception ignored) {}
                }

                String url = baseUrl + "/real-time-response/entities/admin-command/v1"
                    + "?cloud_request_id=" + urlEncode(cloudRequestId) + "&sequence_id=0";
                HttpGet get = new HttpGet(url);
                get.setHeader("Authorization", "Bearer " + accessToken);

                try (CloseableHttpResponse resp = client.execute(get)) {
                    int status = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity());
                    if (status != 200) {
                        loggerMaker.error("CrowdStrike: poll command HTTP " + status + " cloudRequestId=" + cloudRequestId, LogDb.DASHBOARD);
                        return null;
                    }
                    JsonNode json = OBJECT_MAPPER.readTree(body);
                    JsonNode resources = json.path("resources");
                    if (resources.isArray() && resources.size() > 0) {
                        JsonNode resource = resources.get(0);
                        boolean complete = resource.path("complete").asBoolean(false);
                        if (complete) {
                            return OBJECT_MAPPER.convertValue(resource, Map.class);
                        }
                        // Non-zero error code means terminal failure
                        int errorCode = resource.path("error_code").asInt(0);
                        if (errorCode != 0) {
                            loggerMaker.error("CrowdStrike: command failed with error_code=" + errorCode
                                + " stderr=" + resource.path("stderr").asText(""), LogDb.DASHBOARD);
                            return null;
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.error("CrowdStrike: poll error for cloudRequestId=" + cloudRequestId + ": " + e.getMessage(), LogDb.DASHBOARD);
                    return null;
                }

                try { Thread.sleep(RTR_POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
                pollCount++;
            }
        } catch (IOException e) {
            loggerMaker.error("CrowdStrike: could not create HTTP client for polling: " + e.getMessage(), LogDb.DASHBOARD);
        }

        loggerMaker.error("CrowdStrike: command timed out for cloudRequestId=" + cloudRequestId, LogDb.DASHBOARD);
        return null;
    }

    private void deleteRtrSession(String accessToken, String baseUrl, String sessionId) {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpDelete delete = new HttpDelete(baseUrl + "/real-time-response/entities/sessions/v1?session_id=" + urlEncode(sessionId));
            delete.setHeader("Authorization", "Bearer " + accessToken);
            try (CloseableHttpResponse resp = client.execute(delete)) {
                EntityUtils.consumeQuietly(resp.getEntity());
            }
        } catch (Exception e) {
            loggerMaker.error("CrowdStrike: failed to delete RTR session " + sessionId + ": " + e.getMessage(), LogDb.DASHBOARD);
        }
    }

    // ── Script management ─────────────────────────────────────────────────────

    /**
     * Uploads the script from classpath resources to the CrowdStrike RTR script library
     * if not already uploaded. Returns the script name (used as -CloudFile reference).
     *
     * CrowdStrike uses script names as the reference identifier (not a numeric ID).
     * If the script already exists in the library, we reuse it by name.
     */
    private String uploadScriptIfNeeded(String scriptResourceName, String accessToken, String baseUrl) {
        // Check in-memory cache first
        if (uploadedScripts.containsKey(scriptResourceName)) {
            return uploadedScripts.get(scriptResourceName);
        }

        String classpathResource = "/scripts/" + scriptResourceName;
        try (InputStream scriptStream = CrowdStrikeExecutor.class.getResourceAsStream(classpathResource)) {
            if (scriptStream == null) {
                loggerMaker.error("CrowdStrike: script not found in classpath: " + classpathResource, LogDb.DASHBOARD);
                return null;
            }
            byte[] scriptBytes = readAllBytes(scriptStream);

            // Check if script already exists in CrowdStrike library to avoid duplicates
            String existingName = findExistingScript(scriptResourceName, accessToken, baseUrl);
            if (existingName != null) {
                loggerMaker.info("CrowdStrike: reusing existing script: " + scriptResourceName, LogDb.DASHBOARD);
                uploadedScripts.put(scriptResourceName, scriptResourceName);
                return scriptResourceName;
            }

            // Determine platform from file extension
            String[] platform = scriptResourceName.endsWith(".ps1")
                ? new String[]{"windows"}
                : new String[]{"linux", "mac"};

            RequestConfig cfg = buildRequestConfig();
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("name", scriptResourceName);
                builder.addTextBody("description", "Akto MCP/Skill discovery script");
                builder.addTextBody("permission_type", "private");
                for (String p : platform) {
                    builder.addTextBody("platform", p);
                }
                builder.addBinaryBody("file", scriptBytes, ContentType.APPLICATION_OCTET_STREAM, scriptResourceName);

                HttpPost post = new HttpPost(baseUrl + "/real-time-response/entities/scripts/v1");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setEntity(builder.build());

                try (CloseableHttpResponse resp = client.execute(post)) {
                    int status = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity());
                    if (status != 200 && status != 201) {
                        loggerMaker.error("CrowdStrike: script upload failed HTTP " + status + " for " + scriptResourceName + ": " + body, LogDb.DASHBOARD);
                        return null;
                    }
                    loggerMaker.info("CrowdStrike: uploaded script " + scriptResourceName, LogDb.DASHBOARD);
                    uploadedScripts.put(scriptResourceName, scriptResourceName);
                    return scriptResourceName;
                }
            }
        } catch (IOException e) {
            loggerMaker.error("CrowdStrike: error uploading script " + scriptResourceName + ": " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        }
    }

    /**
     * Searches the CrowdStrike RTR script library for a script with a matching name.
     * Returns the script name if found, null otherwise.
     */
    private String findExistingScript(String scriptName, String accessToken, String baseUrl) {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpGet get = new HttpGet(baseUrl + "/real-time-response/queries/falcon-scripts/v1"
                + "?filter=name%3A%22" + urlEncode(scriptName) + "%22&limit=1");
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse resp = client.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status != 200) return null;

                JsonNode json = OBJECT_MAPPER.readTree(body);
                JsonNode resources = json.path("resources");
                if (resources.isArray() && resources.size() > 0) {
                    return scriptName; // script exists
                }
            }
        } catch (Exception e) {
            loggerMaker.error("CrowdStrike: error checking existing scripts: " + e.getMessage(), LogDb.DASHBOARD);
        }
        return null;
    }

    // ── Device listing ────────────────────────────────────────────────────────

    /**
     * Fetches all active/online devices using the Hosts API:
     *   GET /devices/queries/devices-scroll/v1 (cursor-paginated IDs)
     *   POST /devices/entities/devices/v2       (bulk details)
     */
    private List<Map<String, Object>> fetchDeviceList(String accessToken, String baseUrl) throws IOException {
        List<String> deviceIds = new ArrayList<>();
        String offset = null;
        RequestConfig cfg = buildRequestConfig();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            // Step 1: collect all device IDs
            do {
                StringBuilder url = new StringBuilder(baseUrl)
                    .append("/devices/queries/devices-scroll/v1?limit=5000");
                if (offset != null) url.append("&offset=").append(urlEncode(offset));

                HttpGet get = new HttpGet(url.toString());
                get.setHeader("Authorization", "Bearer " + accessToken);

                try (CloseableHttpResponse resp = client.execute(get)) {
                    int status = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity());
                    if (status != 200) {
                        throw new IOException("Failed to fetch device IDs: HTTP " + status + " — " + body);
                    }
                    JsonNode json = OBJECT_MAPPER.readTree(body);
                    JsonNode resources = json.path("resources");
                    if (resources.isArray()) {
                        for (JsonNode id : resources) deviceIds.add(id.asText());
                    }
                    offset = json.path("meta").path("pagination").path("offset").asText(null);
                    if (offset != null && offset.isEmpty()) offset = null;
                    // CrowdStrike scroll returns the same offset when on last page
                    boolean noMore = json.path("meta").path("pagination").path("total").asInt(0) <= deviceIds.size();
                    if (noMore) break;
                }
            } while (offset != null);

            loggerMaker.info("CrowdStrike: found " + deviceIds.size() + " device IDs", LogDb.DASHBOARD);
            if (deviceIds.isEmpty()) return new ArrayList<>();

            // Step 2: fetch device details in batches of 5000
            List<Map<String, Object>> devices = new ArrayList<>();
            int batchSize = 500; // safe batch for POST body
            for (int i = 0; i < deviceIds.size(); i += batchSize) {
                List<String> batch = deviceIds.subList(i, Math.min(i + batchSize, deviceIds.size()));
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("ids", batch);

                HttpPost post = new HttpPost(baseUrl + "/devices/entities/devices/v2");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(reqBody), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse resp = client.execute(post)) {
                    int status = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity());
                    if (status != 200) {
                        loggerMaker.error("CrowdStrike: devices entity fetch HTTP " + status, LogDb.DASHBOARD);
                        continue;
                    }
                    JsonNode json = OBJECT_MAPPER.readTree(body);
                    JsonNode resources = json.path("resources");
                    if (resources.isArray()) {
                        for (JsonNode node : resources) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> deviceMap = OBJECT_MAPPER.convertValue(node, Map.class);
                            devices.add(deviceMap);
                        }
                    }
                }
            }
            loggerMaker.info("CrowdStrike: fetched details for " + devices.size() + " device(s)", LogDb.DASHBOARD);
            return devices;
        }
    }

    // ── MCP ingestion ─────────────────────────────────────────────────────────

    private void ingestMCPDiscoveries(Map<String, JsonNode> discoveriesByDevice, String ingestUrl,
            Map<String, String> deviceNames) {
        List<Map<String, Object>> batchData = new ArrayList<>();
        Map<String, ServerCollectionInfo> serverCollections = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : discoveriesByDevice.entrySet()) {
            String deviceId   = entry.getKey();
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            JsonNode discovery = entry.getValue();

            String os   = discovery.path("os").asText("unknown");
            String user = discovery.path("user").asText("unknown");
            JsonNode configsFound = discovery.path("configs_found");
            if (!configsFound.isArray()) continue;

            for (JsonNode config : configsFound) {
                String configPath = config.path("path").asText("");
                String client = config.path("client").asText("unknown");
                long size     = config.path("size").asLong(0);
                long modified = config.path("modified").asLong(0);
                JsonNode servers = config.path("servers");

                if (configPath.isEmpty()) continue;
                String clientSlug = clientTypeSlug(client);
                if (!servers.isArray() || servers.size() == 0) continue;

                for (JsonNode server : servers) {
                    String serverName = server.path("name").asText("");
                    String serverType = server.path("type").asText("unknown");
                    String command    = server.path("command").asText("");
                    String url        = server.path("url").asText("");

                    if (serverName.isEmpty()) continue;

                    String mcpServerHost;
                    if (!url.isEmpty()) {
                        String urlHost = serverName;
                        try {
                            java.net.URI parsed = new java.net.URI(url);
                            if (parsed.getHost() != null && !parsed.getHost().isEmpty()) urlHost = parsed.getHost();
                        } catch (Exception ignored) {}
                        mcpServerHost = (deviceName + "." + clientSlug + "." + urlHost).toLowerCase();
                    } else {
                        mcpServerHost = (deviceName + "." + clientSlug + "." + serverName).toLowerCase();
                    }

                    if (!serverCollections.containsKey(mcpServerHost)) {
                        ServerCollectionInfo info = new ServerCollectionInfo();
                        info.collectionName = mcpServerHost;
                        info.serverName  = serverName;
                        info.clientType  = clientSlug;
                        info.timestamp   = modified;
                        info.url         = url;
                        serverCollections.put(mcpServerHost, info);
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
                    tagMap.put("connector", "CROWDSTRIKE");
                    tagMap.put("mcp-server", "MCP Server");
                    tagMap.put("mcp-client", clientSlug);

                    try {
                        Map<String, Object> batch = new HashMap<>();
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
                        loggerMaker.error("CrowdStrike: failed to serialize MCP server discovery: " + e.getMessage(), LogDb.DASHBOARD);
                    }
                }
            }
        }

        if (!serverCollections.isEmpty()) {
            createMCPServerCollections(serverCollections, ingestUrl);
        }
        if (!batchData.isEmpty()) {
            try {
                sendBatch(batchData, ingestUrl);
                loggerMaker.info("CrowdStrike: ingested " + batchData.size() + " MCP server discoveries", LogDb.DASHBOARD);
            } catch (IOException e) {
                loggerMaker.error("CrowdStrike: failed to ingest MCP discoveries: " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    // ── Skills ingestion ──────────────────────────────────────────────────────

    private void ingestSkillDiscoveries(Map<String, JsonNode> discoveriesByDevice, String ingestUrl,
            Map<String, String> deviceNames) {
        List<Map<String, Object>> batchData = new ArrayList<>();
        Map<String, Set<String>> skillsByCollection = new HashMap<>();
        Map<String, String> agentByCollection = new HashMap<>();

        for (Map.Entry<String, JsonNode> entry : discoveriesByDevice.entrySet()) {
            String deviceId   = entry.getKey();
            String deviceName = deviceNames.getOrDefault(deviceId, deviceId);
            JsonNode discovery = entry.getValue();
            JsonNode skillsFound = discovery.path("skills_found");
            if (!skillsFound.isArray()) continue;

            for (JsonNode skill : skillsFound) {
                String path   = skill.path("path").asText("");
                String agent  = skill.path("agent").asText("unknown");
                String rawContent = skill.path("skill_content").asText("");
                if (path.isEmpty()) continue;

                SkillMetadata meta = extractSkillMetadata(rawContent);
                String skillNameFromScript = skill.path("skill_name").asText("");
                String skillName = !skillNameFromScript.isEmpty()
                    ? normalizeSlug(skillNameFromScript)
                    : deriveSkillName(meta, path);

                String syntheticHost = deviceName + ".ai-agent." + agent;

                Map<String, String> reqHeaders = new HashMap<>();
                reqHeaders.put("content-type", "application/json");
                reqHeaders.put("host", syntheticHost);
                reqHeaders.put("x-transport", "STDIO");
                reqHeaders.put("x-skill-name", skillName);
                reqHeaders.put("x-agent-type", agent);

                Map<String, String> tagMap = new HashMap<>();
                tagMap.put("source", "ENDPOINT");
                tagMap.put("connector", "CROWDSTRIKE");
                tagMap.put("skill", skillName);
                if (!"unknown".equals(agent)) {
                    tagMap.put("mcp-client", agent);
                    tagMap.put("ai-agent", agent);
                }

                try {
                    Map<String, Object> reqPayload = new HashMap<>();
                    reqPayload.put("skill_name", skillName);
                    reqPayload.put("skill_description", meta.description);
                    reqPayload.put("skill_content", meta.content.isEmpty() ? rawContent : meta.content);
                    reqPayload.put("agent", agent);
                    reqPayload.put("file_path", path);

                    Map<String, Object> batch = new HashMap<>();
                    batch.put("path", "https://" + syntheticHost + "/skills/" + skillName);
                    batch.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(reqHeaders));
                    batch.put("responseHeaders", "{}");
                    batch.put("method", "POST");
                    batch.put("type", "HTTP/1.1");
                    batch.put("requestPayload", OBJECT_MAPPER.writeValueAsString(reqPayload));
                    batch.put("responsePayload", "{\"status\":\"ok\"}");
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

                    skillsByCollection.computeIfAbsent(syntheticHost, k -> new HashSet<>()).add(skillName);
                    agentByCollection.put(syntheticHost, agent);
                } catch (Exception e) {
                    loggerMaker.error("CrowdStrike: failed to serialize skill discovery: " + e.getMessage(), LogDb.DASHBOARD);
                }
            }
        }

        if (!batchData.isEmpty()) {
            try {
                sendBatch(batchData, ingestUrl);
                loggerMaker.info("CrowdStrike: ingested " + batchData.size() + " skill discoveries", LogDb.DASHBOARD);
            } catch (IOException e) {
                loggerMaker.error("CrowdStrike: failed to ingest skill discoveries: " + e.getMessage(), LogDb.DASHBOARD);
            }
        }

        for (Map.Entry<String, Set<String>> e : skillsByCollection.entrySet()) {
            createAgentSkillCollection(e.getKey(), agentByCollection.get(e.getKey()), e.getValue(), ingestUrl);
        }
    }

    // ── Collection creation ───────────────────────────────────────────────────

    private static class ServerCollectionInfo {
        String collectionName;
        String serverName;
        String clientType;
        long timestamp;
        String url;
    }

    private void createMCPServerCollections(Map<String, ServerCollectionInfo> serverCollections, String ingestUrl) {
        String dbUrl = buildDbAbstractorUrl(ingestUrl);
        RequestConfig cfg = buildRequestConfig();

        for (ServerCollectionInfo info : serverCollections.values()) {
            if (!createdCollectionIds.add(info.collectionName)) continue;
            try {
                int colId = generateCollectionId(info.collectionName, info.timestamp);
                List<Map<String, Object>> tagsList = new ArrayList<>();
                addTag(tagsList, "mcp-server", "MCP Server", info.timestamp);
                addTag(tagsList, "source", "ENDPOINT", info.timestamp);
                addTag(tagsList, "connector", "CROWDSTRIKE", info.timestamp);
                if (info.clientType != null && !info.clientType.isEmpty()) {
                    addTag(tagsList, "mcp-client", info.clientType, info.timestamp);
                }
                if ((info.url == null || info.url.isEmpty()) && info.serverName != null && !info.serverName.isEmpty()) {
                    addTag(tagsList, "local-mcp-server", info.serverName, info.timestamp);
                }

                Map<String, Object> request = new HashMap<>();
                request.put("colId", colId);
                request.put("host", info.collectionName);
                request.put("tagsList", tagsList);

                try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                    HttpPost post = new HttpPost(dbUrl);
                    post.setHeader("Content-Type", "application/json");
                    addDbAbstractorAuth(post);
                    post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(request), ContentType.APPLICATION_JSON));

                    try (CloseableHttpResponse resp = client.execute(post)) {
                        int status = resp.getStatusLine().getStatusCode();
                        EntityUtils.consumeQuietly(resp.getEntity());
                        if (status == 200) {
                            loggerMaker.info("CrowdStrike: created collection for MCP server: " + info.collectionName, LogDb.DASHBOARD);
                        } else {
                            loggerMaker.error("CrowdStrike: failed to create collection for " + info.collectionName + ": HTTP " + status, LogDb.DASHBOARD);
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.error("CrowdStrike: error creating collection for " + info.collectionName + ": " + e.getMessage(), LogDb.DASHBOARD);
            }
        }
    }

    private void createAgentSkillCollection(String collectionHost, String agent, Set<String> skillNames, String ingestUrl) {
        if (skillNames == null || skillNames.isEmpty()) return;
        String dbUrl = buildDbAbstractorUrl(ingestUrl);
        long ts = System.currentTimeMillis() / 1000;

        try {
            int colId = generateCollectionId(collectionHost, ts);
            List<Map<String, Object>> tagsList = new ArrayList<>();
            addTag(tagsList, "source", "ENDPOINT", ts);
            addTag(tagsList, "gen-ai", "Gen AI", ts);
            addTag(tagsList, "connector", "CROWDSTRIKE", ts);
            if (agent != null && !"unknown".equals(agent)) {
                addTag(tagsList, "mcp-client", agent, ts);
                addTag(tagsList, "ai-agent", agent, ts);
            }

            Map<String, Object> request = new HashMap<>();
            request.put("colId", colId);
            request.put("host", collectionHost);
            request.put("tagsList", tagsList);
            request.put("skills", new ArrayList<>(skillNames));

            RequestConfig cfg = buildRequestConfig();
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                HttpPost post = new HttpPost(dbUrl);
                post.setHeader("Content-Type", "application/json");
                addDbAbstractorAuth(post);
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(request), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse resp = client.execute(post)) {
                    int status = resp.getStatusLine().getStatusCode();
                    EntityUtils.consumeQuietly(resp.getEntity());
                    if (status == 200) {
                        loggerMaker.info("CrowdStrike: registered " + skillNames.size() + " skill(s) on collection " + collectionHost, LogDb.DASHBOARD);
                    } else {
                        loggerMaker.error("CrowdStrike: failed to register skills on collection " + collectionHost + ": HTTP " + status, LogDb.DASHBOARD);
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.error("CrowdStrike: error registering skills on collection " + collectionHost + ": " + e.getMessage(), LogDb.DASHBOARD);
        }
    }

    // ── Transport ─────────────────────────────────────────────────────────────

    private void sendBatch(List<Map<String, Object>> batch, String ingestUrl) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", batch);

        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            HttpPost post = new HttpPost(ingestUrl + "/api/ingestData");
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

    // ── OAuth2 token ──────────────────────────────────────────────────────────

    private String fetchAccessToken(String clientId, String clientSecret, String baseUrl) throws IOException {
        String tokenUrl = baseUrl + "/oauth2/token";
        RequestConfig cfg = buildRequestConfig();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            String formBody = "client_id=" + urlEncode(clientId) + "&client_secret=" + urlEncode(clientSecret);

            HttpPost post = new HttpPost(tokenUrl);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new StringEntity(formBody, ContentType.APPLICATION_FORM_URLENCODED));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status != 200 && status != 201) {
                    throw new IOException("CrowdStrike OAuth2 token request failed: HTTP " + status + " — " + body);
                }
                JsonNode json = OBJECT_MAPPER.readTree(body);
                String token = json.path("access_token").asText(null);
                if (token == null || token.isEmpty()) {
                    throw new IOException("No access_token in CrowdStrike OAuth2 response: " + body);
                }
                loggerMaker.info("CrowdStrike: obtained access token (expires_in=" + json.path("expires_in").asInt() + "s)", LogDb.DASHBOARD);
                return token;
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String normalizeUrl(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    private static String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private static RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
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

    private static int generateCollectionId(String name, long ts) {
        return Math.abs((name + ts).hashCode());
    }

    private static void addTag(List<Map<String, Object>> tagsList, String key, String value, long ts) {
        Map<String, Object> tag = new HashMap<>();
        tag.put("lastUpdatedTs", ts);
        tag.put("keyName", key);
        tag.put("value", value);
        tag.put("source", "KUBERNETES");
        tagsList.add(tag);
    }

    private static String buildDbAbstractorUrl(String ingestUrl) {
        String env = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        if (env != null && !env.isEmpty()) {
            return (env.endsWith("/") ? env.substring(0, env.length() - 1) : env) + "/api/createCollectionForHostAndVpc";
        }
        return ingestUrl.replaceAll("/api/ingestData.*", "") + "/api/createCollectionForHostAndVpc";
    }

    private static void addDbAbstractorAuth(HttpPost post) {
        String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (token == null || token.isEmpty()) {
            token = System.getenv("DATABASE_ABSTRACTOR_TOKEN");
        }
        if (token != null && !token.isEmpty()) {
            post.setHeader("Authorization", token);
        }
    }

    private static String toolSlug(String tool) {
        if (tool == null) return "unknown";
        switch (tool.toLowerCase()) {
            case "claude":    return "claude-cli";
            case "cursor":    return "cursor";
            case "copilot":   return "copilot";
            case "windsurf":  return "windsurf";
            case "antigravity": return "antigravity";
            case "codex":     return "codex";
            case "ollama":    return "ollama";
            default: return tool.toLowerCase().replaceAll("[^a-z0-9-]", "-");
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
            case "claude-desktop":  return "claude-desktop";
            case "cursor":          return "cursor";
            case "windsurf":        return "windsurf";
            case "vscode":          return "vscode";
            case "github-cli":      return "github-copilot";
            case "antigravity":     return "antigravity";
            case "container":       return "container";
            default: return clientType.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        }
    }

    // ── Skill metadata extraction (mirrors MicrosoftDefenderExecutor) ─────────

    private static class SkillMetadata {
        String name = "";
        String description = "";
        String content = "";
    }

    private static SkillMetadata extractSkillMetadata(String fullContent) {
        SkillMetadata meta = new SkillMetadata();
        meta.content = fullContent == null ? "" : fullContent;
        if (fullContent == null || !fullContent.startsWith("---")) return meta;

        String[] lines = fullContent.split("\n", -1);
        int closeIdx = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) { closeIdx = i; break; }
        }
        if (closeIdx <= 0) return meta;

        StringBuilder body = new StringBuilder();
        for (int i = closeIdx + 1; i < lines.length; i++) {
            if (i > closeIdx + 1) body.append('\n');
            body.append(lines[i]);
        }
        meta.content = body.toString();

        for (int i = 1; i < closeIdx; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key   = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if ("name".equals(key) && meta.name.isEmpty()) meta.name = value;
            else if ("title".equals(key) && meta.name.isEmpty()) meta.name = value;
            else if ("description".equals(key) && meta.description.isEmpty()) meta.description = value;
        }
        return meta;
    }

    private static String deriveSkillName(SkillMetadata meta, String filePath) {
        if (meta != null && meta.name != null && !meta.name.isEmpty()) {
            return normalizeSlug(meta.name);
        }
        int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String parentDir = "";
        if (lastSep > 0) {
            String parentPath = filePath.substring(0, lastSep);
            int parentSep = Math.max(parentPath.lastIndexOf('/'), parentPath.lastIndexOf('\\'));
            parentDir = parentSep >= 0 ? parentPath.substring(parentSep + 1) : parentPath;
        }
        String parentLower = parentDir.toLowerCase();
        if (!parentLower.isEmpty() && !parentLower.equals(".") && !parentLower.equals("skills")) {
            return normalizeSlug(parentDir);
        }
        return extractSkillName(filePath);
    }

    private static String extractSkillName(String filePath) {
        String fileName = filePath.substring(Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) fileName = fileName.substring(0, dot);
        return fileName.toLowerCase().replaceAll("[\\s_]+", "-").replaceAll("[^a-z0-9-]", "");
    }

    private static String normalizeSlug(String s) {
        return s.toLowerCase().replaceAll("[\\s_]+", "-").replaceAll("[^a-z0-9-]", "");
    }
}
