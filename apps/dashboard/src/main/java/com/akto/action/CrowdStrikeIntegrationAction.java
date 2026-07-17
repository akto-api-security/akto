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
    private static final long GUARDRAIL_RTR_MAX_WAIT_MS = 60_000;
    private static final long GUARDRAIL_RTR_POLL_INTERVAL_MS = 2_000;

    private String clientId;
    private String clientSecret;
    private String baseUrl;
    private String dataIngestionUrl;
    private String aktoApiToken;
    private Integer recurringIntervalSeconds;

    // Guardrails fields
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
            Projections.exclude(CrowdStrikeIntegration.CLIENT_SECRET, CrowdStrikeIntegration.AKTO_API_TOKEN)
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

        // Resolve the Akto API token: use provided value or fall back to stored one
        String resolvedAktoApiToken;
        if (aktoApiToken != null && !aktoApiToken.isEmpty()) {
            resolvedAktoApiToken = aktoApiToken;
        } else {
            CrowdStrikeIntegration existingForToken = CrowdStrikeIntegrationDao.instance.findOne(new BasicDBObject());
            resolvedAktoApiToken = existingForToken != null ? existingForToken.getAktoApiToken() : null;
        }

        org.bson.conversions.Bson updates = Updates.combine(
            Updates.set(CrowdStrikeIntegration.CLIENT_ID, clientId),
            Updates.set(CrowdStrikeIntegration.CLIENT_SECRET, resolvedClientSecret),
            Updates.set(CrowdStrikeIntegration.BASE_URL, resolvedBaseUrl),
            Updates.set(CrowdStrikeIntegration.DATA_INGESTION_URL, normalizedIngestUrl),
            Updates.set(CrowdStrikeIntegration.AKTO_API_TOKEN, resolvedAktoApiToken),
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

        Map<String, Object> geminiHooks = new HashMap<>();
        geminiHooks.put("type", "gemini-hooks");
        geminiHooks.put("displayName", "Gemini CLI Hooks");
        geminiHooks.put("description", "Monitor and secure Gemini CLI assistant");
        guardrailTypes.add(geminiHooks);

        Map<String, Object> codexHooks = new HashMap<>();
        codexHooks.put("type", "codex-hooks");
        codexHooks.put("displayName", "Codex CLI Hooks");
        codexHooks.put("description", "Monitor and secure OpenAI Codex CLI assistant");
        guardrailTypes.add(codexHooks);

        Map<String, Object> githubCliHooks = new HashMap<>();
        githubCliHooks.put("type", "github-cli-hooks");
        githubCliHooks.put("displayName", "GitHub CLI Hooks");
        githubCliHooks.put("description", "Monitor and secure GitHub CLI (gh) MCP usage");
        guardrailTypes.add(githubCliHooks);

        Map<String, Object> vscodeCopilotHooks = new HashMap<>();
        vscodeCopilotHooks.put("type", "vscode-copilot-hooks");
        vscodeCopilotHooks.put("displayName", "VS Code Copilot Hooks");
        vscodeCopilotHooks.put("description", "Monitor and secure GitHub Copilot in VS Code");
        guardrailTypes.add(vscodeCopilotHooks);

        Map<String, Object> kiroCliHooks = new HashMap<>();
        kiroCliHooks.put("type", "kiro-cli-hooks");
        kiroCliHooks.put("displayName", "Kiro CLI/IDE Hooks");
        kiroCliHooks.put("description", "Monitor and secure Kiro CLI and IDE");
        guardrailTypes.add(kiroCliHooks);

        Map<String, Object> opencodeHooks = new HashMap<>();
        opencodeHooks.put("type", "opencode-hooks");
        opencodeHooks.put("displayName", "OpenCode CLI Hooks");
        opencodeHooks.put("description", "Monitor and secure OpenCode CLI assistant");
        guardrailTypes.add(opencodeHooks);

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

            List<Map<String, Object>> allDevices = fetchDeviceList(accessToken, resolvedBase);
            List<String> targetDeviceIds;
            Set<String> selectedDeviceIds = guardrailDeviceIds != null
                ? new HashSet<>(guardrailDeviceIds)
                : new HashSet<>();
            List<String> unixDeviceIds = new ArrayList<>();
            List<String> windowsDeviceIds = new ArrayList<>();
            if ("all".equals(guardrailTargetMode)) {
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
            Set<String> targetDeviceIdSet = new HashSet<>(targetDeviceIds);
            for (Map<String, Object> d : allDevices) {
                Object idObj = d.get("id");
                if (idObj == null) continue;
                String id = idObj.toString();
                if (!targetDeviceIdSet.contains(id)) continue;
                String platform = d.get("platform") != null ? d.get("platform").toString().toLowerCase() : "";
                if (platform.contains("windows")) {
                    windowsDeviceIds.add(id);
                } else {
                    unixDeviceIds.add(id);
                }
            }
            if (!"all".equals(guardrailTargetMode)) {
                for (String id : selectedDeviceIds) {
                    if (!targetDeviceIdSet.contains(id)) continue;
                    if (!unixDeviceIds.contains(id) && !windowsDeviceIds.contains(id)) {
                        unixDeviceIds.add(id);
                    }
                }
            }

            Map<String, String> envVars = guardrailEnvVars != null ? guardrailEnvVars : new HashMap<>();
            if (integration.getDataIngestionUrl() != null && !integration.getDataIngestionUrl().isEmpty()) {
                envVars.put("AKTO_DATA_INGESTION_URL", integration.getDataIngestionUrl());
            }
            if (!isBlank(integration.getAktoApiToken())) {
                envVars.put("AKTO_API_TOKEN", integration.getAktoApiToken());
            }
            if (isBlank(envVars.get("AKTO_API_TOKEN"))) {
                addActionError("Akto API Token is required to install guardrails");
                return Action.ERROR.toUpperCase();
            }
            StringBuilder paramsBuilder = new StringBuilder();
            for (Map.Entry<String, String> e : envVars.entrySet()) {
                paramsBuilder.append(e.getKey()).append("=").append(e.getValue()).append(" ");
            }
            String inputParams = paramsBuilder.toString().trim();

            uploadedGuardrailScripts.clear();

            // Upload every requested guardrail's .sh + .ps1 once, up front — batch dispatch
            // below covers all target devices per hook type in a single API call, regardless
            // of how many devices there are.
            Map<String, String> scriptBaseNames = new HashMap<>();
            for (String guardrailTypeItem : guardrailType) {
                String scriptBaseName = getGuardrailScriptBaseName(guardrailTypeItem);
                if (scriptBaseName == null) {
                    loggerMaker.error("Unknown guardrail type: " + guardrailTypeItem, LogDb.DASHBOARD);
                    continue;
                }
                scriptBaseNames.put(guardrailTypeItem, scriptBaseName);
                uploadGuardrailScriptIfNeeded(accessToken, resolvedBase, scriptBaseName + ".sh");
                uploadGuardrailScriptIfNeeded(accessToken, resolvedBase, scriptBaseName + ".ps1");
            }

            // One batch session covers all target devices — queue_offline=true so devices
            // that are offline right now still pick up the command once they reconnect.
            String batchId = batchInitSessions(accessToken, resolvedBase, targetDeviceIds);
            if (batchId == null) {
                addActionError("Could not initialize RTR batch session");
                return Action.ERROR.toUpperCase();
            }

            int totalSuccess = 0;
            int totalFail = 0;

            for (Map.Entry<String, String> entry : scriptBaseNames.entrySet()) {
                String scriptBaseName = entry.getValue();
                Map<String, Boolean> shResults = unixDeviceIds.isEmpty()
                    ? new HashMap<>()
                    : batchAdminCommand(accessToken, resolvedBase, batchId, unixDeviceIds,
                        scriptBaseName + ".sh", inputParams);
                Map<String, Boolean> ps1Results = windowsDeviceIds.isEmpty()
                    ? new HashMap<>()
                    : batchAdminCommand(accessToken, resolvedBase, batchId, windowsDeviceIds,
                        scriptBaseName + ".ps1", inputParams);

                for (String deviceId : unixDeviceIds) {
                    if (Boolean.TRUE.equals(shResults.get(deviceId))) totalSuccess++; else totalFail++;
                }
                for (String deviceId : windowsDeviceIds) {
                    if (Boolean.TRUE.equals(ps1Results.get(deviceId))) totalSuccess++; else totalFail++;
                }
            }

            String finalStatus = totalFail == 0 ? "completed" : "partial";
            guardrailExecution = new HashMap<>();
            guardrailExecution.put("successCount", totalSuccess);
            guardrailExecution.put("failCount", totalFail);
            guardrailExecution.put("totalCount", targetDeviceIds.size() * scriptBaseNames.size());
            guardrailExecution.put("status", finalStatus);
            loggerMaker.info("CrowdStrike guardrails: " + totalSuccess + " success, " + totalFail + " failed", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();

        } catch (IOException e) {
            loggerMaker.error("Error executing CrowdStrike guardrails: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error executing guardrails: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Initializes one RTR batch session covering every target device. queue_offline=true
     * means devices that are offline right now still pick up the dispatched command once
     * they reconnect, instead of silently being skipped.
     */
    private String batchInitSessions(String accessToken, String baseUrl, List<String> deviceIds) throws IOException {
        RequestConfig cfg = buildRequestConfig();
        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            Map<String, Object> body = new HashMap<>();
            body.put("host_ids", deviceIds);
            body.put("queue_offline", true);

            HttpPost post = new HttpPost(baseUrl + "/real-time-response/combined/batch-init-session/v1");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                String respBody = EntityUtils.toString(resp.getEntity());
                JsonNode json = OBJECT_MAPPER.readTree(respBody);
                String batchId = json.path("batch_id").asText(null);
                if (batchId == null || batchId.isEmpty()) {
                    loggerMaker.error("BatchInitSessions returned no batch_id: " + respBody, LogDb.DASHBOARD);
                    return null;
                }
                return batchId;
            }
        }
    }

    /**
     * Dispatches a runscript command for the given script to every device in the batch,
     * in one API call — this is what lets a fleet-wide guardrail install stay inside a
     * single synchronous dashboard request instead of one RTR session per device.
     * Returns a map of deviceId -> whether that device's command completed without error.
     * Devices that are still incomplete in the initial response are polled via
     * BatchGetCmdStatus until done or the timeout elapses.
     */
    private Map<String, Boolean> batchAdminCommand(String accessToken, String baseUrl, String batchId,
            List<String> optionalHostIds,
            String scriptName, String inputParams) throws IOException {
        Map<String, Boolean> results = new HashMap<>();
        RequestConfig cfg = buildRequestConfig();

        // CrowdStrike's RTR command parser rejects single-quoted args — use double quotes.
        String command = "-CloudFile=\"" + scriptName + "\"";
        String commandLine = buildGuardrailCommandLine(scriptName, inputParams);
        if (commandLine != null && !commandLine.isEmpty()) {
            command += " -CommandLine=\"" + commandLine + "\"";
        }

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            Map<String, Object> body = new HashMap<>();
            body.put("batch_id", batchId);
            body.put("base_command", "runscript");
            body.put("command_string", "runscript " + command);
            body.put("persist_all", true);
            if (optionalHostIds != null && !optionalHostIds.isEmpty()) {
                body.put("optional_hosts", optionalHostIds);
            }

            HttpPost post = new HttpPost(baseUrl + "/real-time-response/combined/batch-admin-command/v1");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse resp = client.execute(post)) {
                String respBody = EntityUtils.toString(resp.getEntity());
                JsonNode json = OBJECT_MAPPER.readTree(respBody);

                java.util.Iterator<Map.Entry<String, JsonNode>> fields = json.path("combined").path("resources").fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String deviceId = entry.getKey();
                    JsonNode deviceResult = entry.getValue();
                    JsonNode errors = deviceResult.path("errors");
                    boolean hasErrors = errors.isArray() && errors.size() > 0;
                    boolean complete = deviceResult.path("complete").asBoolean(false);
                    if (hasErrors) {
                        loggerMaker.error("Guardrail " + scriptName + " on device " + deviceId
                            + " error: " + errors.toString(), LogDb.DASHBOARD);
                        results.put(deviceId, false);
                    } else if (complete) {
                        String stderr = deviceResult.path("stderr").asText("");
                        results.put(deviceId, stderr.isEmpty());
                        if (!stderr.isEmpty()) {
                            loggerMaker.error("Guardrail " + scriptName + " on device " + deviceId + " stderr=" + stderr, LogDb.DASHBOARD);
                        }
                    } else {
                        // Not finished yet — poll for this specific device below.
                        results.put(deviceId, null);
                    }
                }
            }
        }

        // Poll any devices still pending (e.g. queued offline, or slow to complete).
        List<String> pendingDeviceIds = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : results.entrySet()) {
            if (e.getValue() == null) pendingDeviceIds.add(e.getKey());
        }
        if (!pendingDeviceIds.isEmpty()) {
            Map<String, Boolean> polled = batchPollPendingDevices(accessToken, baseUrl, batchId, pendingDeviceIds, command);
            results.putAll(polled);
        }

        return results;
    }

    private String buildGuardrailCommandLine(String scriptName, String inputParams) {
        if (inputParams == null || inputParams.isEmpty()) return "";
        if (!scriptName.endsWith(".ps1")) return inputParams;

        Map<String, String> params = new HashMap<>();
        for (String part : inputParams.split("\\s+")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            params.put(part.substring(0, idx), part.substring(idx + 1));
        }

        StringBuilder psParams = new StringBuilder();
        appendPowerShellParam(psParams, "TargetUserHome", params.get("TARGET_USER_HOME"));
        appendPowerShellParam(psParams, "AktoDataIngestionUrl", params.get("AKTO_DATA_INGESTION_URL"));
        appendPowerShellParam(psParams, "AktoApiToken", params.get("AKTO_API_TOKEN"));
        return psParams.toString().trim();
    }

    private void appendPowerShellParam(StringBuilder builder, String name, String value) {
        if (value == null || value.isEmpty()) return;
        if (builder.length() > 0) builder.append(" ");
        builder.append("-").append(name).append(" ").append(escapePowerShellArg(value));
    }

    private String escapePowerShellArg(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * For devices whose batch-admin-command result wasn't complete inline (e.g. offline at
     * dispatch time), re-issues the batch command scoped to just those devices and polls
     * until each completes or the overall timeout elapses. CrowdStrike's BatchAdminCmd is
     * idempotent per-device for the same command — it returns the existing task's result
     * once complete rather than re-running.
     */
    private Map<String, Boolean> batchPollPendingDevices(String accessToken, String baseUrl, String batchId,
            List<String> pendingDeviceIds, String command) throws IOException {
        Map<String, Boolean> results = new HashMap<>();
        for (String id : pendingDeviceIds) results.put(id, false);

        long deadline = System.currentTimeMillis() + GUARDRAIL_RTR_MAX_WAIT_MS;
        RequestConfig cfg = buildRequestConfig();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
            while (System.currentTimeMillis() < deadline && !pendingDeviceIds.isEmpty()) {
                try { Thread.sleep(GUARDRAIL_RTR_POLL_INTERVAL_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }

                Map<String, Object> body = new HashMap<>();
                body.put("batch_id", batchId);
                body.put("base_command", "runscript");
                body.put("optional_hosts", pendingDeviceIds);
                body.put("command_string", "runscript " + command);
                body.put("persist_all", true);

                HttpPost post = new HttpPost(baseUrl + "/real-time-response/combined/batch-admin-command/v1");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse resp = client.execute(post)) {
                    JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                    java.util.Iterator<Map.Entry<String, JsonNode>> fields = json.path("combined").path("resources").fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        JsonNode deviceResult = entry.getValue();
                        if (deviceResult.path("complete").asBoolean(false)) {
                            String stderr = deviceResult.path("stderr").asText("");
                            results.put(entry.getKey(), stderr.isEmpty());
                            pendingDeviceIds.remove(entry.getKey());
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.error("Batch poll error: " + e.getMessage(), LogDb.DASHBOARD);
                    break;
                }
            }
        }

        for (String stillPending : pendingDeviceIds) {
            loggerMaker.error("Guardrail command timed out on device " + stillPending, LogDb.DASHBOARD);
        }
        return results;
    }

    private String uploadGuardrailScriptIfNeeded(String accessToken, String baseUrl, String scriptFileName) {
        if (uploadedGuardrailScripts.contains(scriptFileName)) return scriptFileName;

        // Check if already in RTR library.
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
                HttpPost post = new HttpPost(baseUrl + "/real-time-response/entities/scripts/v1");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setEntity(buildGuardrailScriptUploadEntity(scriptFileName, scriptBytes));

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

    private org.apache.http.HttpEntity buildGuardrailScriptUploadEntity(String scriptFileName, byte[] scriptBytes) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("name", scriptFileName);
        builder.addTextBody("permission_type", "private");
        builder.addTextBody("content_type", scriptFileName.endsWith(".ps1") ? "text/x-powershell" : "text/x-shellscript");
        builder.addTextBody("description", "Akto guardrail installer");
        if (scriptFileName.endsWith(".ps1")) {
            builder.addTextBody("platform", "windows");
        } else {
            builder.addTextBody("platform", "linux");
            builder.addTextBody("platform", "mac");
        }
        builder.addBinaryBody("file", scriptBytes, ContentType.APPLICATION_OCTET_STREAM, scriptFileName);
        return builder.build();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String getGuardrailScriptBaseName(String type) {
        switch (type) {
            case "cursor-hooks":         return "install_cursor_hooks_sentinelone";
            case "openclaw-guardrails":  return "install_openclaw_guardrails_sentinelone";
            case "claude-cli-hooks":     return "install_claude_cli_hooks_sentinelone";
            case "gemini-hooks":         return "install_gemini_hooks";
            case "codex-hooks":         return "install_codex_hooks";
            case "github-cli-hooks":    return "install_github_cli_hooks";
            case "vscode-copilot-hooks": return "install_vscode_copilot_hooks";
            case "kiro-cli-hooks":      return "install_kiro_cli_hooks";
            case "opencode-hooks":      return "install_opencode_hooks";
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
