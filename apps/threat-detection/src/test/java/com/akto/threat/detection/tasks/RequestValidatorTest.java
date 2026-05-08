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

    @Test
    void testValidate_MissingRequiredFields() throws Exception {
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet");
        responseParam.getRequestParams().setMethod("PUT");
        responseParam.getRequestParams()
                .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/json")));
        String requestPayload = "{\"id\":10,\"category\":{\"name\":\"Dogs\"},\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
        responseParam.getRequestParams().setPayload(requestPayload);

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertFalse(errors.isEmpty());
        assertEquals(2, errors.size());
        assertEquals("requestBody", errors.get(0).getAttribute());
        assertEquals("requestBody", errors.get(1).getAttribute());
        assertTrue(errors.get(0).getMessage().contains("required property 'id' not found"));
        assertTrue(errors.get(1).getMessage().contains("required property 'name' not found"));
    }

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

    @Test
    void testValidate_RequestBodyNotFound_PostMethod() throws Exception {
        HttpResponseParams responseParam = new HttpResponseParams();
        responseParam.setRequestParams(new HttpRequestParams());
        responseParam.getRequestParams().setUrl("/api/v3/pet/findByStatus");
        responseParam.getRequestParams().setMethod("POST");
        responseParam.getRequestParams()
                .setHeaders(Collections.singletonMap("content-type", Collections.singletonList("application/json")));
        responseParam.getRequestParams().setPayload("{}");

        List<SchemaConformanceError> errors = RequestValidator.validate(responseParam, apiSchema, "testApiInfoKey");

        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertEquals("requestBody", errors.get(0).getAttribute());
        assertTrue(errors.get(0).getMessage().contains("Request body not available for method"));
    }

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
}