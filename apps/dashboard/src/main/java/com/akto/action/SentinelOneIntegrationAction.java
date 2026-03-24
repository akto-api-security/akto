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

    private static final int SDL_POLL_INTERVAL_MS = 3_000;
    private static final int SDL_MAX_WAIT_MS = 60_000;

    // filter: optional list of agent IDs to scope the application fetch
    private List<String> agentIds;

    // DV / SDL query fields — user provides plain search terms, backend builds S1QL
    private List<String> searchTerms;
    private int sdlLookbackHours = 24;

    // Remote scripts fields
    private String scriptName;
    private String scriptContent;
    private String osTypes;        // macos | linux | windows
    private String scriptId;
    private List<String> executeAgentIds;  // agent IDs to run the script on
    private String executeTaskDescription;
    private String executeInputParams;
    private List<Map<String, Object>> remoteScripts;
    private String parentTaskId;

    private SentinelOneIntegration sentinelOneIntegration;
    private List<Map<String, Object>> agents;
    private List<Map<String, Object>> agentApplications;
    private List<Map<String, Object>> sdlResults;
    private String agentName;  // For ingestion: ai-agent tag value

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

    public String generateSentinelOneApiToken() {
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
                HttpGet get = new HttpGet(normalizedConsoleUrl + "/web/api/v2.0/users/generate-api-token");
                get.setHeader("Authorization", "Bearer " + integration.getApiToken());
                get.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode != 200) {
                        loggerMaker.error("Failed to generate SentinelOne API token: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                        addActionError("Failed to generate API token: HTTP " + statusCode);
                        return Action.ERROR.toUpperCase();
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    String newToken = json.path("data").path("token").asText(null);
                    if (newToken == null || newToken.isEmpty()) {
                        addActionError("No token in SentinelOne response.");
                        return Action.ERROR.toUpperCase();
                    }

                    int now = Context.now();
                    SentinelOneIntegrationDao.instance.updateOne(
                        new BasicDBObject(),
                        Updates.combine(
                            Updates.set(SentinelOneIntegration.API_TOKEN, newToken),
                            Updates.set(SentinelOneIntegration.UPDATED_TS, now)
                        )
                    );

                    AccountJob existingJob = AccountJobDao.instance.findOne(
                        Filters.and(
                            Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                            Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
                        )
                    );
                    if (existingJob != null) {
                        AccountJobDao.instance.updateOneNoUpsert(
                            Filters.eq(AccountJob.ID, existingJob.getId()),
                            Updates.set("config." + SentinelOneIntegration.API_TOKEN, newToken)
                        );
                    }

                    loggerMaker.infoAndAddToDb("SentinelOne API token regenerated successfully", LogDb.DASHBOARD);
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error generating SentinelOne API token: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error generating API token: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

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

    /**
     * Fetches applications for the given agentIds (or all agents if none provided),
     * filtered to only Claude/Cursor entries. Returns agentApplications with agentId
     * and computerName attached.
     */
    @SuppressWarnings("unchecked")
    public String fetchSentinelOneAgentApplications() {
        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());

        try {
            // Build id→computerName map from agents list
            Map<String, String> agentComputerNames = new HashMap<>();
            List<String> idsToFetch = new ArrayList<>();

            if (agentIds != null && !agentIds.isEmpty()) {
                idsToFetch.addAll(agentIds);
            } else {
                // fetch all agent IDs
                List<Map<String, Object>> allAgents = fetchAgentList(integration.getApiToken(), normalizedConsoleUrl);
                for (Map<String, Object> a : allAgents) {
                    String id = (String) a.get("id");
                    if (id != null && !id.isEmpty()) {
                        idsToFetch.add(id);
                        agentComputerNames.put(id, (String) a.getOrDefault("computerName", ""));
                    }
                }
            }

            agentApplications = new ArrayList<>();

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                for (String agentId : idsToFetch) {
                    HttpGet get = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/agents/applications?ids=" + agentId);
                    get.setHeader("Authorization", "ApiToken " + integration.getApiToken());
                    get.setHeader("Content-Type", "application/json");

                    try (CloseableHttpResponse response = httpClient.execute(get)) {
                        int statusCode = response.getStatusLine().getStatusCode();
                        String responseBody = EntityUtils.toString(response.getEntity());

                        if (statusCode != 200) {
                            loggerMaker.error("Failed to fetch applications for agent " + agentId + ": HTTP " + statusCode, LogDb.DASHBOARD);
                            continue;
                        }

                        JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                        JsonNode dataNode = json.path("data");
                        if (dataNode.isArray()) {
                            for (JsonNode appNode : dataNode) {
                                Map<String, Object> appEntry = OBJECT_MAPPER.convertValue(appNode, Map.class);
                                appEntry.put("agentId", agentId);
                                appEntry.put("computerName", agentComputerNames.getOrDefault(agentId, ""));
                                agentApplications.add(appEntry);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error fetching SentinelOne agent applications: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error fetching agent applications: " + e.getMessage());
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

    private List<Map<String, Object>> fetchAgentList(String token, String normalizedConsoleUrl) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpGet get = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/agents?limit=1000");
            get.setHeader("Authorization", "ApiToken " + token);
            get.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    throw new IOException("Failed to fetch SentinelOne agents: HTTP " + statusCode + " - " + responseBody);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode dataNode = json.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode agentNode : dataNode) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("id", agentNode.path("id").asText(""));
                        entry.put("computerName", agentNode.path("computerName").asText(""));
                        result.add(entry);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Builds S1QL from searchTerms, runs via v2.1 Deep Visibility API (init-query → poll → events).
     */
    @SuppressWarnings("unchecked")
    public String runSentinelOneSdlQuery() {
        if (searchTerms == null || searchTerms.isEmpty()) {
            addActionError("Please provide at least one search term.");
            return Action.ERROR.toUpperCase();
        }

        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 0; i < searchTerms.size(); i++) {
            String term = searchTerms.get(i).trim().replace("\"", "\\\"");
            if (i > 0) queryBuilder.append(" OR ");
            queryBuilder.append("processCmd contains \"").append(term).append("\"")
                        .append(" OR processName contains \"").append(term).append("\"");
        }

        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());
        int lookback = sdlLookbackHours > 0 ? sdlLookbackHours : 24;
        String toDate = java.time.Instant.now().toString();
        String fromDate = java.time.Instant.now().minus(lookback, java.time.temporal.ChronoUnit.HOURS).toString();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {

            // Step 1: init-query
            Map<String, Object> body = new HashMap<>();
            body.put("query", queryBuilder.toString());
            body.put("fromDate", fromDate);
            body.put("toDate", toDate);
            body.put("limit", 1000);

            HttpPost post = new HttpPost(normalizedConsoleUrl + "/web/api/v2.1/dv/init-query");
            post.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

            String queryId;
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    loggerMaker.error("DV init-query failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    addActionError("Query failed: " + responseBody);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                queryId = json.path("data").path("queryId").asText(null);
                if (queryId == null || queryId.isEmpty()) {
                    addActionError("No queryId returned from SentinelOne.");
                    return Action.ERROR.toUpperCase();
                }
            }

            // Step 2: poll query-status until FINISHED or terminal
            long deadline = System.currentTimeMillis() + SDL_MAX_WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                HttpGet statusGet = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/dv/query-status?queryId=" + queryId);
                statusGet.setHeader("Authorization", "ApiToken " + integration.getApiToken());
                try (CloseableHttpResponse response = httpClient.execute(statusGet)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    String responseState = json.path("data").path("responseState").asText("");
                    if ("FINISHED".equalsIgnoreCase(responseState) || "EMPTY_RESULTS".equalsIgnoreCase(responseState)) break;
                    if ("FAILED".equalsIgnoreCase(responseState) || "FAILED_CLIENT".equalsIgnoreCase(responseState)
                            || "TIMED_OUT".equalsIgnoreCase(responseState) || "QUERY_EXPIRED".equalsIgnoreCase(responseState)) {
                        addActionError("Query ended with state: " + responseState);
                        return Action.ERROR.toUpperCase();
                    }
                }
                try { Thread.sleep(SDL_POLL_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            // Step 3: fetch events
            sdlResults = new ArrayList<>();
            HttpGet eventsGet = new HttpGet(normalizedConsoleUrl + "/web/api/v2.1/dv/events?queryId=" + queryId + "&limit=1000");
            eventsGet.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            try (CloseableHttpResponse response = httpClient.execute(eventsGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    loggerMaker.error("DV events fetch failed: HTTP " + statusCode, LogDb.DASHBOARD);
                    addActionError("Failed to fetch events: HTTP " + statusCode);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode data = json.path("data");
                if (data.isArray()) {
                    for (JsonNode event : data) {
                        sdlResults.add(OBJECT_MAPPER.convertValue(event, Map.class));
                    }
                }
            }

            loggerMaker.infoAndAddToDb("DV query returned " + sdlResults.size() + " result(s)", LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();

        } catch (IOException e) {
            loggerMaker.error("Error running DV query: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error running DV query: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /** List all remote scripts in the SentinelOne library. */
    @SuppressWarnings("unchecked")
    public String listSentinelOneRemoteScripts() {
        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }
        String normalizedConsoleUrl = normalizeUrl(integration.getConsoleUrl());
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS).setSocketTimeout(SOCKET_TIMEOUT_MS).build();
        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            // Include siteId so site-scoped user-uploaded scripts appear alongside built-ins
            String siteId = fetchSiteIdFromAgents(integration.getApiToken(), normalizedConsoleUrl);
            String listUrl = normalizedConsoleUrl + "/web/api/v2.1/remote-scripts?limit=200&sortby=createdAt&sortorder=desc";
            if (siteId != null && !siteId.isEmpty()) listUrl += "&siteIds=" + siteId;

            HttpGet get = new HttpGet(listUrl);
            get.setHeader("Authorization", "ApiToken " + integration.getApiToken());
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                if (statusCode != 200) {
                    loggerMaker.error("Failed to list remote scripts: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                    addActionError("Failed to list remote scripts: HTTP " + statusCode + " - " + responseBody);
                    return Action.ERROR.toUpperCase();
                }
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                remoteScripts = new ArrayList<>();
                JsonNode data = json.path("data");
                if (data.isArray()) {
                    for (JsonNode script : data) {
                        remoteScripts.add(OBJECT_MAPPER.convertValue(script, Map.class));
                    }
                }
            }
            return Action.SUCCESS.toUpperCase();
        } catch (IOException e) {
            loggerMaker.error("Error listing remote scripts: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error listing remote scripts: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
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

    /**
     * Ingests agent application results (from fetchSentinelOneAgentApplications) into Akto as collections.
     * Groups by device (computerName or agentId), uses SentinelOne domain for proper logo display.
     */
    public String ingestSentinelOneAgentApplications() {
        if (agentApplications == null || agentApplications.isEmpty()) {
            addActionError("No agent applications to ingest.");
            return Action.ERROR.toUpperCase();
        }

        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        String dataIngestionUrl = integration.getDataIngestionUrl();
        if (dataIngestionUrl == null || dataIngestionUrl.trim().isEmpty()) {
            addActionError("Data ingestion URL is not configured.");
            return Action.ERROR.toUpperCase();
        }

        String normalizedUrl = dataIngestionUrl.endsWith("/")
            ? dataIngestionUrl.substring(0, dataIngestionUrl.length() - 1)
            : dataIngestionUrl;

        String consoleUrl = integration.getConsoleUrl();
        String domain = extractDomain(consoleUrl);

        try {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (Map<String, Object> app : agentApplications) {
                batch.add(toAppIngestionRecord(app, domain));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("batchData", batch);
            String jsonPayload = OBJECT_MAPPER.writeValueAsString(payload);

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpPost post = new HttpPost(normalizedUrl + "/api/ingestData");
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    EntityUtils.consumeQuietly(response.getEntity());
                    if (statusCode != 200) {
                        addActionError("Ingestion service returned HTTP " + statusCode);
                        return Action.ERROR.toUpperCase();
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error ingesting agent applications: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to ingest agent applications: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        loggerMaker.infoAndAddToDb("Ingested " + agentApplications.size() + " agent application(s)", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Ingests SDL (Deep Visibility) process event results into Akto as collections.
     * Groups by device and uses agentName to set the ai-agent and bot-name tags.
     */
    public String ingestSentinelOneSdlEvents() {
        if (sdlResults == null || sdlResults.isEmpty()) {
            addActionError("No SDL events to ingest.");
            return Action.ERROR.toUpperCase();
        }
        if (agentName == null || agentName.trim().isEmpty()) {
            addActionError("Please provide an agent name.");
            return Action.ERROR.toUpperCase();
        }

        SentinelOneIntegration integration = SentinelOneIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("SentinelOne integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        String dataIngestionUrl = integration.getDataIngestionUrl();
        if (dataIngestionUrl == null || dataIngestionUrl.trim().isEmpty()) {
            addActionError("Data ingestion URL is not configured.");
            return Action.ERROR.toUpperCase();
        }

        String normalizedUrl = dataIngestionUrl.endsWith("/")
            ? dataIngestionUrl.substring(0, dataIngestionUrl.length() - 1)
            : dataIngestionUrl;

        String consoleUrl = integration.getConsoleUrl();
        String domain = extractDomain(consoleUrl);

        try {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (Map<String, Object> event : sdlResults) {
                batch.add(toSdlIngestionRecord(event, agentName.trim(), domain));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("batchData", batch);
            String jsonPayload = OBJECT_MAPPER.writeValueAsString(payload);

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpPost post = new HttpPost(normalizedUrl + "/api/ingestData");
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    EntityUtils.consumeQuietly(response.getEntity());
                    if (statusCode != 200) {
                        addActionError("Ingestion service returned HTTP " + statusCode);
                        return Action.ERROR.toUpperCase();
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error ingesting SDL events: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to ingest SDL events: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        loggerMaker.infoAndAddToDb("Ingested " + sdlResults.size() + " SDL event(s) for agent: " + agentName, LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    private Map<String, Object> toAppIngestionRecord(Map<String, Object> app, String domain) throws IOException {
        Map<String, Object> record = new HashMap<>();

        String computerName = getStringOrDefault(app, "computerName", null);
        String agentId = getStringOrDefault(app, "agentId", null);
        String appName = getStringOrDefault(app, "name", "unknown-app");
        
        String botName = computerName != null ? computerName : (agentId != null ? agentId : "unknown-device");
        String pathSegment = appName.toLowerCase().replaceAll("[^a-z0-9_\\-]+", "-");
        String deviceSegment = computerName != null ? computerName : (agentId != null ? agentId : "unknown-device");
        
        record.put("path", "/sentinelone/apps/" + deviceSegment + "/" + pathSegment);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");

        record.put("requestPayload", "");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(app));

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", domain);
        requestHeaders.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(requestHeaders));

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");
        record.put("responseHeaders", OBJECT_MAPPER.writeValueAsString(responseHeaders));

        record.put("time", String.valueOf(System.currentTimeMillis()));

        record.put("source", "MIRRORING");
        record.put("akto_account_id", String.valueOf(Context.accountId.get()));
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
        tagMap.put("connector", "SENTINELONE");
        tagMap.put("ai-agent", appName);
        tagMap.put("bot-name", botName);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    private Map<String, Object> toSdlIngestionRecord(Map<String, Object> event, String agent, String domain) throws IOException {
        Map<String, Object> record = new HashMap<>();

        // Try common device field names
        String deviceName = getStringOrDefault(event, "endpointName", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "agentComputerName", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "computerName", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "hostname", null);
        if (deviceName == null) deviceName = getStringOrDefault(event, "srcIp", "unknown-device");

        String botName = deviceName;
        String pathSegment = deviceName.replaceAll("[^a-zA-Z0-9_\\-]+", "-");
        
        record.put("path", "/sentinelone/sdl-events/" + pathSegment);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");

        record.put("requestPayload", "");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(event));

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", domain);
        requestHeaders.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(requestHeaders));

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");
        record.put("responseHeaders", OBJECT_MAPPER.writeValueAsString(responseHeaders));

        Object tsVal = event.get("eventTime");
        if (tsVal == null) tsVal = event.get("timestamp");
        record.put("time", tsVal != null ? tsVal.toString() : String.valueOf(System.currentTimeMillis()));

        record.put("source", "MIRRORING");
        record.put("akto_account_id", String.valueOf(Context.accountId.get()));
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
        tagMap.put("connector", "SENTINELONE");
        tagMap.put("ai-agent", agent);
        tagMap.put("bot-name", botName);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "sentinelone.net";
        try {
            java.net.URI uri = new java.net.URI(normalizeUrl(url));
            String host = uri.getHost();
            return host != null ? host : "sentinelone.net";
        } catch (Exception e) {
            return "sentinelone.net";
        }
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
