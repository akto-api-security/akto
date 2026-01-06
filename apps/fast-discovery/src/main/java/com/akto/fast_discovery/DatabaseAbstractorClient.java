package com.akto.fast_discovery;

import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.fast_discovery.dto.ApiExistenceResult;
import com.akto.fast_discovery.dto.ApiId;
import com.akto.log.LoggerMaker;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DatabaseAbstractorClient - HTTP client for database-abstractor API.
 *
 * Handles all database operations via HTTP calls to database-abstractor (cyborg service).
 * Supports:
 * - Fetching API IDs for Bloom filter initialization
 * - Batch checking API existence
 * - Bulk writes to single_type_info and api_info collections
 */
public class DatabaseAbstractorClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DatabaseAbstractorClient.class);

    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final Gson gson;

    /**
     * Get JWT token from environment variable.
     * Same pattern as mini-runtime's ClientActor.getAuthToken()
     *
     * @return JWT token for authentication
     * @throws IllegalStateException if token is not set
     */
    private static String getAuthToken() {
        String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException(
                "DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable is required"
            );
        }
        return token;
    }

    public DatabaseAbstractorClient(String baseUrl) {
        this.baseUrl = baseUrl;
        // Validate token is available at initialization
        getAuthToken();
        this.httpClient = HttpClients.createDefault();
        this.gson = new Gson();
        loggerMaker.infoAndAddToDb("DatabaseAbstractorClient initialized with baseUrl: " + baseUrl);
    }

    /**
     * Fetch all API IDs for Bloom filter initialization.
     * Calls: GET /api/fetchApiIds
     *
     * @return List of API IDs (apiCollectionId, url, method)
     * @throws Exception if HTTP call fails
     */
    public List<ApiId> fetchApiIds() throws Exception {
        String endpoint = baseUrl + "/api/fetchApiIds";
        loggerMaker.infoAndAddToDb("Fetching API IDs from: " + endpoint);

        HttpGet request = new HttpGet(endpoint);
        request.setHeader("Authorization", getAuthToken());

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode >= 400) {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }

            // Parse JSON: [{"apiCollectionId": 1, "url": "/api/users", "method": "GET"}, ...]
            // Response is a direct array, not wrapped in an object
            Type responseType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> apiIdMaps = gson.fromJson(responseBody, responseType);

            List<ApiId> apiIds = new ArrayList<>();
            for (Map<String, Object> map : apiIdMaps) {
                ApiId apiId = new ApiId();
                apiId.setApiCollectionId(((Number) map.get("apiCollectionId")).intValue());
                apiId.setUrl((String) map.get("url"));
                apiId.setMethod((String) map.get("method"));
                apiIds.add(apiId);
            }

            loggerMaker.infoAndAddToDb("Fetched " + apiIds.size() + " API IDs");
            return apiIds;
        }
    }

    /**
     * Check if APIs exist in database (batch check).
     * Calls: POST /api/checkApisExist
     *
     * @param apiIds List of API IDs to check
     * @return List of existence results with exists flag
     * @throws Exception if HTTP call fails
     */
    public List<ApiExistenceResult> checkApisExist(List<ApiId> apiIds) throws Exception {
        String endpoint = baseUrl + "/api/checkApisExist";
        loggerMaker.infoAndAddToDb("Checking existence for " + apiIds.size() + " APIs");

        Map<String, Object> payload = new HashMap<>();
        payload.put("apiIdsToCheck", apiIds);  // Must match server's setter name

        HttpPost request = new HttpPost(endpoint);
        request.setHeader("Authorization", getAuthToken());
        request.setHeader("Content-Type", "application/json");

        String jsonPayload = gson.toJson(payload);
        request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());

            if (statusCode >= 400) {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }

            // Parse JSON: [{"apiCollectionId": 1, "url": "/api/users", "method": "GET", "exists": true}, ...]
            // Response is a direct array, not wrapped in an object
            Type responseType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> resultMaps = gson.fromJson(responseBody, responseType);

            List<ApiExistenceResult> results = new ArrayList<>();
            for (Map<String, Object> map : resultMaps) {
                ApiExistenceResult existenceResult = new ApiExistenceResult();
                existenceResult.setApiCollectionId(((Number) map.get("apiCollectionId")).intValue());
                existenceResult.setUrl((String) map.get("url"));
                existenceResult.setMethod((String) map.get("method"));
                existenceResult.setExists((Boolean) map.get("exists"));
                results.add(existenceResult);
            }

            long existingCount = results.stream().filter(ApiExistenceResult::isExists).count();
            loggerMaker.infoAndAddToDb("Checked " + results.size() + " APIs, " + existingCount + " exist");
            return results;
        }
    }

    /**
     * Bulk write to single_type_info collection via fast-discovery endpoint.
     * Calls: POST /api/fastDiscoveryBulkWriteSti (dedicated fast-discovery endpoint)
     *
     * @param writes List of bulk updates
     * @throws Exception if HTTP call fails
     */
    public void bulkWriteSti(List<BulkUpdates> writes) throws Exception {
        String endpoint = baseUrl + "/api/fastDiscoveryBulkWriteSti";  // Use fast-discovery endpoint
        loggerMaker.infoAndAddToDb("Fast-discovery: Bulk writing " + writes.size() + " entries to single_type_info");

        Map<String, Object> payload = new HashMap<>();
        payload.put("writesForSti", writes);

        executePost(endpoint, payload);
    }

    /**
     * Bulk write to api_info collection via fast-discovery endpoint.
     * Calls: POST /api/fastDiscoveryBulkWriteApiInfo (dedicated fast-discovery endpoint)
     *
     * @param writes List of bulk updates
     * @throws Exception if HTTP call fails
     */
    public void bulkWriteApiInfo(List<BulkUpdates> writes) throws Exception {
        String endpoint = baseUrl + "/api/fastDiscoveryBulkWriteApiInfo";  // Use fast-discovery endpoint
        loggerMaker.infoAndAddToDb("Fast-discovery: Bulk writing " + writes.size() + " entries to api_info");

        Map<String, Object> payload = new HashMap<>();
        payload.put("apiInfoList", convertToApiInfoList(writes));

        executePost(endpoint, payload);
    }

    /**
     * Execute HTTP POST request.
     */
    private void executePost(String endpoint, Object payload) throws Exception {
        HttpPost request = new HttpPost(endpoint);
        request.setHeader("Authorization", getAuthToken());
        request.setHeader("Content-Type", "application/json");

        String jsonPayload = gson.toJson(payload);
        request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 400) {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        }
    }

    /**
     * Convert BulkUpdates to ApiInfo format expected by database-abstractor.
     */
    private List<Map<String, Object>> convertToApiInfoList(List<BulkUpdates> writes) {
        List<Map<String, Object>> apiInfoList = new ArrayList<>();

        for (BulkUpdates write : writes) {
            Map<String, Object> apiInfo = new HashMap<>();
            Map<String, Object> filters = write.getFilters();

            // Extract _id
            if (filters.containsKey("_id")) {
                apiInfo.put("id", filters.get("_id"));
            }

            // Apply updates to create complete ApiInfo object
            for (String updateStr : write.getUpdates()) {
                try {
                    Type updateType = new TypeToken<Map<String, Object>>(){}.getType();
                    Map<String, Object> update = gson.fromJson(updateStr, updateType);
                    String field = (String) update.get("field");
                    Object value = update.get("val");
                    apiInfo.put(field, value);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Failed to parse update: " + updateStr + " - " + e.getMessage());
                }
            }

            apiInfoList.add(apiInfo);
        }

        return apiInfoList;
    }

    /**
     * Close HTTP client and release resources.
     */
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
            loggerMaker.infoAndAddToDb("DatabaseAbstractorClient closed");
        }
    }
}
