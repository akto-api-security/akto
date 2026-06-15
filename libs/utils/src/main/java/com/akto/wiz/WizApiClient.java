package com.akto.wiz;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WizApiClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(WizApiClient.class, LogDb.DASHBOARD);

    static final int HOST_FETCH_PAGE_SIZE = 500;
    static final int ENDPOINT_FETCH_PAGE_SIZE = 20;
    static final int ENDPOINT_META_FETCH_PAGE_SIZE = 500;

    static Map<String, List<String>> buildHeaders(String accessToken) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + accessToken));
        headers.put("User-Agent", Collections.singletonList(WizIntegrationUtils.USER_AGENT));
        return headers;
    }

    /**
     * Paginates apiEndpoints fetching only id and updatedAt, returns the full list.
     */
    public static List<BasicDBObject> fetchAllEndpointMeta(String apiUrl, String accessToken) throws Exception {
        List<BasicDBObject> results = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        Map<String, List<String>> headers = buildHeaders(accessToken);

        while (hasNextPage) {
            String variablesPart = cursor == null
                ? String.format("{\"first\":%d}", ENDPOINT_META_FETCH_PAGE_SIZE)
                : String.format("{\"first\":%d,\"after\":\"%s\"}", ENDPOINT_META_FETCH_PAGE_SIZE, cursor);

            String graphqlQuery = "{\"query\":\"query($first: Int, $after: String) { apiEndpoints(first: $first, after: $after) { nodes { id updatedAt } pageInfo { endCursor hasNextPage } } }\",\"variables\":" + variablesPart + "}";

            loggerMaker.infoAndAddToDb(String.format("fetchAllEndpointMeta: page cursor=%s", cursor));
            BasicDBObject root = executeAndGetRoot(apiUrl, graphqlQuery, headers, "apiEndpoints");

            List<?> nodes = (List<?>) root.get("nodes");
            BasicDBObject pageInfo = (BasicDBObject) root.get("pageInfo");

            for (Object nodeObj : nodes) {
                results.add((BasicDBObject) nodeObj);
            }

            hasNextPage = pageInfo.getBoolean("hasNextPage", false);
            cursor = pageInfo.getString("endCursor");
        }

        return results;
    }

    /**
     * Fetches one page of full endpoint details filtered by a set of endpoint IDs.
     * Returns the apiEndpoints root object containing "nodes" and "pageInfo".
     */
    public static BasicDBObject fetchEndpointsPageByIds(String apiUrl, String accessToken, List<String> ids) throws Exception {
        StringBuilder idsJson = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) idsJson.append(",");
            idsJson.append("\"").append(ids.get(i).replace("\"", "\\\"")).append("\"");
        }
        idsJson.append("]");

        String variablesPart = String.format("{\"first\":%d,\"filterBy\":{\"id\":{\"equals\":%s}}}", ENDPOINT_FETCH_PAGE_SIZE, idsJson);

        String graphqlQuery = "{\"query\":\"query APIEndpointsTable($first: Int, $filterBy: APIEndpointFilters) { apiEndpoints(first: $first, filterBy: $filterBy) { nodes { id host source authSchemes isAiGenerated createdAt updatedAt ... on HTTPRestAPIEndpoint { httpMethod pathname specification { string } } ... on HTTPGraphqlAPIEndpoint { operationType operationName } ... on HTTPGrpcAPIEndpoint { methodName } ... on HTTPSoapAPIEndpoint { methodName } ... on HTTPMcpAPIEndpoint { method name } } } }\",\"variables\":" + variablesPart + "}";

        loggerMaker.infoAndAddToDb(String.format("fetchEndpointsPageByIds: %d ids", ids.size()));
        return executeAndGetRoot(apiUrl, graphqlQuery, buildHeaders(accessToken), "apiEndpoints");
    }

    static BasicDBObject executeRaw(String apiUrl, String graphqlQuery, Map<String, List<String>> headers) throws Exception {
        OriginalHttpRequest request = new OriginalHttpRequest(apiUrl, "", "POST", graphqlQuery, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), WizIntegrationUtils.isWizDevMode());

        if (response == null || response.getStatusCode() != 200 || response.getBody() == null) {
            throw new Exception(String.format("Wiz API call failed. Status: %d",
                response != null ? response.getStatusCode() : -1));
        }

        return BasicDBObject.parse(response.getBody());
    }

    private static BasicDBObject executeAndGetRoot(String apiUrl, String graphqlQuery, Map<String, List<String>> headers, String rootKey) throws Exception {
        OriginalHttpRequest request = new OriginalHttpRequest(apiUrl, "", "POST", graphqlQuery, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), WizIntegrationUtils.isWizDevMode());

        if (response == null || response.getStatusCode() != 200 || response.getBody() == null) {
            loggerMaker.errorAndAddToDb(String.format("Wiz API call failed. Status: %d", response != null ? response.getStatusCode() : -1));
            throw new Exception(String.format("Wiz API call failed. Status: %d",
                response != null ? response.getStatusCode() : -1));
        }
        loggerMaker.infoAndAddToDb(String.format("Response status: %d", response.getStatusCode()));

        BasicDBObject responseObj = BasicDBObject.parse(response.getBody());
        return (BasicDBObject) ((BasicDBObject) responseObj.get("data")).get(rootKey);
    }
}
