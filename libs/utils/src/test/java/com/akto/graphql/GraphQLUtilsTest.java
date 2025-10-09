package com.akto.graphql;

import static org.junit.Assert.*;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

public class GraphQLUtilsTest {

    private GraphQLUtils graphQLUtils;

    @Before
    public void setup() {
        graphQLUtils = GraphQLUtils.getUtils();
    }

    // Test 1: Valid GraphQL request with /query path (your real example)
    @Test
    public void testValidGraphQLWithQueryPath() {
        String payload = "{\n" +
                "  \"operationName\": \"Offers\",\n" +
                "  \"query\": \"query Offers {\\n    offers {\\n        getPromotions(placementIds: \\\"post-cashout\\\") {\\n            promotions {\\n                placement {\\n                    placementId\\n                }\\n            }\\n        }\\n    }\\n}\\n\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull("Result should not be null", result);
        assertFalse("Should parse valid GraphQL request with /query path", result.isEmpty());
        assertTrue("URL should be transformed to GraphQL endpoint path",
                   result.get(0).getRequestParams().getURL().contains("/query/query/Offers/offers"));
    }

    // Test 2: Valid GraphQL request with /graphql path
    @Test
    public void testValidGraphQLWithGraphqlPath() {
        String payload = "{\n" +
                "  \"operationName\": \"GetUser\",\n" +
                "  \"query\": \"query GetUser { user(id: 1) { name email } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/graphql", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertFalse("Should parse valid GraphQL request with /graphql path", result.isEmpty());
    }

    // Test 3: GraphQL mutation
    @Test
    public void testValidGraphQLMutation() {
        String payload = "{\n" +
                "  \"operationName\": \"CreateUser\",\n" +
                "  \"query\": \"mutation CreateUser($name: String!) { createUser(name: $name) { id name } }\",\n" +
                "  \"variables\": {\"name\": \"John\"}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertFalse("Should parse valid GraphQL mutation", result.isEmpty());
        assertTrue("URL should contain mutation operation type",
                   result.get(0).getRequestParams().getURL().contains("/mutation/"));
    }

    // Test 4: GraphQL subscription
    @Test
    public void testValidGraphQLSubscription() {
        String payload = "{\n" +
                "  \"operationName\": \"OnMessage\",\n" +
                "  \"query\": \"subscription OnMessage { messageAdded { content } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertFalse("Should parse valid GraphQL subscription", result.isEmpty());
    }

    // Test 5: Anonymous GraphQL query (no operation name)
    @Test
    public void testAnonymousGraphQLQuery() {
        String payload = "{\n" +
                "  \"operationName\": null,\n" +
                "  \"query\": \"{ user(id: 1) { name } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertFalse("Should parse anonymous GraphQL query starting with {", result.isEmpty());
    }

    // Test 6: REST API with /query path - should NOT parse
    @Test
    public void testRestAPIWithQueryPath() {
        String payload = "{\n" +
                "  \"search\": \"user\",\n" +
                "  \"filters\": {\"active\": true}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/api/query/search", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should NOT parse REST API without 'query' keyword in payload", result.isEmpty());
    }

    // Test 7: REST API with "query" field but not GraphQL - should NOT parse
    @Test
    public void testRestAPIWithQueryFieldNotGraphQL() {
        String payload = "{\n" +
                "  \"query\": \"search term\",\n" +
                "  \"limit\": 10\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/api/query/data", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should NOT parse REST API with 'query' field that's not GraphQL", result.isEmpty());
    }

    // Test 8: Missing operationName field - should NOT parse (strict validation for /query)
    @Test
    public void testMissingOperationName() {
        String payload = "{\n" +
                "  \"query\": \"query GetUser { user(id: 1) { name } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should NOT parse GraphQL without operationName for /query path", result.isEmpty());
    }

    // Test 9: Missing variables field - should NOT parse (strict validation for /query)
    @Test
    public void testMissingVariables() {
        String payload = "{\n" +
                "  \"operationName\": \"GetUser\",\n" +
                "  \"query\": \"query GetUser { user(id: 1) { name } }\"\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should NOT parse GraphQL without variables for /query path", result.isEmpty());
    }

    // Test 10: /graphql path with missing operationName - SHOULD parse (relaxed validation)
    @Test
    public void testGraphqlPathMissingOperationName() {
        String payload = "{\n" +
                "  \"query\": \"query { user(id: 1) { name } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/graphql", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertFalse("Should parse GraphQL with /graphql path even without operationName", result.isEmpty());
    }

    // Test 11: Empty payload
    @Test
    public void testEmptyPayload() {
        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", "");
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should not parse empty payload", result.isEmpty());
    }

    // Test 12: Invalid JSON payload
    @Test
    public void testInvalidJSON() {
        String payload = "{invalid json";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should not parse invalid JSON", result.isEmpty());
    }

    // Test 13: Path without /query or /graphql
    @Test
    public void testPathWithoutQueryOrGraphql() {
        String payload = "{\n" +
                "  \"operationName\": \"GetUser\",\n" +
                "  \"query\": \"query GetUser { user(id: 1) { name } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/api/users", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertTrue("Should not parse when path doesn't contain /query or /graphql", result.isEmpty());
    }

    // Test 14: GraphQL with query parameters in URL
    @Test
    public void testGraphQLWithQueryParams() {
        String payload = "{\n" +
                "  \"operationName\": \"GetUser\",\n" +
                "  \"query\": \"query GetUser { user(id: 1) { name } }\",\n" +
                "  \"variables\": {}\n" +
                "}";

        HttpResponseParams responseParams = createHttpResponseParams("https://api.example.com/query?version=v1&debug=true", payload);
        List<HttpResponseParams> result = graphQLUtils.parseGraphqlResponseParam(responseParams);

        assertNotNull(result);
        assertFalse("Should parse GraphQL with query parameters in URL", result.isEmpty());
        assertTrue("Should preserve query parameters",
                   result.get(0).getRequestParams().getURL().contains("version=v1"));
    }

    // Helper method to create HttpResponseParams
    private HttpResponseParams createHttpResponseParams(String url, String payload) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setUrl(url);
        requestParams.setPayload(payload);
        requestParams.setMethod("POST");

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Arrays.asList("application/json"));
        requestParams.setHeaders(headers);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.requestParams = requestParams;
        responseParams.statusCode = 200;
        responseParams.setPayload("{}"); // Empty response for testing

        return responseParams;
    }
}
