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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MicrosoftDefenderExecutor extends AccountJobExecutor {

    public static final MicrosoftDefenderExecutor INSTANCE = new MicrosoftDefenderExecutor();

    private static final LoggerMaker logger = new LoggerMaker(MicrosoftDefenderExecutor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CURSOR_KEY = "lastQueriedAt";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int SOCKET_TIMEOUT_MS = 60_000;
    private static final int BATCH_SIZE = 500;

    private static final String KQL_QUERY =
        "DeviceProcessEvents" +
        "| where ProcessCommandLine has_any (\"openclaw\", \"clawdbot\", \"moltbot\", \"gateway\")" +
        "| project Timestamp, DeviceName, AccountName, AccountDomain, FileName, ProcessCommandLine, InitiatingProcessFileName" +
        "| order by Timestamp desc";

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

        String accessToken = fetchAccessToken(tenantId, clientId, clientSecret);

        Instant now = Instant.now();
        Object cursorVal = jobConfig.get(CURSOR_KEY);
        String lastQueriedAt = cursorVal != null ? cursorVal.toString() : null;

        List<Map<String, Object>> results = runHuntingQuery(accessToken, buildKqlQuery(lastQueriedAt));
        logger.info("Microsoft Defender query returned {} results for jobId={}", results.size(), job.getId());

        if (!results.isEmpty()) {
            String normalizedUrl = dataIngestionUrl.endsWith("/")
                ? dataIngestionUrl.substring(0, dataIngestionUrl.length() - 1)
                : dataIngestionUrl;

            List<Map<String, Object>> batch = new ArrayList<>();
            int batchNum = 0;
            for (Map<String, Object> row : results) {
                batch.add(toIngestionRecord(row, job.getAccountId()));
                if (batch.size() >= BATCH_SIZE) {
                    postBatch(normalizedUrl, batch, ++batchNum, job);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                postBatch(normalizedUrl, batch, ++batchNum, job);
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("config." + CURSOR_KEY, now.toString());
        CyborgApiClient.updateJob(job.getId(), updates);

        logger.info("Microsoft Defender job completed: jobId={}", job.getId());
    }

    private void postBatch(String baseUrl, List<Map<String, Object>> records, int batchNum, AccountJob job)
            throws IOException, RetryableJobException {
        updateJobHeartbeat(job);

        Map<String, Object> payload = new HashMap<>();
        payload.put("batchData", records);
        String jsonPayload = OBJECT_MAPPER.writeValueAsString(payload);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost post = new HttpPost(baseUrl + "/api/ingestData");
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                EntityUtils.consumeQuietly(response.getEntity());

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

    private Map<String, Object> toIngestionRecord(Map<String, Object> row, int accountId) throws IOException {
        Map<String, Object> record = new HashMap<>();

        String deviceName = getStringOrDefault(row, "DeviceName", "unknown-device");
        record.put("path", "/defender/process-events/" + deviceName);
        record.put("method", "GET");
        record.put("statusCode", "200");
        record.put("type", "HTTP/1.1");
        record.put("status", "OK");

        record.put("requestPayload", getStringOrDefault(row, "ProcessCommandLine", ""));
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
        tagMap.put("source", "DEFENDER");
        tagMap.put("bot-name", getStringOrDefault(row, "AccountName", ""));
        tagMap.put("agent-name", "openclaw");
        record.put("tag", OBJECT_MAPPER.writeValueAsString(tagMap));

        return record;
    }

    private String buildKqlQuery(String lastQueriedAt) {
        if (lastQueriedAt != null && !lastQueriedAt.isEmpty()) {
            return "DeviceProcessEvents" +
                "| where Timestamp > datetime(" + lastQueriedAt + ")" +
                "| where ProcessCommandLine has_any (\"openclaw\", \"clawdbot\", \"moltbot\", \"gateway\")" +
                "| project Timestamp, DeviceName, AccountName, AccountDomain, FileName, ProcessCommandLine, InitiatingProcessFileName" +
                "| order by Timestamp desc";
        }
        return KQL_QUERY;
    }

    private String fetchAccessToken(String tenantId, String clientId, String clientSecret)
            throws IOException, RetryableJobException {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

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

    private List<Map<String, Object>> runHuntingQuery(String accessToken, String kqlQuery)
            throws IOException, RetryableJobException {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("Query", kqlQuery);

            HttpPost post = new HttpPost("https://api.securitycenter.microsoft.com/api/advancedqueries/run");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode == 429 || statusCode >= 500) {
                    throw new RetryableJobException("Defender Advanced Hunting returned " + statusCode);
                }
                if (statusCode != 200) {
                    throw new IOException("Defender Advanced Hunting returned HTTP " + statusCode + " - " + responseBody);
                }

                JsonNode json = OBJECT_MAPPER.readTree(responseBody);
                JsonNode resultsNode = json.get("Results");
                if (resultsNode == null || !resultsNode.isArray()) {
                    return new ArrayList<>();
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                for (JsonNode rowNode : resultsNode) {
                    rows.add(OBJECT_MAPPER.convertValue(rowNode, Map.class));
                }
                return rows;
            }
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
