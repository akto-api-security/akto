package com.akto.action;

import com.akto.dao.MicrosoftDefenderIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.jobs.AccountJobDao;
import com.akto.dto.jobs.AccountJob;
import com.akto.dto.jobs.JobStatus;
import com.akto.dto.jobs.ScheduleType;
import com.akto.dto.microsoft_defender_integration.MicrosoftDefenderIntegration;
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
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
public class MicrosoftDefenderIntegrationAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MicrosoftDefenderIntegrationAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String JOB_TYPE = "MICROSOFT_DEFENDER_AH";
    private static final String JOB_SUB_TYPE = "ADVANCED_HUNTING";
    private static final int DEFAULT_INTERVAL = 3600;
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS = 60_000;

    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String dataIngestionUrl;
    private Integer recurringIntervalSeconds;

    private static final int LIVE_RESPONSE_POLL_INTERVAL_MS = 5_000;
    private static final int LIVE_RESPONSE_MAX_WAIT_MS = 300_000; // 5 minutes
    private static final int CANCEL_RETRY_WAIT_MS = 10_000;
    private static final Pattern ACTION_ID_PATTERN = Pattern.compile("action id:\\s*([a-f0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    // Live response fields
    private List<String> deviceIds;
    private String scriptContent;
    private String scriptName;
    private String scriptParameters;
    private List<Map<String, Object>> liveResponseResults;
    private List<Map<String, Object>> devices;
    private List<Map<String, Object>> libraryScripts;

    // KQL query fields
    private String kqlQuery;
    private Integer kqlTimeRangeDays;
    private List<Map<String, Object>> kqlResults;
    private String agentName;

    private MicrosoftDefenderIntegration microsoftDefenderIntegration;

    public String fetchMicrosoftDefenderIntegration() {
        microsoftDefenderIntegration = MicrosoftDefenderIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(MicrosoftDefenderIntegration.CLIENT_SECRET)
        );
        return Action.SUCCESS.toUpperCase();
    }

    public String addMicrosoftDefenderIntegration() {
        if (tenantId == null || tenantId.isEmpty()) {
            addActionError("Please enter a valid tenant ID.");
            return Action.ERROR.toUpperCase();
        }

        if (clientId == null || clientId.isEmpty()) {
            addActionError("Please enter a valid client ID.");
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

        org.bson.conversions.Bson updates = Updates.combine(
            Updates.set(MicrosoftDefenderIntegration.TENANT_ID, tenantId),
            Updates.set(MicrosoftDefenderIntegration.CLIENT_ID, clientId),
            Updates.set(MicrosoftDefenderIntegration.DATA_INGESTION_URL, dataIngestionUrl),
            Updates.set(MicrosoftDefenderIntegration.RECURRING_INTERVAL_SECONDS, interval),
            Updates.setOnInsert(MicrosoftDefenderIntegration.CREATED_TS, now),
            Updates.set(MicrosoftDefenderIntegration.UPDATED_TS, now)
        );

        String resolvedClientSecret;
        if (clientSecret != null && !clientSecret.isEmpty()) {
            resolvedClientSecret = clientSecret;
            updates = Updates.combine(updates, Updates.set(MicrosoftDefenderIntegration.CLIENT_SECRET, clientSecret));
        } else {
            MicrosoftDefenderIntegration existing = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
            if (existing == null || existing.getClientSecret() == null || existing.getClientSecret().isEmpty()) {
                addActionError("Please enter a valid client secret.");
                return Action.ERROR.toUpperCase();
            }
            resolvedClientSecret = existing.getClientSecret();
        }

        MicrosoftDefenderIntegrationDao.instance.updateOne(new BasicDBObject(), updates);

        // Upsert AccountJob for the recurring defender job
        AccountJob existingJob = AccountJobDao.instance.findOne(
            Filters.and(
                Filters.eq(AccountJob.JOB_TYPE, JOB_TYPE),
                Filters.eq(AccountJob.SUB_TYPE, JOB_SUB_TYPE)
            )
        );

        if (existingJob == null) {
            Map<String, Object> jobConfig = new HashMap<>();
            jobConfig.put(MicrosoftDefenderIntegration.TENANT_ID, tenantId);
            jobConfig.put(MicrosoftDefenderIntegration.CLIENT_ID, clientId);
            jobConfig.put(MicrosoftDefenderIntegration.CLIENT_SECRET, resolvedClientSecret);
            jobConfig.put(MicrosoftDefenderIntegration.DATA_INGESTION_URL, dataIngestionUrl);

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
            loggerMaker.info("Created Microsoft Defender account job", LogDb.DASHBOARD);
        } else {
            org.bson.conversions.Bson jobUpdates = Updates.combine(
                Updates.set("config." + MicrosoftDefenderIntegration.TENANT_ID, tenantId),
                Updates.set("config." + MicrosoftDefenderIntegration.CLIENT_ID, clientId),
                Updates.set("config." + MicrosoftDefenderIntegration.CLIENT_SECRET, resolvedClientSecret),
                Updates.set("config." + MicrosoftDefenderIntegration.DATA_INGESTION_URL, dataIngestionUrl),
                Updates.set(AccountJob.RECURRING_INTERVAL_SECONDS, interval),
                Updates.set(AccountJob.LAST_UPDATED_AT, now),
                Updates.set(AccountJob.JOB_STATUS, JobStatus.SCHEDULED.name()),
                Updates.set(AccountJob.SCHEDULED_AT, now)
            );
            AccountJobDao.instance.updateOneNoUpsert(
                Filters.eq(AccountJob.ID, existingJob.getId()),
                jobUpdates
            );
            loggerMaker.info("Updated Microsoft Defender account job", LogDb.DASHBOARD);
        }

        loggerMaker.infoAndAddToDb("Microsoft Defender integration saved successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeMicrosoftDefenderIntegration() {
        MicrosoftDefenderIntegrationDao.instance.deleteAll(new BasicDBObject());

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

        loggerMaker.infoAndAddToDb("Microsoft Defender integration removed successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchDefenderDevices() {
        MicrosoftDefenderIntegration integration = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Microsoft Defender integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        try {
            String accessToken = fetchAccessToken(integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpGet get = new HttpGet("https://api.securitycenter.microsoft.com/api/machines?$select=id,computerDnsName,osPlatform,lastSeen&$top=500");
                get.setHeader("Authorization", "Bearer " + accessToken);
                get.setHeader("Content-Type", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode != 200) {
                        addActionError("Failed to fetch devices from Microsoft Defender: HTTP " + statusCode);
                        return Action.ERROR.toUpperCase();
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    JsonNode valueNode = json.get("value");
                    devices = new ArrayList<>();
                    if (valueNode != null && valueNode.isArray()) {
                        for (JsonNode deviceNode : valueNode) {
                            devices.add(OBJECT_MAPPER.convertValue(deviceNode, Map.class));
                        }
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error fetching Defender devices: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error fetching devices: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String uploadDefenderScript() {
        if (scriptContent == null || scriptContent.isEmpty()) {
            addActionError("Please provide script content.");
            return Action.ERROR.toUpperCase();
        }
        if (scriptName == null || scriptName.isEmpty()) {
            addActionError("Please provide a script name.");
            return Action.ERROR.toUpperCase();
        }

        MicrosoftDefenderIntegration integration = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Microsoft Defender integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        try {
            String accessToken = fetchAccessToken(integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/libraryfiles");
                post.setHeader("Authorization", "Bearer " + accessToken);

                org.apache.http.HttpEntity multipart = MultipartEntityBuilder.create()
                    .addTextBody("FileName", scriptName, ContentType.TEXT_PLAIN)
                    .addTextBody("OverrideIfExists", "true", ContentType.TEXT_PLAIN)
                    .addBinaryBody("file", scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        ContentType.APPLICATION_OCTET_STREAM, scriptName)
                    .build();
                post.setEntity(multipart);

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode != 200 && statusCode != 201) {
                        loggerMaker.error("Failed to upload script to Defender library: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                        addActionError("Failed to upload script: HTTP " + statusCode + " - " + responseBody);
                        return Action.ERROR.toUpperCase();
                    }
                    loggerMaker.infoAndAddToDb("Script '" + scriptName + "' uploaded to Defender library successfully", LogDb.DASHBOARD);
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error uploading script to Defender library: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error uploading script: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String runDefenderLiveResponse() {
        if (deviceIds == null || deviceIds.isEmpty()) {
            addActionError("Please select at least one device.");
            return Action.ERROR.toUpperCase();
        }
        if (scriptName == null || scriptName.isEmpty()) {
            addActionError("Please provide a script name.");
            return Action.ERROR.toUpperCase();
        }

        MicrosoftDefenderIntegration integration = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Microsoft Defender integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        liveResponseResults = new ArrayList<>();

        try {
            String accessToken = fetchAccessToken(integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            for (String deviceId : deviceIds) {
                Map<String, Object> result = new HashMap<>();
                result.put("deviceId", deviceId);

                try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                    String actionId = submitLiveResponseAction(httpClient, accessToken, deviceId, scriptParameters);
                    if (actionId == null) {
                        // ActiveRequestAlreadyExists — try to cancel and retry once
                        String existingActionId = getActiveActionId(httpClient, accessToken, deviceId);
                        if (existingActionId != null) {
                            loggerMaker.info("Cancelling existing live response action " + existingActionId + " on device " + deviceId, LogDb.DASHBOARD);
                            cancelLiveResponseAction(httpClient, accessToken, existingActionId);
                            try { Thread.sleep(CANCEL_RETRY_WAIT_MS); } catch (InterruptedException ignored) {}
                        }
                        actionId = submitLiveResponseAction(httpClient, accessToken, deviceId, scriptParameters);
                    }

                    if (actionId == null) {
                        result.put("status", "error");
                        result.put("error", "Could not submit live response action after cancel retry");
                        liveResponseResults.add(result);
                        continue;
                    }

                    result.put("actionId", actionId);
                    loggerMaker.info("Live response action " + actionId + " submitted for device " + deviceId + ", polling for completion...", LogDb.DASHBOARD);

                    Map<String, Object> finalStatus = pollActionUntilDone(httpClient, accessToken, actionId);
                    String status = finalStatus.getOrDefault("status", "unknown").toString();
                    result.put("status", status);
                    result.put("response", finalStatus);
                    loggerMaker.infoAndAddToDb("Live response action " + actionId + " on device " + deviceId + " finished with status: " + status, LogDb.DASHBOARD);

                } catch (IOException e) {
                    result.put("status", "error");
                    result.put("error", e.getMessage());
                    loggerMaker.error("IOException for device " + deviceId + ": " + e.getMessage(), LogDb.DASHBOARD);
                }

                liveResponseResults.add(result);
            }
        } catch (IOException e) {
            loggerMaker.error("Error in live response execution: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error running live response: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        loggerMaker.infoAndAddToDb("Live response completed for " + deviceIds.size() + " device(s)", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    // Submits the RunScript live response action. Returns actionId on success, null if ActiveRequestAlreadyExists.
    private String submitLiveResponseAction(CloseableHttpClient httpClient, String accessToken, String deviceId, String params) throws IOException {
        Map<String, Object> scriptNameParam = new HashMap<>();
        scriptNameParam.put("key", "ScriptName");
        scriptNameParam.put("value", scriptName);

        List<Object> cmdParams = new ArrayList<>();
        cmdParams.add(scriptNameParam);
        if (params != null && !params.isEmpty()) {
            Map<String, Object> scriptArgsParam = new HashMap<>();
            scriptArgsParam.put("key", "Args");
            scriptArgsParam.put("value", params);
            cmdParams.add(scriptArgsParam);
        }

        Map<String, Object> runScriptCommand = new HashMap<>();
        runScriptCommand.put("type", "RunScript");
        runScriptCommand.put("params", cmdParams);

        Map<String, Object> body = new HashMap<>();
        body.put("Commands", new Object[]{ runScriptCommand });
        body.put("Comment", "Akto live response execution");

        HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/machines/" + deviceId + "/runliveresponse");
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode == 201) {
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                return json.path("id").asText(null);
            }
            if (statusCode == 400) {
                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                String errorCode = json.path("error").path("code").asText("");
                if ("ActiveRequestAlreadyExists".equals(errorCode)) {
                    return null; // signal to caller to cancel-and-retry
                }
            }
            throw new IOException("runliveresponse returned HTTP " + statusCode + ": " + responseBody);
        }
    }

    // Finds the active action ID on a device by looking at recent pending/InProgress machine actions.
    private String getActiveActionId(CloseableHttpClient httpClient, String accessToken, String deviceId) throws IOException {
        URI uri;
        try {
            uri = new URIBuilder("https://api.securitycenter.microsoft.com/api/machineactions")
                .addParameter("$filter", "machineId eq '" + deviceId + "' and (status eq 'Pending' or status eq 'InProgress')")
                .addParameter("$orderby", "creationDateTimeUtc desc")
                .addParameter("$top", "1")
                .build();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to build URI for getActiveActionId", e);
        }
        HttpGet get = new HttpGet(uri);
        get.setHeader("Authorization", "Bearer " + accessToken);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            if (statusCode != 200) return null;

            JsonNode json = OBJECT_MAPPER.readTree(responseBody);
            JsonNode value = json.path("value");
            if (value.isArray() && value.size() > 0) {
                return value.get(0).path("id").asText(null);
            }

            // fallback: parse action id from error message stored by caller if needed
            Matcher m = ACTION_ID_PATTERN.matcher(responseBody);
            return m.find() ? m.group(1) : null;
        }
    }

    private void cancelLiveResponseAction(CloseableHttpClient httpClient, String accessToken, String actionId) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("Comment", "Cancelled by Akto before retry");

        HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/machineactions/" + actionId + "/cancel");
        post.setHeader("Authorization", "Bearer " + accessToken);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            loggerMaker.info("Cancel action " + actionId + " returned HTTP " + statusCode, LogDb.DASHBOARD);
        }
    }

    // Polls GET /api/machineactions/{id} until status is Succeeded, Failed, Cancelled, or timeout.
    @SuppressWarnings("unchecked")
    private Map<String, Object> pollActionUntilDone(CloseableHttpClient httpClient, String accessToken, String actionId) throws IOException {
        long deadline = System.currentTimeMillis() + LIVE_RESPONSE_MAX_WAIT_MS;

        while (System.currentTimeMillis() < deadline) {
            HttpGet get = new HttpGet("https://api.securitycenter.microsoft.com/api/machineactions/" + actionId);
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    throw new IOException("Polling action " + actionId + " returned HTTP " + statusCode + ": " + responseBody);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                String status = json.path("status").asText("unknown");

                if ("Succeeded".equals(status) || "Failed".equals(status) || "Cancelled".equals(status) || "TimeOut".equals(status)) {
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

    public String runDefenderKqlQuery() {
        if (kqlQuery == null || kqlQuery.trim().isEmpty()) {
            addActionError("Please provide a KQL query.");
            return Action.ERROR.toUpperCase();
        }

        MicrosoftDefenderIntegration integration = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Microsoft Defender integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        try {
            String accessToken = fetchAccessToken(integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

            int days = (kqlTimeRangeDays != null && kqlTimeRangeDays > 0 && kqlTimeRangeDays <= 30) ? kqlTimeRangeDays : 1;
            String timeFilter = "| where Timestamp > ago(" + days + "d)";
            String finalQuery = kqlQuery.trim().contains("ago(") ? kqlQuery.trim() : kqlQuery.trim() + "\n" + timeFilter;

            Map<String, String> body = new HashMap<>();
            body.put("Query", finalQuery);

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/advancedqueries/run");
                post.setHeader("Authorization", "Bearer " + accessToken);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode != 200) {
                        loggerMaker.error("KQL query failed: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                        addActionError("Query is invalid or could not be executed. Please check your KQL syntax and try again.");
                        return Action.ERROR.toUpperCase();
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    JsonNode results = json.path("Results");
                    kqlResults = new ArrayList<>();
                    if (results.isArray()) {
                        for (JsonNode row : results) {
                            kqlResults.add(OBJECT_MAPPER.convertValue(row, Map.class));
                        }
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error running KQL query: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Query is invalid or could not be executed. Please check your KQL syntax and try again.");
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String ingestDefenderKqlResults() {
        if (kqlResults == null || kqlResults.isEmpty()) {
            addActionError("No KQL results to ingest.");
            return Action.ERROR.toUpperCase();
        }
        if (agentName == null || agentName.trim().isEmpty()) {
            addActionError("Please provide an agent name.");
            return Action.ERROR.toUpperCase();
        }

        MicrosoftDefenderIntegration integration = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Microsoft Defender integration not configured.");
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

        try {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (Map<String, Object> row : kqlResults) {
                batch.add(toKqlIngestionRecord(row, agentName.trim()));
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
            loggerMaker.error("Error ingesting KQL results: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to ingest KQL results: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        loggerMaker.infoAndAddToDb("Ingested " + kqlResults.size() + " KQL result(s) for agent: " + agentName, LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    private Map<String, Object> toKqlIngestionRecord(Map<String, Object> row, String agent) throws IOException {
        Map<String, Object> record = new HashMap<>();

        String deviceId = getStringOrDefault(row, "DeviceId", null);
        String deviceName = getStringOrDefault(row, "DeviceName", null);
        String botName = deviceId != null ? deviceId : (deviceName != null ? deviceName : "unknown-device");
        String pathSegment = deviceName != null ? deviceName : (deviceId != null ? deviceId : "unknown-device");
        record.put("path", "/defender/kql-events/" + pathSegment);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");

        record.put("requestPayload", "");
        record.put("responsePayload", OBJECT_MAPPER.writeValueAsString(row));

        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("host", "api.securitycenter.microsoft.com");
        requestHeaders.put("content-type", "application/json");
        record.put("requestHeaders", OBJECT_MAPPER.writeValueAsString(requestHeaders));

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");
        record.put("responseHeaders", OBJECT_MAPPER.writeValueAsString(responseHeaders));

        Object tsVal = row.get("Timestamp");
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
        tagMap.put("connector", "MICROSOFT_DEFENDER");
        tagMap.put("ai-agent", agent);
        tagMap.put("bot-name", botName);
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    public String listDefenderLibraryScripts() {
        MicrosoftDefenderIntegration integration = MicrosoftDefenderIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            addActionError("Microsoft Defender integration not configured.");
            return Action.ERROR.toUpperCase();
        }

        try {
            String accessToken = fetchAccessToken(integration.getTenantId(), integration.getClientId(), integration.getClientSecret());

            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
                HttpGet get = new HttpGet("https://api.securitycenter.microsoft.com/api/libraryfiles");
                get.setHeader("Authorization", "Bearer " + accessToken);

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity());

                    if (statusCode != 200) {
                        loggerMaker.error("Failed to list Defender library scripts: HTTP " + statusCode + " - " + responseBody, LogDb.DASHBOARD);
                        addActionError("Failed to list library scripts: HTTP " + statusCode);
                        return Action.ERROR.toUpperCase();
                    }

                    JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                    JsonNode value = json.path("value");
                    libraryScripts = new ArrayList<>();
                    if (value.isArray()) {
                        for (JsonNode item : value) {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("fileName", item.path("fileName").asText(""));
                            entry.put("description", item.path("description").asText(""));
                            entry.put("creationTime", item.path("creationTime").asText(""));
                            libraryScripts.add(entry);
                        }
                    }
                }
            }
        } catch (IOException e) {
            loggerMaker.error("Error listing Defender library scripts: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Error listing library scripts: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    private String fetchAccessToken(String tenantId, String clientId, String clientSecret) throws IOException {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            String body = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&scope=https%3A%2F%2Fapi.securitycenter.microsoft.com%2F.default";

            HttpPost post = new HttpPost(tokenUrl);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

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

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }
}
