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
            Updates.set(SentinelOneIntegration.RECURRING_INTERVAL_SECONDS, interval),
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

    /** Upload a script to the SentinelOne remote scripts library. */
    public String uploadSentinelOneRemoteScript() {
        if (scriptName == null || scriptName.trim().isEmpty() || scriptContent == null || scriptContent.trim().isEmpty()) {
            addActionError("scriptName and scriptContent are required.");
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

            String siteId = fetchSiteIdFromAgents(integration.getApiToken(), normalizedConsoleUrl);
            if (siteId == null || siteId.isEmpty()) {
                addActionError("Could not determine site ID — ensure at least one agent is enrolled.");
                return Action.ERROR.toUpperCase();
            }

            // Detect OS from file extension when not provided
            String os = "macos";
            if (scriptName.endsWith(".ps1")) os = "windows";
            else if (scriptName.endsWith(".py")) os = "linux";

            String fileName = scriptName.endsWith(".sh") || scriptName.endsWith(".ps1") || scriptName.endsWith(".py")
                ? scriptName : scriptName + ".sh";
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("scriptName", scriptName);
            builder.addTextBody("scriptType", "artifactCollection");
            builder.addTextBody("scopeLevel", "site");
            builder.addTextBody("scopeId", siteId);
            builder.addTextBody("inputRequired", "false");
            builder.addTextBody("osTypes", os);
            builder.addBinaryBody("file",
                scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ContentType.TEXT_PLAIN, fileName);

            HttpPost post = new HttpPost(normalizedConsoleUrl + "/web/api/v2.1/remote-scripts");
            post.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            post.setEntity(builder.build());

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200 && statusCode != 201) {
                    loggerMaker.error("Upload remote script failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    addActionError("Upload failed: " + responseBody);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                scriptId = json.path("data").path("id").asText(null);
            }
            return Action.SUCCESS.toUpperCase();
        } catch (IOException e) {
            loggerMaker.error("Error uploading remote script: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error uploading remote script: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /** Fetch the content of a remote script by ID. */
    public String getSentinelOneRemoteScriptContent() {
        if (scriptId == null || scriptId.trim().isEmpty()) {
            addActionError("scriptId is required.");
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
            HttpGet get = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/remote-scripts/script-content?scriptId=" + scriptId);
            get.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    addActionError("Failed to fetch script content: HTTP " + statusCode);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                scriptContent = json.path("data").path("scriptContent").asText(responseBody);
            }
            return Action.SUCCESS.toUpperCase();
        } catch (IOException e) {
            loggerMaker.error("Error fetching script content: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error fetching script content: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
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

    private String normalizeUrl(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}
