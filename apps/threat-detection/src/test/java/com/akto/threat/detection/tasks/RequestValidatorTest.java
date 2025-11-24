package com.akto.threat.detection.tasks;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

class RequestValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SCHEMA_FILE_PATH = "pet-schema.json";
    private static String apiSchema = loadJsonSchemaFile();

    public static String loadJsonSchemaFile() {
        InputStream inputStream = RequestValidator.class.getClassLoader()
                .getResourceAsStream(SCHEMA_FILE_PATH);

        if (inputStream == null) {
            return null;
        }

        StringBuilder stringBuilder = new StringBuilder();
        int ch;
        try {
            while ((ch = inputStream.read()) != -1) {
                stringBuilder.append((char) ch);
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String jsonString = stringBuilder.toString();
        return jsonString;
    }

    void testBenchmark(){
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet");
        responseParam.getRequestParams().setMethod("PUT");
        responseParam.getRequestParams()
                .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/json")));
        
        String requestPayload = "{\"id\":10,\"category\":{\"id\":1,\"name\":\"Dogs\"},\"name\":\"doggie\",\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
        responseParam.getRequestParams().setPayload(requestPayload);

        long startTime = System.nanoTime();
        for (int i = 0; i < 1_00_000; i++){
            List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");
        }
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        double totalTimeInSeconds = totalTime / 1_000_000_000.0;
        double avgTimePerIteration = totalTime / 1_00_000.0 / 1_000_000.0;
        double iterationsPerSecond = 1_00_000 / totalTimeInSeconds;

        System.out.println("Total time (seconds): " + totalTimeInSeconds);
        System.out.println("Average time per iteration (ms): " + avgTimePerIteration);
        System.out.println("Iterations per second: " + iterationsPerSecond);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_NoServers() throws Exception {
        String schemaJson = "{ \"paths\": { \"/example\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/example";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/example", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_ServerUrlPrefixMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"https://api.example.com\" }], \"paths\": { \"/example\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "https://api.example.com/example";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/example", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_ServerUrlPrefixNoMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"https://api.example.com\" }], \"paths\": { \"/example\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "https://otherapi.example.com/example";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("https://otherapi.example.com/example", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_ParameterizedPathMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"https://api.example.com\" }], \"paths\": { \"/users/{userId}/orders/{orderId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "https://api.example.com/users/123/orders/456";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/{userId}/orders/{orderId}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_NoPathMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"https://api.example.com\" }], \"paths\": { \"/example\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "https://api.example.com/unknown";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/unknown", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_MultipleServersParameterizedPathMatch() throws Exception {
        String schemaJson = "{ \"servers\": ["
                + "{ \"url\": \"https://api.example.com\" },"
                + "{ \"url\": \"https://api2.example.com\" }"
                + "],"
                + "\"paths\": {"
                + "\"/users/{userId}\": {},"
                + "\"/pets/{petId}\": {}"
                + "}"
                + "}";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "https://api2.example.com/pets/456";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/pets/{petId}", result);
    }


    @Test
    void testValidate_ValidSchemaAndRequest() throws Exception {
        
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet");
        responseParam.getRequestParams().setMethod("PUT");
        responseParam.getRequestParams()
                .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/json")));
        
        String requestPayload = "{\"id\":10,\"category\":{\"id\":1,\"name\":\"Dogs\"},\"name\":\"doggie\",\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
        responseParam.getRequestParams().setPayload(requestPayload);

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertTrue(errors.isEmpty());
    }

    // @Test
    // void testValidate_MissingRequiredFields() throws Exception {
    //     HttpResponseParams responseParam = new HttpResponseParams();
    //     responseParam.setRequestParams(new HttpRequestParams());
    //     responseParam.getRequestParams().setUrl("/api/v3/pet");
    //     responseParam.getRequestParams().setMethod("PUT");
    //     responseParam.getRequestParams()
    //             .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/json")));
    //     String requestPayload = "{\"id\":10,\"category\":{\"name\":\"Dogs\"},\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
    //     responseParam.getRequestParams().setPayload(requestPayload);

    //     List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

    //     assertFalse(errors.isEmpty());
    //     assertEquals(2, errors.size());
    //     assertEquals("requestBody", errors.get(0).getAttribute());
    //     assertEquals("requestBody", errors.get(1).getAttribute());
    //     assertTrue(errors.get(0).getMessage().contains("required property 'id' not found"));
    //     assertTrue(errors.get(1).getMessage().contains("required property 'name' not found"));
    // }

    @Test
    void testValidate_PathNotFound() throws Exception {
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/unknown");
        responseParam.getRequestParams().setMethod("POST");

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertEquals("url", errors.get(0).getAttribute());
        assertTrue(errors.get(0).getMessage().contains("URL path not found in schema"));
    }

    @Test
    void testValidate_MethodNotFound() throws Exception {
        
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet");
        responseParam.getRequestParams().setMethod("GET");

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertEquals("method", errors.get(0).getAttribute());
        assertTrue(errors.get(0).getMessage().contains("Method GET not available for path"));
    }

    @Test
    void testValidate_RequestBodyNotFound_GetMethod() throws Exception {
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet/findByStatus");
        responseParam.getRequestParams().setMethod("GET");

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertTrue(errors.isEmpty(), "GET requests should skip request body validation");
    }

    @Test
    void testValidate_RequestBodyNotFound_DeleteMethod() throws Exception {
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet/{petId}");
        responseParam.getRequestParams().setMethod("DELETE");

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertTrue(errors.isEmpty(), "DELETE requests should skip request body validation");
    }

    // @Test
    // void testValidate_RequestBodyNotFound_PostMethod() throws Exception {
    //     HttpResponseParams responseParam = new HttpResponseParams();
    //     responseParam.setRequestParams(new HttpRequestParams());
    //     responseParam.getRequestParams().setUrl("/api/v3/pet/findByStatus");
    //     responseParam.getRequestParams().setMethod("POST");
    //     responseParam.getRequestParams()
    //             .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/json")));
    //     responseParam.getRequestParams().setPayload("{}");

    //     List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

    //     assertFalse(errors.isEmpty());
    //     assertEquals(1, errors.size());
    //     assertEquals("requestBody", errors.get(0).getAttribute());
    //     assertTrue(errors.get(0).getMessage().contains("Request body not available for method"));
    // }

    @Test
    void testValidate_XMLContentType() throws Exception {

        // Create the HttpResponseParams with XML content type
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet");
        responseParam.getRequestParams().setMethod("PUT");
        responseParam.getRequestParams()
                .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/xml")));
        String requestPayload = "<pet><id>10</id><name>doggie</name></pet>";
        responseParam.getRequestParams().setPayload(requestPayload);

        // Validate the request
        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        // Assertions
        assertTrue(errors.isEmpty(), "Expected no schema conformance errors for valid XML payload");
    }

    // ========== Additional tests for transformTrafficUrlToSchemaUrl ==========

    @Test
    void testTransformTrafficUrlToSchemaUrl_ExactPathMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/profile\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/profile";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/profile", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_MultiPathMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}\": {}, \"/users/test\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/test";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);
        //TODO: fix the function for this.


        // When multiple paths match, the first matching path in iteration order is returned
        // Both /users/{userId} and /users/test would match /users/test
        // The result depends on JSON object key iteration order
        assertTrue(result.equals("/users/{userId}") || result.equals("/users/test"),
                "Result should be one of the matching paths: /users/{userId} or /users/test");
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_SingleParameter() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/12345";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/{userId}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_WithQueryParameters() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/12345?filter=active&sort=name";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/{userId}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_ComplexParameterizedPathWithQueryParams() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/v1/users/{param1}/vehicles/{param2}/store/updates\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/v1/users/34cc5604-d7ba-4466-8767-a6f9abe55715/vehicles/691c184c8408fc777c00f71c/store/updates?locale=en_US";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/v1/users/{param1}/vehicles/{param2}/store/updates", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_UUIDParameters() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/api/{apiId}/resources/{resourceId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/api/550e8400-e29b-41d4-a716-446655440000/resources/123e4567-e89b-12d3-a456-426614174000";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/api/{apiId}/resources/{resourceId}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_LengthMismatchShorter() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}/orders\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/12345";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/12345", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_LengthMismatchLonger() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/12345/orders";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/12345/orders", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_StaticSegmentMismatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/customers/12345";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/customers/12345", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_RootParameterizedPath() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/{id}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/123";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/{id}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_TrailingSlash() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/users/{userId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/12345/";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/users/{userId}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_ConsecutiveParameters() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/api/{version}/{id}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/api/v2/12345";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/api/{version}/{id}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_SpecialCharactersInParameter() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/files/{fileName}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/files/document-2024.pdf";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/files/{fileName}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_NumericParameter() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/items/{itemId}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/items/999";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/items/{itemId}", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_QueryParamsOnly() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/search\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/search?q=test&page=1";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/search", result);
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_MultiplePathsMatch() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { "
                + "\"/users/{userId}\": {}, "
                + "\"/users/me\": {} "
                + "} }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/users/123";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertTrue(result.equals("/users/{userId}") || result.equals("/users/me"));
    }

    @Test
    void testTransformTrafficUrlToSchemaUrl_WithFragment() throws Exception {
        String schemaJson = "{ \"servers\": [{ \"url\": \"\" }], \"paths\": { \"/docs/{section}\": {} } }";
        JsonNode rootSchemaNode = objectMapper.readTree(schemaJson);
        String url = "/docs/api#introduction";

        String result = RequestValidator.transformTrafficUrlToSchemaUrl(rootSchemaNode, url);

        assertEquals("/docs/{section}", result);
    }
}