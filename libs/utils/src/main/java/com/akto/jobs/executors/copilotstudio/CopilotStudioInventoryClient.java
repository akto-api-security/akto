package com.akto.jobs.executors.copilotstudio;

import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Queries the Power Platform inventory API for Copilot Studio agents; caller passes an access token for POWER_PLATFORM_RESOURCE. */
public class CopilotStudioInventoryClient {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioInventoryClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    public static final String POWER_PLATFORM_RESOURCE = "https://api.powerplatform.com";

    private static final String QUERY_ENDPOINT =
        POWER_PLATFORM_RESOURCE + "/resourcequery/resources/query?api-version=2024-10-01";
    private static final String TABLE_NAME = "PowerPlatformResources";
    private static final String AGENT_RESOURCE_TYPE = "microsoft.copilotstudio/agents";
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final int PAGE_SIZE = 1000;
    private static final int MAX_RETRIES = 3;
    /** Bounds the paging loop so a misbehaving skipToken can't spin forever. */
    private static final int MAX_PAGES = 50;

    /** Fetches every agent in the tenant, or just environmentId's; rows stay raw JSON so new fields flow through untouched. */
    public List<JsonNode> fetchAgents(String accessToken, String environmentId) throws Exception {
        List<JsonNode> agents = new ArrayList<>();
        String skipToken = "";

        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode response = executeQuery(accessToken, environmentId, skipToken);

            JsonNode data = response.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode row : data) {
                    agents.add(row);
                }
            }

            String nextToken = response.has("skipToken") && !response.get("skipToken").isNull()
                ? response.get("skipToken").asText() : "";
            // Empty or repeated continuation token means there is nothing more to read.
            if (nextToken.isEmpty() || nextToken.equals(skipToken)) {
                logger.info("CopilotStudioInventory: fetched {} agents for environmentId={}",
                    agents.size(), environmentId);
                return agents;
            }
            skipToken = nextToken;
        }

        logger.warn("CopilotStudioInventory: paging hit the {} page cap, returning {} agents",
            MAX_PAGES, agents.size());
        return agents;
    }

    private JsonNode executeQuery(String accessToken, String environmentId, String skipToken) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("TableName", TABLE_NAME);

        ArrayNode clauses = body.putArray("Clauses");
        // The value is pasted straight into KQL, so the resource type needs its own quotes.
        clauses.add(whereClause("type", "'" + AGENT_RESOURCE_TYPE + "'"));
        if (environmentId != null && !environmentId.isEmpty()) {
            clauses.add(whereClause("properties.environmentId", environmentId));
        }

        ObjectNode options = body.putObject("Options");
        options.put("Top", PAGE_SIZE);
        // Skip is deliberately omitted: the API documents Skip and SkipToken as mutually exclusive.
        if (skipToken != null && !skipToken.isEmpty()) {
            options.put("SkipToken", skipToken);
        }

        String responseBody = executeWithRetry(accessToken, objectMapper.writeValueAsString(body));
        return objectMapper.readTree(responseBody);
    }

    private ObjectNode whereClause(String fieldName, String value) {
        ObjectNode clause = objectMapper.createObjectNode();
        clause.put("$type", "where");
        clause.put("FieldName", fieldName);
        clause.put("Operator", "==");
        clause.putArray("Values").add(value);
        return clause;
    }

    /** Retries ARG throttling (429) and server errors; 401/403 are permission problems, so they fail fast. */
    private String executeWithRetry(String accessToken, String requestBody) throws Exception {
        Exception lastError = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Request request = new Request.Builder()
                .url(QUERY_ENDPOINT)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();

            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    return body;
                }

                InventoryException error = new InventoryException(response.code(), body);
                if (!error.isRetryable()) {
                    throw error;
                }
                lastError = error;
            } catch (InventoryException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
            }

            if (attempt < MAX_RETRIES - 1) {
                long waitMillis = (long) Math.pow(2, attempt) * 1000L;
                logger.warn("CopilotStudioInventory: query failed (attempt {}/{}), retrying in {}ms: {}",
                    attempt + 1, MAX_RETRIES, waitMillis, lastError.getMessage());
                Thread.sleep(waitMillis);
            }
        }

        throw new Exception("Inventory query failed after " + MAX_RETRIES + " attempts", lastError);
    }

    /** Non-2xx response from the inventory API. */
    public static class InventoryException extends Exception {
        private final int statusCode;

        public InventoryException(int statusCode, String body) {
            super("Inventory API error (status " + statusCode + "): " + body);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isRetryable() {
            return statusCode >= 500 || statusCode == 429;
        }

        /** True when rejected for permissions; caller logs and continues since the fix is a tenant-side grant. */
        public boolean isAuthorizationError() {
            return statusCode == 401 || statusCode == 403;
        }
    }
}
