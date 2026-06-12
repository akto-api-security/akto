package com.akto.wiz;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WizApiClient {

    static final int HOST_FETCH_PAGE_SIZE = 500;
    static final int ENDPOINT_FETCH_PAGE_SIZE = 20;

    private static Map<String, List<String>> buildHeaders(String accessToken) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + accessToken));
        headers.put("User-Agent", Collections.singletonList(WizIntegrationUtils.USER_AGENT));
        return headers;
    }

    /**
     * Paginates apiEndpoints fetching only the host field, returns the full set of unique hosts.
     */
    public static Set<String> fetchAllHosts(String apiUrl, String accessToken) throws Exception {
        Set<String> hosts = new HashSet<>();
        String cursor = null;
        boolean hasNextPage = true;
        Map<String, List<String>> headers = buildHeaders(accessToken);

        while (hasNextPage) {
            String variablesPart = cursor == null
                ? String.format("{\"first\":%d}", HOST_FETCH_PAGE_SIZE)
                : String.format("{\"first\":%d,\"after\":\"%s\"}", HOST_FETCH_PAGE_SIZE, cursor);

            String graphqlQuery = "{\"query\":\"query($first: Int, $after: String) { apiEndpoints(first: $first, after: $after) { nodes { host } pageInfo { endCursor hasNextPage } } }\",\"variables\":" + variablesPart + "}";

            BasicDBObject root = executeAndGetRoot(apiUrl, graphqlQuery, headers, "apiEndpoints");

            List<?> nodes = (List<?>) root.get("nodes");
            BasicDBObject pageInfo = (BasicDBObject) root.get("pageInfo");

            for (Object nodeObj : nodes) {
                String host = ((BasicDBObject) nodeObj).getString("host");
                if (host != null && !host.isEmpty()) {
                    hosts.add(host);
                }
            }

            hasNextPage = pageInfo.getBoolean("hasNextPage", false);
            cursor = pageInfo.getString("endCursor");
        }

        return hosts;
    }

    /**
     * Fetches one page of full endpoint details for a specific host.
     * Returns the apiEndpoints root object containing "nodes" and "pageInfo".
     * Throws on non-200 or null response.
     */
    public static BasicDBObject fetchEndpointsPage(String apiUrl, String accessToken, String host, String cursor) throws Exception {
        String escapedHost = host.replace("\\", "\\\\").replace("\"", "\\\"");
        String variablesPart = cursor == null
            ? String.format("{\"first\":%d,\"fetchTotalCount\":true,\"filterBy\":{\"host\":{\"equals\":[\"%s\"]}}}", ENDPOINT_FETCH_PAGE_SIZE, escapedHost)
            : String.format("{\"first\":%d,\"after\":\"%s\",\"fetchTotalCount\":false,\"filterBy\":{\"host\":{\"equals\":[\"%s\"]}}}", ENDPOINT_FETCH_PAGE_SIZE, cursor, escapedHost);

        String graphqlQuery = "{\"query\":\"query APIEndpointsTable($first: Int, $after: String, $fetchTotalCount: Boolean = true, $filterBy: APIEndpointFilters) { apiEndpoints(first: $first, after: $after, filterBy: $filterBy) { nodes { id host source authSchemes isAiGenerated createdAt updatedAt ... on HTTPRestAPIEndpoint { httpMethod pathname specification { string } } ... on HTTPGraphqlAPIEndpoint { operationType operationName } ... on HTTPGrpcAPIEndpoint { methodName } ... on HTTPSoapAPIEndpoint { methodName } ... on HTTPMcpAPIEndpoint { method name } } pageInfo { endCursor hasNextPage } totalCount @include(if: $fetchTotalCount) } }\",\"variables\":" + variablesPart + "}";

        return executeAndGetRoot(apiUrl, graphqlQuery, buildHeaders(accessToken), "apiEndpoints");
    }

    private static BasicDBObject executeAndGetRoot(String apiUrl, String graphqlQuery, Map<String, List<String>> headers, String rootKey) throws Exception {
        OriginalHttpRequest request = new OriginalHttpRequest(apiUrl, "", "POST", graphqlQuery, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), WizIntegrationUtils.isWizDevMode());

        if (response == null || response.getStatusCode() != 200 || response.getBody() == null) {
            throw new Exception(String.format("Wiz API call failed. Status: %d",
                response != null ? response.getStatusCode() : -1));
        }

        BasicDBObject responseObj = BasicDBObject.parse(response.getBody());
        return (BasicDBObject) ((BasicDBObject) responseObj.get("data")).get(rootKey);
    }
}
