package com.akto.action;

import com.akto.dao.SentinelOneIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.dto.sentinelone_integration.SentinelOneIntegration;
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
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SentinelOneIntegrationAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SentinelOneIntegrationAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String JOB_TYPE = "SENTINELONE_AH";
    private static final String JOB_SUB_TYPE = "AGENT_APPLICATIONS";
    private static final int DEFAULT_INTERVAL = 3600;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS = 60_000;


    private String apiToken;
    private String consoleUrl;
    private String dataIngestionUrl;
    private String guardrailsUrl;
    private Integer recurringIntervalSeconds;

    // Remote scripts fields
    private String scriptName;
    private String scriptContent;
    private String scriptId;
    private List<String> executeAgentIds;  // agent IDs to run the script on
    private String executeTaskDescription;
    private String executeInputParams;
    private List<Map<String, Object>> remoteScripts;
    private String parentTaskId;

    private SentinelOneIntegration sentinelOneIntegration;
    private List<Map<String, Object>> agents;

    // Guardrails fields
    private List<String> guardrailType;  // Support multiple guardrail types
    private Map<String, String> guardrailEnvVars;
    private String guardrailTargetMode;
    private List<String> guardrailAgentIds;
    private List<Map<String, Object>> guardrailTypes;
    private Map<String, Object> guardrailExecution;

    public String fetchSentinelOneIntegration() {
        sentinelOneIntegration = SentinelOneIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(SentinelOneIntegration.API_TOKEN)
        );
        return Action.SUCCESS.toUpperCase();
    }

    public String addSentinelOneIntegration() {
        if (consoleUrl == null || consoleUrl.isEmpty()) {
            addActionError("Please enter a valid SentinelOne console URL.");
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

        String normalizedConsoleUrl = normalizeUrl(consoleUrl);

        org.bson.conversions.Bson updates = Updates.combine(
            Updates.set(SentinelOneIntegration.CONSOLE_URL, normalizedConsoleUrl),
            Updates.set(SentinelOneIntegration.DATA_INGESTION_URL, dataIngestionUrl),
            Updates.setOnInsert(SentinelOneIntegration.CREATED_TS, now),
            Updates.set(SentinelOneIntegration.UPDATED_TS, now)
        );

        String resolvedApiToken;
        if (apiToken != null && !apiToken.isEmpty()) {
            resolvedApiToken = apiToken;
            updates = Updates.combine(updates, Updates.set(SentinelOneIntegration.API_TOKEN, apiToken));
        } else {
            SentinelOneIntegration existing = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
            if (existing == null || existing.getApiToken() == null || existing.getApiToken().isEmpty()) {
                addActionError("Please enter a valid API token.");
                return Action.ERROR.toUpperCase();
            }
            resolvedApiToken = existing.getApiToken();
        }

        SentinelOneIntegrationDao.instance.updateOne(new BasicDBObject(), updates);

        AccountJob existingJob = AccountJobDao.instance.findOne(
            Filters.and(
                Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
            )
        );

        if (existingJob == null) {
            Map<String, Object> jobConfig = new HashMap<>();
            jobConfig.put(SentinelOneIntegration.API_TOKEN, resolvedApiToken);
            jobConfig.put(SentinelOneIntegration.CONSOLE_URL, normalizedConsoleUrl);
            jobConfig.put(SentinelOneIntegration.DATA_INGESTION_URL, dataIngestionUrl);

            AccountJob accountJob = new AccountJob(
                Context.accountId.get(),
                JOB_TYPE,
                JOB_SUB_TYPE,
                jobConfig,
                interval,
                now,
                now
            );
            accountJob.setJobStatus(JobStatus.SCHEDULED);
            accountJob.setScheduleType(ScheduleType.RECURRING);
            accountJob.setScheduledAt(now);
            accountJob.setHeartbeatAt(0);
            accountJob.setStartedAt(0);
            accountJob.setFinishedAt(0);

            AccountJobDao.instance.insertOne(accountJob);
            loggerMaker.info("Created SentinelOne account job", LogDb.DASHBOARD);
        } else {
            org.bson.conversions.Bson jobUpdates = Updates.combine(
                Updates.set("config." + SentinelOneIntegration.API_TOKEN, resolvedApiToken),
                Updates.set("config." + SentinelOneIntegration.CONSOLE_URL, normalizedConsoleUrl),
                Updates.set("config." + SentinelOneIntegration.DATA_INGESTION_URL, dataIngestionUrl),
                Updates.set(AccountJob.RECURRING_INTERVAL_SECONDS, interval),
                Updates.set(AccountJob.LAST_UPDATED_AT, now),
                Updates.set(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name()),
                Updates.set(AccountJob.SCHEDULED_AT, now)
            );
            AccountJobDao.instance.updateOneNoUpsert(
                Filters.eq(AccountJob.ID, existingJob.getId()),
                jobUpdates
            );
            loggerMaker.info("Updated SentinelOne account job", LogDb.DASHBOARD);
        }

        loggerMaker.infoAndAddToDb("SentinelOne integration saved successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeSentinelOneIntegration() {
        SentinelOneIntegrationDao.instance.deleteAll(new BasicDBObject());

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

        loggerMaker.infoAndAddToDb("SentinelOne integration removed successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Returns all agents with id, computerName, osName, lastActiveDate.
     */
    public String fetchSentinelOneAgents() {
        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());

        try {
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpGet get = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/agents?limit=1000");
                get.setHeader("Authorization", "ApiToken " + integration.getApiToken());
                get.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode != 200) {
                        loggerMaker.error("Failed to fetch SentinelOne agents: HTTP " + statusCode, LogDb.DASHBOARD);
                        addActionError("Failed to fetch agents: HTTP " + statusCode);
                        return Action.ERROR.toUpperCase();
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    JsonNode dataNode = json.path("data");
                    agents = new ArrayList<>();
                    if (dataNode.isArray()) {
                        for (JsonNode agentNode : dataNode) {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("id", agentNode.path("id").asText(""));
                            entry.put("computerName", agentNode.path("computerName").asText(""));
                            entry.put("osName", agentNode.path("osName").asText(""));
                            entry.put("lastActiveDate", agentNode.path("lastActiveDate").asText(""));
                            entry.put("siteId", agentNode.path("siteId").asText(""));
                            agents.add(entry);
                        }
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error fetching SentinelOne agents: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error fetching agents: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    /** Returns the siteId of the first agent — agents are typically all in one site. */
    private String fetchSiteIdFromAgents(String token, String consoleUrl) {
        try {
            RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS).setSocketTimeout(SOCKET_TIMEOUT_MS).build();
            try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(cfg).build()) {
                HttpGet get = new HttpGet(consoleUrl + "/web/api/v2.1/agents?limit=1");
                get.setHeader("Authorization", "ApiToken " + token);
                try (CloseableHttpResponse resp = client.execute(get)) {
                    JsonNode json = OBJECT_MAPPER.readTree(EntityUtils.toString(resp.getEntity()));
                    JsonNode first = json.path("data");
                    if (first.isArray() && first.size() > 0) {
                        String siteId = first.get(0).path("siteId").asText(null);
                        if (siteId != null && !siteId.isEmpty()) return siteId;
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Could not fetch siteId from agents: " + e.getMessage(), LogDb.DASHBOARD);
        }
        return null;
    }

    /** Execute a remote script on selected agents. */
    public String executeSentinelOneRemoteScript() {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            addActionError("scriptId is required.");
            return Action.ERROR.toUpperCase();
        }
        if (executeAgentIds == null || executeAgentIds.isEmpty()) {
            addActionError("At least one agent ID is required.");
            return Action.ERROR.toUpperCase();
        }
        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }
        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());

        Map<String, Object> data = new HashMap<>();
        data.put("scriptId", scriptId.trim());
        data.put("outputDestination", "SentinelCloud");
        data.put("taskDescription", executeTaskDescription != null ? executeTaskDescription : "Akto script execution");
        if (executeInputParams != null && !executeInputParams.trim().isEmpty()) {
            data.put("inputParams", executeInputParams.trim());
        }

        Map<String, Object> filter = new HashMap<>();
        filter.put("ids", executeAgentIds);

        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        body.put("filter", filter);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS).setSocketTimeout(SOCKET_TIMEOUT_MS).build();
        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost post = new HttpPost(normalizedConsoleUrl + "/web/api/v2.1/remote-scripts/execute");
            post.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200 && statusCode != 201) {
                    loggerMaker.error("Execute remote script failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    addActionError("Execute failed: " + responseBody);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                parentTaskId = json.path("data").path("parentTaskId").asText(null);
            }
            return Action.SUCCESS.toUpperCase();
        } catch (IOException e) {
            loggerMaker.error("Error executing remote script: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error executing remote script: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /** Poll the status of a remote script task by parentTaskId. */
    public String getSentinelOneScriptTaskStatus() {
        if (parentTaskId == null || parentTaskId.trim().isEmpty()) {
            addActionError("parentTaskId is required.");
            return Action.ERROR.toUpperCase();
        }
        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }
        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS).setSocketTimeout(SOCKET_TIMEOUT_MS).build();
        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet get = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/remote-scripts/status?parenttaskid=" + parentTaskId);
            get.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    loggerMaker.error("Failed to get task status: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    addActionError("Failed to get task status: HTTP " + statusCode + " - " + responseBody);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                remoteScripts = new ArrayList<>();
                JsonNode data = json.path("data");
                if (data.isArray()) {
                    for (JsonNode task : data) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("agentComputerName", task.path("agentComputerName").asText(""));
                        entry.put("status", task.path("status").asText(""));
                        entry.put("detailedStatus", task.path("detailedStatus").asText(""));
                        entry.put("updatedAt", task.path("updatedAt").asText(""));
                        remoteScripts.add(entry);
                    }
                }
            }
            return Action.SUCCESS.toUpperCase();
        } catch (IOException e) {
            loggerMaker.error("Error getting script task status: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error getting script task status: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Guardrails Management
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns available guardrail types with their required environment variables
     */
    public String fetchGuardrailTypes() {
        guardrailTypes = new ArrayList<>();
        
        // Cursor Hooks guardrail
        Map<String, Object> cursorHooks = new HashMap<>();
        cursorHooks.put("type", "cursor-hooks");
        cursorHooks.put("displayName", "Cursor IDE Hooks");
        cursorHooks.put("description", "Install Akto guardrails hooks for Cursor IDE");
        
        guardrailTypes.add(cursorHooks);
        
        // OpenClaw guardrail
        Map<String, Object> openClawGuardrails = new HashMap<>();
        openClawGuardrails.put("type", "openclaw-guardrails");
        openClawGuardrails.put("displayName", "OpenClaw Guardrails");
        openClawGuardrails.put("description", "Install MCP Endpoint Shield guardrails for OpenClaw (Clawdbot)");
        
        List<Map<String, String>> openClawEnvVars = new ArrayList<>();
        
        Map<String, String> openaiApiKey = new HashMap<>();
        openaiApiKey.put("name", "OPENAI_API_KEY");
        openaiApiKey.put("label", "OpenAI API Key");
        openaiApiKey.put("placeholder", "sk-xxxxx");
        openaiApiKey.put("required", "true");
        openClawEnvVars.add(openaiApiKey);
        
        Map<String, String> originalProvider = new HashMap<>();
        originalProvider.put("name", "ORIGINAL_PROVIDER");
        originalProvider.put("label", "Original Provider");
        originalProvider.put("placeholder", "openai/gpt-4o-mini");
        originalProvider.put("required", "true");
        openClawEnvVars.add(originalProvider);
        
        Map<String, String> modelId = new HashMap<>();
        modelId.put("name", "MODEL_ID");
        modelId.put("label", "Model ID");
        modelId.put("placeholder", "gpt-4o-mini");
        modelId.put("required", "true");
        openClawEnvVars.add(modelId);
        
        openClawGuardrails.put("envVars", openClawEnvVars);
        guardrailTypes.add(openClawGuardrails);
        
        // Claude CLI Hooks guardrail
        Map<String, Object> claudeCliHooks = new HashMap<>();
        claudeCliHooks.put("type", "claude-cli-hooks");
        claudeCliHooks.put("displayName", "Claude CLI Hooks");
        claudeCliHooks.put("description", "Monitor and secure Claude AI CLI assistant");
        
        guardrailTypes.add(claudeCliHooks);
        
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Saves guardrails configuration and executes multiple guardrail types
     */
    public String saveGuardrailsConfig() {
        if (guardrailType == null || guardrailType.isEmpty()) {
            addActionError("At least one guardrail type is required");
            return Action.ERROR.toUpperCase();
        }

        if (guardrailTargetMode == null || guardrailTargetMode.isEmpty()) {
            addActionError("Target mode is required (all or select)");
            return Action.ERROR.toUpperCase();
        }

        if ("select".equals(guardrailTargetMode) && (guardrailAgentIds == null || guardrailAgentIds.isEmpty())) {
            addActionError("At least one agent must be selected for 'select' target mode");
            return Action.ERROR.toUpperCase();
        }

        int now = Context.now();
        
        // Save all selected guardrail types
        org.bson.conversions.Bson updates = Updates.combine(
            Updates.set(SentinelOneIntegration.GUARDRAIL_TYPE, guardrailType),  // Now saves array
            Updates.set(SentinelOneIntegration.GUARDRAIL_ENV_VARS, guardrailEnvVars),
            Updates.set(SentinelOneIntegration.GUARDRAIL_TARGET_MODE, guardrailTargetMode),
            Updates.set(SentinelOneIntegration.GUARDRAIL_AGENT_IDS, guardrailAgentIds),
            Updates.set(SentinelOneIntegration.GUARDRAIL_STATUS, "pending"),
            Updates.set(SentinelOneIntegration.UPDATED_TS, now)
        );

        SentinelOneIntegrationDao.instance.updateOne(new BasicDBObject(), updates);
        
        loggerMaker.info("Guardrails configuration saved: " + String.join(", ", guardrailType), LogDb.DASHBOARD);
        
        // Automatically trigger execution for all types
        return executeGuardrails();
    }

    /**
     * Executes guardrails installation on selected agents
     */
    public String executeGuardrails() {
        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured");
            return Action.ERROR.toUpperCase();
        }

        List<String> guardrailTypesList = integration.getGuardrailType();
        if (guardrailTypesList == null || guardrailTypesList.isEmpty()) {
            addActionError("Guardrail type not configured");
            return Action.ERROR.toUpperCase();
        }

        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());
        
        try {
            // Update status to running
            SentinelOneIntegrationDao.instance.updateOne(
                new BasicDBObject(),
                Updates.set(SentinelOneIntegration.GUARDRAIL_STATUS, "running")
            );

            // Get target agents
            List<String> targetAgentIds;
            if ("all".equals(integration.getGuardrailTargetMode())) {
                // Fetch all agents
                List<Map<String, Object>> allAgents = fetchAllAgents(integration.getApiToken(), normalizedConsoleUrl);
                targetAgentIds = new ArrayList<>();
                for (Map<String, Object> agent : allAgents) {
                    targetAgentIds.add((String) agent.get("id"));
                }
            } else {
                targetAgentIds = integration.getGuardrailAgentIds();
            }

            if (targetAgentIds == null || targetAgentIds.isEmpty()) {
                addActionError("No agents available for guardrails installation");
                SentinelOneIntegrationDao.instance.updateOne(
                    new BasicDBObject(),
                    Updates.set(SentinelOneIntegration.GUARDRAIL_STATUS, "failed")
                );
                return Action.ERROR.toUpperCase();
            }

            int totalSuccessCount = 0;
            int totalFailCount = 0;
            
            // Build complete env vars map including common dataIngestionUrl
            Map<String, String> completeEnvVars = new HashMap<>();
            if (integration.getGuardrailEnvVars() != null) {
                completeEnvVars.putAll(integration.getGuardrailEnvVars());
            }
            // Add common AKTO_DATA_INGESTION_URL for all guardrails
            if (integration.getDataIngestionUrl() != null && !integration.getDataIngestionUrl().isEmpty()) {
                completeEnvVars.put("AKTO_DATA_INGESTION_URL", integration.getDataIngestionUrl());
            }
            
            // Execute each guardrail type
            for (String guardrailTypeItem : guardrailTypesList) {
                loggerMaker.info("Executing guardrail: " + guardrailTypeItem, LogDb.DASHBOARD);
                
                // Determine script based on guardrail type
                String scriptPath = getScriptPath(guardrailTypeItem);
                if (scriptPath == null) {
                    loggerMaker.error("Unknown guardrail type: " + guardrailTypeItem, LogDb.DASHBOARD);
                    continue;
                }
                
                // Execute script on each agent
                int successCount = 0;
                int failCount = 0;
                
                for (String agentId : targetAgentIds) {
                    try {
                        // Get agent details to determine OS
                        Map<String, Object> agentDetails = getAgentDetails(agentId, integration.getApiToken(), normalizedConsoleUrl);
                        String osName = (String) agentDetails.get("osName");
                        
                        // Execute script based on OS
                        boolean success = executeGuardrailScript(
                            agentId,
                            osName,
                            scriptPath,
                            completeEnvVars,
                            integration.getApiToken(),
                            normalizedConsoleUrl
                        );
                        
                        if (success) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } catch (Exception e) {
                        loggerMaker.error("Failed to execute guardrail " + guardrailTypeItem + " on agent " + agentId + ": " + e.getMessage(), LogDb.DASHBOARD);
                        failCount++;
                    }
                }
                
                loggerMaker.info("Guardrail " + guardrailTypeItem + " execution: " + successCount + " success, " + failCount + " failed", LogDb.DASHBOARD);
                totalSuccessCount += successCount;
                totalFailCount += failCount;
            }

            // Update status
            String finalStatus = (totalFailCount == 0) ? "completed" : "partial";
            int now = Context.now();
            
            SentinelOneIntegrationDao.instance.updateOne(
                new BasicDBObject(),
                Updates.combine(
                    Updates.set(SentinelOneIntegration.GUARDRAIL_STATUS, finalStatus),
                    Updates.set(SentinelOneIntegration.GUARDRAIL_LAST_EXECUTION_TS, now)
                )
            );

            int totalExecutions = targetAgentIds.size() * guardrailTypesList.size();
            guardrailExecution = new HashMap<>();
            guardrailExecution.put("successCount", totalSuccessCount);
            guardrailExecution.put("failCount", totalFailCount);
            guardrailExecution.put("totalCount", totalExecutions);
            guardrailExecution.put("status", finalStatus);
            
            loggerMaker.info("All guardrails execution completed: " + totalSuccessCount + " success, " + totalFailCount + " failed out of " + totalExecutions + " total", LogDb.DASHBOARD);
            
            return Action.SUCCESS.toUpperCase();
            
        } catch (IOException e) {
            loggerMaker.error("Error executing guardrails: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error executing guardrails: " + e.getMessage());
            
            SentinelOneIntegrationDao.instance.updateOne(
                new BasicDBObject(),
                Updates.set(SentinelOneIntegration.GUARDRAIL_STATUS, "failed")
            );
            
            return Action.ERROR.toUpperCase();
        }
    }

    // ── Helper methods for guardrails ─────────────────────────────────────────

    private List<Map<String, Object>> fetchAllAgents(String token, String consoleUrl) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet get = new HttpGet(consoleUrl + "/web/api/v2.1/agents?limit=1000");
            get.setHeader("Authorization", "ApiToken " + token);
            get.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    throw new IOException("Failed to fetch agents: HTTP " + statusCode);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode dataNode = json.path("data");
                List<Map<String, Object>> agents = new ArrayList<>();
                
                if (dataNode.isArray()) {
                    for (JsonNode agentNode : dataNode) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("id", agentNode.path("id").asText(""));
                        entry.put("computerName", agentNode.path("computerName").asText(""));
                        entry.put("osName", agentNode.path("osName").asText(""));
                        agents.add(entry);
                    }
                }
                
                return agents;
            }
        }
    }

    private Map<String, Object> getAgentDetails(String agentId, String token, String consoleUrl) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet get = new HttpGet(consoleUrl + "/web/api/v2.1/agents?ids=" + agentId);
            get.setHeader("Authorization", "ApiToken " + token);
            get.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    throw new IOException("Failed to fetch agent details: HTTP " + statusCode);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode dataNode = json.path("data");
                
                if (dataNode.isArray() && dataNode.size() > 0) {
                    JsonNode agentNode = dataNode.get(0);
                    Map<String, Object> agent = new HashMap<>();
                    agent.put("id", agentNode.path("id").asText(""));
                    agent.put("computerName", agentNode.path("computerName").asText(""));
                    agent.put("osName", agentNode.path("osName").asText(""));
                    agent.put("osType", agentNode.path("osType").asText(""));
                    return agent;
                }
                
                throw new IOException("Agent not found: " + agentId);
            }
        }
    }

    private String getScriptPath(String guardrailType) {
        // Return relative path from dashboard/scripts/guardrails/
        switch (guardrailType) {
            case "cursor-hooks":
                return "install_cursor_hooks_sentinelone";
            case "openclaw-guardrails":
                return "install_openclaw_guardrails_sentinelone";
            case "claude-cli-hooks":
                return "install_claude_cli_hooks_sentinelone";
            default:
                return null;
        }
    }

    private boolean executeGuardrailScript(String agentId, String osName, String scriptBasePath,
                                           Map<String, String> envVars, String token, String consoleUrl) throws IOException {
        
        // Determine script extension based on OS
        String scriptExt;
        if (osName != null && osName.toLowerCase().contains("windows")) {
            scriptExt = ".ps1";
        } else {
            scriptExt = ".sh";
        }
        
        String fullScriptPath = "apps/dashboard/scripts/guardrails/" + scriptBasePath + scriptExt;
        
        // Read script content from file
        String scriptContent;
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(fullScriptPath);
            scriptContent = new String(java.nio.file.Files.readAllBytes(path));
        } catch (Exception e) {
            loggerMaker.error("Failed to read script file: " + fullScriptPath + " - " + e.getMessage(), LogDb.DASHBOARD);
            return false;
        }

        // Upload script
        String uploadedScriptId = uploadScriptForGuardrails(scriptBasePath + scriptExt, scriptContent, token, consoleUrl);
        if (uploadedScriptId == null) {
            return false;
        }

        // Build input params from env vars
        StringBuilder inputParams = new StringBuilder();
        if (envVars != null) {
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                inputParams.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
        }

        // Execute script
        return executeScriptOnAgent(uploadedScriptId, agentId, inputParams.toString().trim(), token, consoleUrl);
    }

    private String uploadScriptForGuardrails(String scriptName, String scriptContent, String token, String consoleUrl) {
        try {
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                String siteId = fetchSiteIdFromAgents(token, consoleUrl);
                if (siteId == null || siteId.isEmpty()) {
                    loggerMaker.error("Could not determine site ID for guardrails", LogDb.DASHBOARD);
                    return null;
                }

                String osTypesValue;
                if (scriptName.endsWith(".ps1")) {
                    osTypesValue = "windows";
                } else if (scriptName.endsWith(".py")) {
                    osTypesValue = "linux,macos,windows";
                } else {
                    osTypesValue = "linux,macos";
                }

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("scriptName", scriptName);
                builder.addTextBody("scriptType", "action");
                builder.addTextBody("scopeLevel", "site");
                builder.addTextBody("scopeId", siteId);
                builder.addTextBody("inputRequired", "false");
                builder.addTextBody("osTypes", osTypesValue);
                builder.addBinaryBody("file",
                    scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    ContentType.TEXT_PLAIN, scriptName);

                HttpPost post = new HttpPost(consoleUrl + "/web/api/v2.1/remote-scripts");
                post.setHeader("Authorization", "ApiToken " + token);
                post.setEntity(builder.build());

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());
                    
                    if (statusCode != 200 && statusCode != 201) {
                        loggerMaker.error("Upload guardrail script failed: HTTP " + statusCode, LogDb.DASHBOARD);
                        return null;
                    }
                    
                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    return json.path("data").path("id").asText(null);
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error uploading guardrail script: " + e.getMessage(), LogDb.DASHBOARD);
            return null;
        }
    }

    private boolean executeScriptOnAgent(String scriptId, String agentId, String inputParams, String token, String consoleUrl) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("scriptId", scriptId);
            data.put("outputDestination", "SentinelCloud");
            data.put("taskDescription", "Akto guardrails installation");
            if (inputParams != null && !inputParams.isEmpty()) {
                data.put("inputParams", inputParams);
            }

            Map<String, Object> filter = new HashMap<>();
            filter.put("ids", java.util.Arrays.asList(agentId));

            Map<String, Object> body = new HashMap<>();
            body.put("data", data);
            body.put("filter", filter);

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpPost post = new HttpPost(consoleUrl + "/web/api/v2.1/remote-scripts/execute");
                post.setHeader("Authorization", "ApiToken " + token);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    return statusCode == 200 || statusCode == 201;
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error executing script on agent: " + e.getMessage(), LogDb.DASHBOARD);
            return false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════════════════════════════

    private String normalizeUrl(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    // Getters and Setters
    // ══════════════════════════════════════════════════════════════════════════

    public SentinelOneIntegration getSentinelOneIntegration() {
        return sentinelOneIntegration;
    }

    public void setSentinelOneIntegration(SentinelOneIntegration sentinelOneIntegration) {
        this.sentinelOneIntegration = sentinelOneIntegration;
    }

    public List<Map<String, Object>> getAgents() {
        return agents;
    }

    public void setAgents(List<Map<String, Object>> agents) {
        this.agents = agents;
    }

    public List<Map<String, Object>> getGuardrailTypes() {
        return guardrailTypes;
    }

    public void setGuardrailTypes(List<Map<String, Object>> guardrailTypes) {
        this.guardrailTypes = guardrailTypes;
    }

    public Map<String, Object> getGuardrailExecution() {
        return guardrailExecution;
    }

    public void setGuardrailExecution(Map<String, Object> guardrailExecution) {
        this.guardrailExecution = guardrailExecution;
    }

    public List<String> getGuardrailType() {
        return guardrailType;
    }

    public void setGuardrailType(List<String> guardrailType) {
        this.guardrailType = guardrailType;
    }

    public Map<String, String> getGuardrailEnvVars() {
        return guardrailEnvVars;
    }

    public void setGuardrailEnvVars(Map<String, String> guardrailEnvVars) {
        this.guardrailEnvVars = guardrailEnvVars;
    }

    public String getGuardrailTargetMode() {
        return guardrailTargetMode;
    }

    public void setGuardrailTargetMode(String guardrailTargetMode) {
        this.guardrailTargetMode = guardrailTargetMode;
    }

    public List<String> getGuardrailAgentIds() {
        return guardrailAgentIds;
    }

    public void setGuardrailAgentIds(List<String> guardrailAgentIds) {
        this.guardrailAgentIds = guardrailAgentIds;
    }
}
