package com.akto.jobs.executors.salesforce;

import com.akto.jobs.exception.RetryableJobException;
import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.*;

/**
 * Salesforce API Client for OAuth authentication and AI Agent chat data fetching.
 * Handles token refresh and data retrieval from Salesforce using SQL queries.
 */
public class SalesforceApiClient {

    private static final LoggerMaker logger = new LoggerMaker(SalesforceApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    private final String baseUrl;
    private final String apiVersion;
    private String accessToken;
    private final String clientId;
    private final String clientSecret;
    private long tokenExpiresAt = 0;
    private boolean tokenInitialized = false;
    private static final int MAX_TOKEN_RETRIES = 1;

    public SalesforceApiClient(String baseUrl, String apiVersion, String clientId, String clientSecret) {
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.accessToken = null;
        this.tokenInitialized = false;
    }

    /**
     * Fetch AI Agent Interaction Messages from Salesforce.
     * Uses SQL API to query the ssot__AiAgentInteractionMessage__dlm object.
     * Generates token on first call, regenerates only if 401 is received.
     *
     * @param limit Number of records to fetch
     * @param offset Offset for pagination
     * @return List of formatted Salesforce chat data
     * @throws Exception if fetch fails
     */
    public List<Map<String, Object>> fetchChatData(int limit, int offset) throws Exception {
        return fetchChatDataInternal(limit, offset, 0);
    }

    private List<Map<String, Object>> fetchChatDataInternal(int limit, int offset, int tokenRetryCount) throws Exception {
        try {
            // Generate token on first call
            if (!tokenInitialized) {
                logger.info("Token not initialized, generating new Salesforce access token");
                generateToken();
                tokenInitialized = true;
            }

            String query = buildSalesforceQuery(limit, offset);
            String url = baseUrl + "/services/data/" + AIAgentConnectorConstants.SALESFORCE_API_VERSION + "/ssot/query-sql";

            logger.infoAndAddToDb("Fetching chat data from Salesforce: URL={}"+url +"limit="+limit+", offset="+offset, LoggerMaker.LogDb.AGENTIC_TESTING);

            Request request = new Request.Builder()
                .url(url)
                .header(AIAgentConnectorConstants.HEADER_AUTHORIZATION, AIAgentConnectorConstants.AUTH_BEARER_PREFIX + accessToken)
                .header(AIAgentConnectorConstants.HEADER_CONTENT_TYPE, AIAgentConnectorConstants.CONTENT_TYPE_JSON)
                .post(RequestBody.create(
                    objectMapper.writeValueAsString(Collections.singletonMap("sql", query)),
                    MediaType.parse("application/json")
                ))
                .build();

            Response response = client.newCall(request).execute();

            if (response.code() == 401) {
                if (tokenRetryCount >= MAX_TOKEN_RETRIES) {
                    logger.error("Max token regeneration attempts exceeded. Authentication failed.");
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new Exception("Authentication failed after " + MAX_TOKEN_RETRIES + " retry(ies): " + errorBody);
                }
                logger.error("Access token invalid (401), regenerating token (attempt {}/{})",
                    tokenRetryCount + 1, MAX_TOKEN_RETRIES);
                tokenInitialized = false;
                generateToken();
                tokenInitialized = true;
                return fetchChatDataInternal(limit, offset, tokenRetryCount + 1);
            }

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                logger.errorAndAddToDb("Salesforce API error: status="+response.code(), LoggerMaker.LogDb.AGENTIC_TESTING);
                throw new RetryableJobException("Salesforce API returned status " + response.code());
            }

            String responseBody = response.body().string();
            return parseResponse(responseBody);

        } catch (RetryableJobException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching chat data from Salesforce: {}", e.getMessage());
            throw new RetryableJobException("Failed to fetch from Salesforce: " + e.getMessage(), e);
        }
    }

    /**
     * Build SQL query for fetching AI Agent Interaction Messages.
     */
    private String buildSalesforceQuery(int limit, int offset) {
        return "SELECT " +
            "ssot__AiAgentInteractionId__c, " +
            "ssot__Id__c, " +
            "ssot__AiAgentInteractionMessageType__c, " +
            "ssot__AiAgentInteractionMsgContentType__c, " +
            "ssot__AiAgentSessionId__c, " +
            "ssot__AiAgentSessionParticipantId__c, " +
            "ssot__ContentText__c, " +
            "ssot__DataSourceId__c, " +
            "ssot__DataSourceObjectId__c, " +
            "ssot__ExternalSourceId__c " +
            "FROM " + AIAgentConnectorConstants.SALESFORCE_TABLE_NAME + " " +
            "ORDER BY " + AIAgentConnectorConstants.SALESFORCE_ORDER_BY + " " +
            "LIMIT " + limit + " " +
            "OFFSET " + offset;
    }

    /**
     * Parse Salesforce API response and format it.
     */
    private List<Map<String, Object>> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        List<Map<String, Object>> results = new ArrayList<>();

        JsonNode dataArray = root.get("data");
        JsonNode metadataArray = root.get("metadata");

        if (dataArray == null || metadataArray == null || !dataArray.isArray()) {
            logger.warn("Invalid Salesforce response structure");
            return results;
        }

        // Build column index map
        Map<String, Integer> columnIndices = new HashMap<>();
        for (int i = 0; i < metadataArray.size(); i++) {
            String columnName = metadataArray.get(i).get("name").asText();
            columnIndices.put(columnName, i);
        }

        // Transform each row
        for (JsonNode row : dataArray) {
            Map<String, Object> record = new HashMap<>();
            record.put("interactionId", getFieldValue(row, columnIndices, "ssot__AiAgentInteractionId__c"));
            record.put("id", getFieldValue(row, columnIndices, "ssot__Id__c"));
            record.put("messageType", getFieldValue(row, columnIndices, "ssot__AiAgentInteractionMessageType__c"));
            record.put("contentType", getFieldValue(row, columnIndices, "ssot__AiAgentInteractionMsgContentType__c"));
            record.put("sessionId", getFieldValue(row, columnIndices, "ssot__AiAgentSessionId__c"));
            record.put("participantId", getFieldValue(row, columnIndices, "ssot__AiAgentSessionParticipantId__c"));
            record.put("contentText", getFieldValue(row, columnIndices, "ssot__ContentText__c"));
            record.put("dataSourceId", getFieldValue(row, columnIndices, "ssot__DataSourceId__c"));
            record.put("dataSourceObjectId", getFieldValue(row, columnIndices, "ssot__DataSourceObjectId__c"));
            record.put("externalSourceId", getFieldValue(row, columnIndices, "ssot__ExternalSourceId__c"));

            results.add(record);
        }

        logger.info("Parsed {} records from Salesforce response", results.size());
        return results;
    }

    /**
     * Get field value from row using column index.
     */
    private Object getFieldValue(JsonNode row, Map<String, Integer> columnIndices, String fieldName) {
        Integer index = columnIndices.get(fieldName);
        if (index == null || index >= row.size()) {
            return null;
        }
        JsonNode value = row.get(index);
        return value.isNull() ? null : value.asText();
    }

    /**
     * Generate Salesforce access token using OAuth2 client credentials flow.
     * Called on first use and when token becomes invalid (401 response).
     */
    public synchronized void generateToken() throws Exception {
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            throw new Exception("Salesforce OAuth credentials not configured");
        }

        try {
            String tokenUrl = baseUrl + AIAgentConnectorConstants.SALESFORCE_OAUTH_TOKEN_ENDPOINT;

            logger.infoAndAddToDb("Generating Salesforce access token from URL: {}", LoggerMaker.LogDb.AGENTIC_TESTING);

            FormBody formBody = new FormBody.Builder()
                .add("grant_type", AIAgentConnectorConstants.SALESFORCE_OAUTH_GRANT_TYPE)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();

            Request request = new Request.Builder()
                .url(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody)
                .build();

            long startTime = System.currentTimeMillis();
            Response response = client.newCall(request).execute();
            long duration = System.currentTimeMillis() - startTime;

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                logger.error("Token generation failed: status={}, response={}", response.code(), errorBody);
                throw new RetryableJobException("Failed to generate Salesforce token: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            this.accessToken = root.get("access_token").asText();
            long expiresIn = root.has("expires_in") ? root.get("expires_in").asLong() : 3600;  // Default 1 hour
            this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000);

            logger.info("✓ Salesforce token generated successfully in {}ms. Expires in {} seconds",
                duration, expiresIn);

        } catch (RetryableJobException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error generating Salesforce token: {}", e.getMessage(), e);
            throw new RetryableJobException("Token generation failed: " + e.getMessage(), e);
        }
    }

    public String getAccessToken() {
        return accessToken;
    }
}
