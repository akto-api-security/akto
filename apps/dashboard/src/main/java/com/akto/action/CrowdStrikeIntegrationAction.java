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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
