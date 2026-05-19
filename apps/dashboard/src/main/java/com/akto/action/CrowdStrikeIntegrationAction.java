package com.akto.action;

import com.akto.dao.CrowdStrikeIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.dto.crowdstrike_integration.CrowdStrikeIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.apache.http.entity.mime.MultipartEntityBuilder;

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

@Getter
@Setter
public class CrowdStrikeIntegrationAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CrowdStrikeIntegrationAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String JOB_TYPE = "CROWDSTRIKE_AH";
    private static final String JOB_SUB_TYPE = "FALCON_DISCOVER";
    private static final int DEFAULT_INTERVAL = 3600;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS  = 60_000;

    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String dataIngestionUrl;
    private Integer recurringIntervalSeconds;

    // Guardrails fields (mirrors SentinelOneIntegrationAction)
    private List<String> guardrailType;
    private Map<String, String> guardrailEnvVars;
    private String guardrailTargetMode;
    private List<String> guardrailDeviceIds;
    private List<Map<String, Object>> guardrailTypes;
    private Map<String, Object> guardrailExecution;

    // In-memory dedup for script uploads within one request
    private static final Set<String> uploadedGuardrailScripts = ConcurrentHashMap.newKeySet();

    private CrowdStrikeIntegration crowdStrikeIntegration;
    private List<Map<String, Object>> devices;

    public String fetchCrowdStrikeIntegration() {
        crowdStrikeIntegration = CrowdStrikeIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(CrowdStrikeIntegration.CLIENT_SECRET)
        );
        return Action.SUCCESS.toUpperCase();
    }

    public String addCrowdStrikeIntegration() {
        if (clientId == null || clientId.isEmpty()) {
            addActionError("Please enter a valid Client ID.");
            return Action.ERROR.toUpperCase();
        }
        if (dataIngestionUrl == null || dataIngestionUrl.isEmpty()) {
            addActionError("Please enter a valid data ingestion service URL.");
            return Action.ERROR.toUpperCase();
        }

        int interval = (recurringIntervalSeconds != null && recurringIntervalSeconds > 0)
            ? recurringIntervalSeconds
            : DEFAULT_INTERVAL;

        int now = Context.now();

        // Resolve the client secret: use provided value or fall back to stored one
        String resolvedClientSecret;
        if (clientSecret != null && !clientSecret.isEmpty()) {
            resolvedClientSecret = clientSecret;
        } else {
            CrowdStrikeIntegration existing = CrowdStrikeIntegrationDao.instance.findOne(new BasicDBObject());
            if (existing == null || existing.getClientSecret() == null || existing.getClientSecret().isEmpty()) {
                addActionError("Please enter a valid Client Secret.");
                return Action.ERROR.toUpperCase();
            }
            resolvedClientSecret = existing.getClientSecret();
        }

        // Normalize base URL
        String resolvedBaseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : "https://api.crowdstrike.com";
        if (resolvedBaseUrl.endsWith("/")) resolvedBaseUrl = resolvedBaseUrl.substring(0, resolvedBaseUrl.length() - 1);
        String normalizedIngestUrl = dataIngestionUrl.endsWith("/") ? dataIngestionUrl.substring(0, dataIngestionUrl.length() - 1) : dataIngestionUrl;

        org.bson.conversions.Bson updates = Updates.combine(
            Updates.set(CrowdStrikeIntegration.CLIENT_ID, clientId),
            Updates.set(CrowdStrikeIntegration.CLIENT_SECRET, resolvedClientSecret),
            Updates.set(CrowdStrikeIntegration.BASE_URL, resolvedBaseUrl),
            Updates.set(CrowdStrikeIntegration.DATA_INGESTION_URL, normalizedIngestUrl),
            Updates.set(CrowdStrikeIntegration.RECURRING_INTERVAL_SECONDS, interval),
            Updates.setOnInsert(CrowdStrikeIntegration.CREATED_TS, now),
            Updates.set(CrowdStrikeIntegration.UPDATED_TS, now)
        );
        CrowdStrikeIntegrationDao.instance.updateOne(new BasicDBObject(), updates);

        // Create or update AccountJob
        AccountJob existingJob = AccountJobDao.instance.findOne(
            Filters.and(
                Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
            )
        );

        if (existingJob == null) {
            Map<String, Object> jobConfig = new HashMap<>();
            jobConfig.put(CrowdStrikeIntegration.CLIENT_ID, clientId);
            jobConfig.put(CrowdStrikeIntegration.CLIENT_SECRET, resolvedClientSecret);
            jobConfig.put(CrowdStrikeIntegration.BASE_URL, resolvedBaseUrl);
            jobConfig.put(CrowdStrikeIntegration.DATA_INGESTION_URL, normalizedIngestUrl);

            AccountJob accountJob = new AccountJob(
                Context.accountId.get(), JOB_TYPE, JOB_SUB_TYPE,
                jobConfig, interval, now, now
            );
            accountJob.setJobStatus(JobStatus.SCHEDULED);
            accountJob.setScheduleType(ScheduleType.RECURRING);
            accountJob.setScheduledAt(now);
            accountJob.setHeartbeatAt(0);
            accountJob.setStartedAt(0);
            accountJob.setFinishedAt(0);
            AccountJobDao.instance.insertOne(accountJob);
            loggerMaker.info("Created CrowdStrike account job", LogDb.DASHBOARD);
        } else {
            org.bson.conversions.Bson jobUpdates = Updates.combine(
                Updates.set("config." + CrowdStrikeIntegration.CLIENT_ID, clientId),
                Updates.set("config." + CrowdStrikeIntegration.CLIENT_SECRET, resolvedClientSecret),
                Updates.set("config." + CrowdStrikeIntegration.BASE_URL, resolvedBaseUrl),
                Updates.set("config." + CrowdStrikeIntegration.DATA_INGESTION_URL, normalizedIngestUrl),
                Updates.set(AccountJob.RECURRING_INTERVAL_SECONDS, interval),
                Updates.set(AccountJob.LAST_UPDATED_AT, now),
                Updates.set(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name()),
                Updates.set(AccountJob.SCHEDULED_AT, now)
            );
            AccountJobDao.instance.updateOneNoUpsert(Filters.eq(AccountJob.ID, existingJob.getId()), jobUpdates);
            loggerMaker.info("Updated CrowdStrike account job", LogDb.DASHBOARD);
        }

        loggerMaker.info("CrowdStrike integration saved successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeCrowdStrikeIntegration() {
        CrowdStrikeIntegrationDao.instance.deleteAll(new BasicDBObject());

        AccountJob existingJob = AccountJobDao.instance.findOne(
            Filters.and(
                Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
            )
        );
        if (existingJob != null) {
            AccountJobDao.instance.updateOneNoUpsert(
                Filters.eq(AccountJob.ID, existingJob.getId()),
                Updates.combine(
                    Updates.set(AccountJob.JOB_STATUS, JobStatus.STOPPED.name()),
                    Updates.set(AccountJob.LAST_UPDATED_AT, Context.now())
                )
            );
        }

        loggerMaker.info("CrowdStrike integration removed successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Fetches the device list from CrowdStrike Falcon to show in the UI.
     * Uses scroll-paginated query for IDs + bulk entity fetch.
     */
    public String fetchCrowdStrikeDevices() {
        CrowdStrikeIntegration integration = CrowdStrikeIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("CrowdStrike integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        try {
            String resolvedBase = normalizeUrl(integration.getBaseUrl() != null && !integration.getBaseUrl().isEmpty()
                ? integration.getBaseUrl() : "https://api.crowdstrike.com");
            String accessToken = fetchAccessToken(integration.getClientId(), integration.getClientSecret(), resolvedBase);
            devices = fetchDeviceList(accessToken, resolvedBase);
        } catch (IOException e) {
            loggerMaker.error("Error fetching CrowdStrike devices: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error fetching devices: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> fetchDeviceList(String accessToken, String baseUrl) throws IOException {
        List<String> deviceIds = new ArrayList<>();
        String offset = null;
        RequestConfig cfg = buildRequestConfig();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            do {
                StringBuilder url = new StringBuilder(baseUrl)
                    .append("/devices/queries/devices-scroll/v1?limit=5000");
                if (offset != null) url.append("&offset=").append(encode(offset));

                HttpGet get = new HttpGet(url.toString());
                get.setHeader("Authorization", "Bearer " + accessToken);

                try (CloseableHttpResponse resp = client.execute(get)) {
                    String body = EntityUtils.toString(resp.getEntity());
                    if (resp.getStatusLine().getStatusCode() != 200)
                        throw new IOException("Device query failed: HTTP " + resp.getStatusLine().getStatusCode());

                    JsonNode json = OBJECT_MAPPER.readTree(body);
                    JsonNode resources = json.path("resources");
                    if (resources.isArray()) for (JsonNode id : resources) deviceIds.add(id.asText());

                    offset = json.path("meta").path("pagination").path("offset").asText(null);
                    if (offset != null && offset.isEmpty()) offset = null;
                    int total = json.path("meta").path("pagination").path("total").asInt(0);
                    if (deviceIds.size() >= total) break;
                }
            } while (offset != null);

            if (deviceIds.isEmpty()) return new ArrayList<>();

            // Fetch device details in batches of 500
            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < deviceIds.size(); i += 500) {
                List<String> batch = deviceIds.subList(i, Math.min(i + 500, deviceIds.size()));
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("ids", batch);

                HttpPost post = new HttpPost(baseUrl + "/devices/entities/devices/v2");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(reqBody), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse resp = client.execute(post)) {
                    if (resp.getStatusLine().getStatusCode() != 200) { EntityUtils.consumeQuietly(resp.getEntity()); continue; }
                    JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                    JsonNode resources = json.path("resources");
                    if (resources.isArray()) {
                        for (JsonNode node : resources) {
                            Map<String, Object> device = new HashMap<>();
                            device.put("id", node.path("device_id").asText(""));
                            device.put("hostname", node.path("hostname").asText(""));
                            device.put("platform", node.path("platform_name").asText(""));
                            device.put("osVersion", node.path("os_version").asText(""));
                            device.put("lastSeen", node.path("last_seen").asText(""));
                            result.add(device);
                        }
                    }
                }
            }
            return result;
        }
    }

    private String fetchAccessToken(String clientId, String clientSecret, String baseUrl) throws IOException {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            String formBody = "client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret);
            HttpPost post = new HttpPost(baseUrl + "/oauth2/token");
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new StringEntity(formBody, ContentType.APPLICATION_FORM_URLENCODED));

            try (CloseableHttpResponse resp = client.execute(post)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                if (status != 200 && status != 201)
                    throw new IOException("CrowdStrike OAuth2 failed: HTTP " + status + " — " + body);
                JsonNode json = OBJECT_MAPPER.readTree(body);
                String token = json.path("access_token").asText(null);
                if (token == null || token.isEmpty())
                    throw new IOException("No access_token in CrowdStrike response: " + body);
                return token;
            }
        }
    }

    // ── Guardrails ────────────────────────────────────────────────────────────

    public String fetchCrowdStrikeGuardrailTypes() {
        guardrailTypes = new ArrayList<>();

        Map<String, Object> cursorHooks = new HashMap<>();
        cursorHooks.put("type", "cursor-hooks");
        cursorHooks.put("displayName", "Cursor IDE Hooks");
        cursorHooks.put("description", "Install Akto guardrails hooks for Cursor IDE");
        guardrailTypes.add(cursorHooks);

        Map<String, Object> openClawGuardrails = new HashMap<>();
        openClawGuardrails.put("type", "openclaw-guardrails");
        openClawGuardrails.put("displayName", "OpenClaw Guardrails");
        openClawGuardrails.put("description", "Install MCP Endpoint Shield guardrails for OpenClaw (Clawdbot)");
        List<Map<String, String>> openClawEnvVars = new ArrayList<>();
        Map<String, String> openaiApiKey = new HashMap<>();
        openaiApiKey.put("name", "OPENAI_API_KEY"); openaiApiKey.put("label", "OpenAI API Key");
        openaiApiKey.put("placeholder", "sk-xxxxx"); openaiApiKey.put("required", "true");
        openClawEnvVars.add(openaiApiKey);
        Map<String, String> originalProvider = new HashMap<>();
        originalProvider.put("name", "ORIGINAL_PROVIDER"); originalProvider.put("label", "Original Provider");
        originalProvider.put("placeholder", "openai/gpt-4o-mini"); originalProvider.put("required", "true");
        openClawEnvVars.add(originalProvider);
        Map<String, String> modelId = new HashMap<>();
        modelId.put("name", "MODEL_ID"); modelId.put("label", "Model ID");
        modelId.put("placeholder", "gpt-4o-mini"); modelId.put("required", "true");
        openClawEnvVars.add(modelId);
        openClawGuardrails.put("envVars", openClawEnvVars);
        guardrailTypes.add(openClawGuardrails);

        Map<String, Object> claudeCliHooks = new HashMap<>();
        claudeCliHooks.put("type", "claude-cli-hooks");
        claudeCliHooks.put("displayName", "Claude CLI Hooks");
        claudeCliHooks.put("description", "Monitor and secure Claude AI CLI assistant");
        guardrailTypes.add(claudeCliHooks);

        return Action.SUCCESS.toUpperCase();
    }

    public String saveCrowdStrikeGuardrailsConfig() {
        if (guardrailType == null || guardrailType.isEmpty()) {
            addActionError("At least one guardrail type is required");
            return Action.ERROR.toUpperCase();
        }
        if (guardrailTargetMode == null || guardrailTargetMode.isEmpty()) {
            addActionError("Target mode is required (all or select)");
            return Action.ERROR.toUpperCase();
        }
        if ("select".equals(guardrailTargetMode) && (guardrailDeviceIds == null || guardrailDeviceIds.isEmpty())) {
            addActionError("At least one device must be selected for 'select' target mode");
            return Action.ERROR.toUpperCase();
        }
        return executeCrowdStrikeGuardrails();
    }

    public String executeCrowdStrikeGuardrails() {
        CrowdStrikeIntegration integration = CrowdStrikeIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("CrowdStrike integration not configured");
            return Action.ERROR.toUpperCase();
        }
        if (guardrailType == null || guardrailType.isEmpty()) {
            addActionError("Guardrail type not configured");
            return Action.ERROR.toUpperCase();
        }

        String resolvedBase = normalizeUrl(
            integration.getBaseUrl() != null && !integration.getBaseUrl().isEmpty()
                ? integration.getBaseUrl() : "https://api.crowdstrike.com"
        );

        try {
            String accessToken = fetchAccessToken(integration.getClientId(), integration.getClientSecret(), resolvedBase);

            List<String> targetDeviceIds;
            if ("all".equals(guardrailTargetMode)) {
                List<Map<String, Object>> allDevices = fetchDeviceList(accessToken, resolvedBase);
                targetDeviceIds = new ArrayList<>();
                for (Map<String, Object> d : allDevices) {
                    Object id = d.get("id");
                    if (id != null) targetDeviceIds.add(id.toString());
                }
            } else {
                targetDeviceIds = guardrailDeviceIds;
            }

            if (targetDeviceIds == null || targetDeviceIds.isEmpty()) {
                addActionError("No devices available for guardrails installation");
                return Action.ERROR.toUpperCase();
            }

            Map<String, String> envVars = guardrailEnvVars != null ? guardrailEnvVars : new HashMap<>();
            if (integration.getDataIngestionUrl() != null && !integration.getDataIngestionUrl().isEmpty()) {
                envVars.put("AKTO_DATA_INGESTION_URL", integration.getDataIngestionUrl());
            }

            int totalSuccess = 0;
            int totalFail = 0;
            uploadedGuardrailScripts.clear();

            for (String guardrailTypeItem : guardrailType) {
                String scriptBaseName = getGuardrailScriptBaseName(guardrailTypeItem);
                if (scriptBaseName == null) {
                    loggerMaker.error("Unknown guardrail type: " + guardrailTypeItem, LogDb.DASHBOARD);
                    continue;
                }
                for (String deviceId : targetDeviceIds) {
                    try {
                        boolean ok = executeGuardrailOnDevice(
                            accessToken, resolvedBase, deviceId, scriptBaseName, envVars);
                        if (ok) totalSuccess++; else totalFail++;
                    } catch (Exception e) {
                        loggerMaker.error("Failed guardrail " + guardrailTypeItem + " on device " + deviceId + ": " + e.getMessage(), LogDb.DASHBOARD);
                        totalFail++;
                    }
                }
            }

            String finalStatus = totalFail == 0 ? "completed" : "partial";
            guardrailExecution = new HashMap<>();
            guardrailExecution.put("successCount", totalSuccess);
            guardrailExecution.put("failCount", totalFail);
            guardrailExecution.put("totalCount", targetDeviceIds.size() * guardrailType.size());
            guardrailExecution.put("status", finalStatus);
            loggerMaker.info("CrowdStrike guardrails: " + totalSuccess + " success, " + totalFail + " failed", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();

        } catch (IOException e) {
            loggerMaker.error("Error executing CrowdStrike guardrails: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error executing guardrails: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private boolean executeGuardrailOnDevice(String accessToken, String baseUrl, String deviceId,
            String scriptBaseName, Map<String, String> envVars) throws IOException {
        // Upload both .sh and .ps1 (or fetch from RTR library if already uploaded)
        String shScript = uploadGuardrailScriptIfNeeded(accessToken, baseUrl, scriptBaseName + ".sh");
        String ps1Script = uploadGuardrailScriptIfNeeded(accessToken, baseUrl, scriptBaseName + ".ps1");

        // Determine device OS to pick the right script
        String platform = getDevicePlatform(accessToken, baseUrl, deviceId);
        boolean isWindows = platform != null && platform.toLowerCase().contains("windows");
        String scriptName = isWindows ? (ps1Script != null ? scriptBaseName + ".ps1" : null)
                                      : (shScript != null ? scriptBaseName + ".sh" : null);
        if (scriptName == null) {
            loggerMaker.error("Script not available for device " + deviceId + " (platform=" + platform + ")", LogDb.DASHBOARD);
            return false;
        }

        // Build input params string
        StringBuilder params = new StringBuilder();
        for (Map.Entry<String, String> e : envVars.entrySet()) {
            params.append(e.getKey()).append("=").append(e.getValue()).append(" ");
        }

        return runGuardrailScript(accessToken, baseUrl, deviceId, scriptName, params.toString().trim());
    }

    private String getDevicePlatform(String accessToken, String baseUrl, String deviceId) {
        try {
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("ids", Arrays.asList(deviceId));
            RequestConfig cfg = buildRequestConfig();
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                HttpPost post = new HttpPost(baseUrl + "/devices/entities/devices/v2");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(reqBody), ContentType.APPLICATION_JSON));
                try (CloseableHttpResponse resp = client.execute(post)) {
                    JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                    JsonNode resources = json.path("resources");
                    if (resources.isArray() && resources.size() > 0) {
                        return resources.get(0).path("platform_name").asText(null);
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.error("Could not fetch platform for device " + deviceId + ": " + e.getMessage(), LogDb.DASHBOARD);
        }
        return null;
    }

    private String uploadGuardrailScriptIfNeeded(String accessToken, String baseUrl, String scriptFileName) {
        if (uploadedGuardrailScripts.contains(scriptFileName)) return scriptFileName;

        // Check if already in RTR library
        try {
            RequestConfig cfg = buildRequestConfig();
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                HttpGet check = new HttpGet(baseUrl + "/real-time-response/queries/falcon-scripts/v1?filter=name%3A%27"
                    + encode(scriptFileName) + "%27");
                check.setHeader("Authorization", "Bearer " + accessToken);
                try (CloseableHttpResponse resp = client.execute(check)) {
                    JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                    JsonNode resources = json.path("resources");
                    if (resources.isArray() && resources.size() > 0) {
                        uploadedGuardrailScripts.add(scriptFileName);
                        return scriptFileName;
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.error("Error checking RTR library for " + scriptFileName + ": " + e.getMessage(), LogDb.DASHBOARD);
        }

        // Upload from classpath (scripts live under /sentinelone/ — same scripts, platform-agnostic content)
        String classpathResource = "/sentinelone/" + scriptFileName;
        try (InputStream scriptStream = getClass().getResourceAsStream(classpathResource)) {
            if (scriptStream == null) {
                loggerMaker.error("Guardrail script not found in classpath: " + classpathResource, LogDb.DASHBOARD);
                return null;
            }
            byte[] scriptBytes = readAllBytes(scriptStream);

            RequestConfig cfg = buildRequestConfig();
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("name", scriptFileName);
                builder.addTextBody("permission_type", "private");
                builder.addTextBody("content_type", scriptFileName.endsWith(".ps1") ? "text/x-powershell" : "text/x-shellscript");
                builder.addTextBody("description", "Akto guardrail installer");
                builder.addBinaryBody("file", scriptBytes, ContentType.APPLICATION_OCTET_STREAM, scriptFileName);

                HttpPost post = new HttpPost(baseUrl + "/real-time-response/entities/scripts/v1");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setEntity(builder.build());

                try (CloseableHttpResponse resp = client.execute(post)) {
                    int status = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity());
                    if (status == 200 || status == 201) {
                        uploadedGuardrailScripts.add(scriptFileName);
                        loggerMaker.info("Uploaded guardrail script to RTR library: " + scriptFileName, LogDb.DASHBOARD);
                        return scriptFileName;
                    }
                    loggerMaker.error("Failed to upload guardrail script " + scriptFileName + ": HTTP " + status + " " + body, LogDb.DASHBOARD);
                    return null;
                }
            }
        } catch (Exception e) {
            loggerMaker.error("Error uploading guardrail script " + scriptFileName + ": " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        }
    }

    private boolean runGuardrailScript(String accessToken, String baseUrl,
            String deviceId, String scriptName, String inputParams) throws IOException {
        String sessionId = null;
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            // Init RTR session
            Map<String, Object> sessionBody = new HashMap<>();
            sessionBody.put("device_id", deviceId);
            sessionBody.put("timeout", 600);
            HttpPost initPost = new HttpPost(baseUrl + "/real-time-response/entities/sessions/v1");
            initPost.setHeader("Authorization", "Bearer " + accessToken);
            initPost.setHeader("Content-Type", "application/json");
            initPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(sessionBody), ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse resp = client.execute(initPost)) {
                JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                sessionId = json.path("resources").path(0).path("session_id").asText(null);
                if (sessionId == null || sessionId.isEmpty()) {
                    loggerMaker.error("Could not init RTR session for device " + deviceId, LogDb.DASHBOARD);
                    return false;
                }
            }

            // Run script
            String command = "-CloudFile='" + scriptName + "'";
            if (inputParams != null && !inputParams.isEmpty()) {
                command += " -CommandLine='" + inputParams + "'";
            }
            Map<String, Object> cmdBody = new HashMap<>();
            cmdBody.put("session_id", sessionId);
            cmdBody.put("base_command", "runscript");
            cmdBody.put("command_string", "runscript " + command);
            HttpPost cmdPost = new HttpPost(baseUrl + "/real-time-response/entities/admin-command/v1");
            cmdPost.setHeader("Authorization", "Bearer " + accessToken);
            cmdPost.setHeader("Content-Type", "application/json");
            cmdPost.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(cmdBody), ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse resp = client.execute(cmdPost)) {
                int status = resp.getStatusLine().getStatusCode();
                if (status != 201 && status != 200) {
                    loggerMaker.error("RTR runscript failed for device " + deviceId + ": HTTP " + status, LogDb.DASHBOARD);
                    return false;
                }
                loggerMaker.info("Guardrail script dispatched via RTR to device " + deviceId, LogDb.DASHBOARD);
                return true;
            }
        } finally {
            if (sessionId != null) {
                try {
                    deleteRtrSession(accessToken, baseUrl, sessionId);
                } catch (Exception ignored) {}
            }
        }
    }

    private void deleteRtrSession(String accessToken, String baseUrl, String sessionId) throws IOException {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            org.apache.http.client.methods.HttpDelete del = new org.apache.http.client.methods.HttpDelete(
                baseUrl + "/real-time-response/entities/sessions/v1?session_id=" + encode(sessionId));
            del.setHeader("Authorization", "Bearer " + accessToken);
            try (CloseableHttpResponse resp = client.execute(del)) {
                EntityUtils.consumeQuietly(resp.getEntity());
            }
        }
    }

    private static String getGuardrailScriptBaseName(String type) {
        switch (type) {
            case "cursor-hooks":       return "install_cursor_hooks_sentinelone";
            case "openclaw-guardrails": return "install_openclaw_guardrails_sentinelone";
            case "claude-cli-hooks":   return "install_claude_cli_hooks_sentinelone";
            default: return null;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();
    }

    private static String normalizeUrl(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    private String encode(String value) {
        try { return java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { return value; }
    }
}
