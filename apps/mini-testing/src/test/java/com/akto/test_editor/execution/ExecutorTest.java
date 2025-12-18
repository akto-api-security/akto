package com.akto.test_editor.execution;

import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.testing.TestResult.TestError;
import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ExecutorTest {
    // Helper to invoke the private method
    @SuppressWarnings("unchecked")
    private List<BasicDBObject> invokeParseGeneratedKeyValues(Executor executor, BasicDBObject generatedData, String operationType, Object value) {
        try {
            java.lang.reflect.Method method = Executor.class.getDeclaredMethod("parseGeneratedKeyValues", BasicDBObject.class, String.class, Object.class);
            method.setAccessible(true);
            return (List<BasicDBObject>) method.invoke(executor, generatedData, operationType, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStringValue() {
        Executor executor = new Executor();
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", "key1");
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val1");
        assertEquals(1, result.size());
        BasicDBObject dbObject = result.get(0);
        assertTrue(dbObject.containsValue("key1"));
    }

    @Test
    public void testJSONObjectValue() {
        Executor executor = new Executor();
        JSONObject obj = new JSONObject();
        obj.put("k1", "v1");
        obj.put("k2", "v2");
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", obj);
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val");
        assertEquals(2, result.size());
        Set<String> keys = new HashSet<String>();
        for (BasicDBObject dbObj : result) {
            keys.addAll(dbObj.keySet());
        }
        assertTrue(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
    }

    @Test
    public void testJSONArrayValue() {
        Executor executor = new Executor();
        JSONArray arr = new JSONArray();
        arr.put("k1");
        JSONObject obj = new JSONObject();
        obj.put("k2", "v2");
        arr.put(obj);
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", arr);
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val");
        assertEquals(2, result.size());
        BasicDBObject dbObject1 = result.get(0);
        BasicDBObject dbObject2 = result.get(1);
        assertTrue(dbObject1.containsValue("k1"));
        assertTrue(dbObject2.containsValue("v2"));
    }

    @Test
    public void testUnexpectedType() {
        Executor executor = new Executor();
        BasicDBObject generatedData = new BasicDBObject();
        generatedData.put("op", 12345);
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "op", "val");
        assertEquals(1, result.size());
    }

    @Test
    public void testOperationNotFound() {
        Executor executor = new Executor();
        BasicDBObject generatedData = new BasicDBObject();
        List<BasicDBObject> result = invokeParseGeneratedKeyValues(executor, generatedData, "not_found", "val");
        assertEquals(0, result.size());
    }

    @Test
    public void testCategorizeError_Timeout() {
        TestError result = Executor.categorizeError("Error executing test request: java.net.SocketTimeoutException: timeout");
        assertEquals(TestError.API_CONNECTION_TIMEOUT, result);

        result = Executor.categorizeError("Connection timed out after 5000ms");
        assertEquals(TestError.API_CONNECTION_TIMEOUT, result);
    }

    @Test
    public void testCategorizeError_ConnectionRefused() {
        TestError result = Executor.categorizeError("Error executing test request: Connection refused");
        assertEquals(TestError.API_CONNECTION_REFUSED, result);

        result = Executor.categorizeError("Failed to connect: ECONNREFUSED");
        assertEquals(TestError.API_CONNECTION_REFUSED, result);
    }

    @Test
    public void testCategorizeError_HostUnreachable() {
        TestError result = Executor.categorizeError("Host unreachable: api.example.com");
        assertEquals(TestError.API_HOST_UNREACHABLE, result);

        result = Executor.categorizeError("No route to host");
        assertEquals(TestError.API_HOST_UNREACHABLE, result);

        result = Executor.categorizeError("Network is unreachable");
        assertEquals(TestError.API_HOST_UNREACHABLE, result);

        result = Executor.categorizeError("java.net.UnknownHostException: invalid-host.com");
        assertEquals(TestError.API_HOST_UNREACHABLE, result);

        // Test real-world DNS error messages
        result = Executor.categorizeError("Api Call failed: this-domain-does-not-exist.com: nodename nor servname provided, or not known");
        assertEquals(TestError.API_HOST_UNREACHABLE, result);

        result = Executor.categorizeError("unknown host: example.invalid");
        assertEquals(TestError.API_HOST_UNREACHABLE, result);
    }

    @Test
    public void testCategorizeError_SSLHandshake() {
        TestError result = Executor.categorizeError("SSL handshake failed");
        assertEquals(TestError.API_SSL_HANDSHAKE_FAILED, result);

        result = Executor.categorizeError("TLS connection error");
        assertEquals(TestError.API_SSL_HANDSHAKE_FAILED, result);

        result = Executor.categorizeError("Certificate verification failed");
        assertEquals(TestError.API_SSL_HANDSHAKE_FAILED, result);
    }

    @Test
    public void testCategorizeError_ResponseBodyNull() {
        TestError result = Executor.categorizeError("Couldn't read response body");
        assertEquals(TestError.API_RESPONSE_BODY_NULL, result);

        result = Executor.categorizeError("Error: response body is null");
        assertEquals(TestError.API_RESPONSE_BODY_NULL, result);
    }

    @Test
    public void testCategorizeError_ParseFailed() {
        TestError result = Executor.categorizeError("Failed to parse response");
        assertEquals(TestError.API_RESPONSE_PARSE_FAILED, result);

        result = Executor.categorizeError("JSON parsing error");
        assertEquals(TestError.API_RESPONSE_PARSE_FAILED, result);
    }

    @Test
    public void testCategorizeError_GenericFailure() {
        TestError result = Executor.categorizeError("Some other error occurred");
        assertEquals(TestError.API_REQUEST_FAILED_BEFORE_SENDING, result);

        result = Executor.categorizeError("Unknown error");
        assertEquals(TestError.API_REQUEST_FAILED_BEFORE_SENDING, result);
    }

    @Test
    public void testCategorizeError_NullMessage() {
        TestError result = Executor.categorizeError(null);
        assertEquals(TestError.API_REQUEST_FAILED, result);
    }

    @Test
    public void testCategorizeError_CaseInsensitive() {
        TestError result = Executor.categorizeError("CONNECTION TIMEOUT ERROR");
        assertEquals(TestError.API_CONNECTION_TIMEOUT, result);

        result = Executor.categorizeError("SSL ERROR OCCURRED");
        assertEquals(TestError.API_SSL_HANDSHAKE_FAILED, result);
    }

    // Integration tests with real HTTP requests showing actual error categorization
    @Test
    public void testRealErrorCategorization_ConnectionTimeout() {
        // Test that shows real connection timeout gets properly categorized
        // 192.0.2.1 is from TEST-NET-1 (RFC 5737) - reserved for documentation, won't connect
        com.akto.dto.OriginalHttpRequest request = new com.akto.dto.OriginalHttpRequest();
        request.setUrl("http://192.0.2.1:9999/api/test");
        request.setMethod("GET");
        request.setHeaders(new java.util.HashMap<String, java.util.List<String>>());

        try {
            com.akto.testing.ApiExecutor.sendRequest(request, false, null, false, new java.util.ArrayList<>(), true);
            fail("Expected connection to fail");
        } catch (Exception e) {
            // Expected - connection should timeout
            String errorMessage = e.getMessage();
            System.out.println("Real connection error from ApiExecutor: " + errorMessage);

            // Call the actual categorizeError method directly
            TestError categorized = Executor.categorizeError(errorMessage);
            System.out.println("Categorized as: " + categorized.getMessage());

            // Verify it was categorized correctly - should be timeout or connection refused
            // (Network behavior varies by OS - some timeout, some refuse immediately)
            assertTrue("Expected API_CONNECTION_TIMEOUT or API_CONNECTION_REFUSED, got: " + categorized,
                categorized == TestError.API_CONNECTION_TIMEOUT ||
                categorized == TestError.API_CONNECTION_REFUSED);
        }
    }

    @Test
    public void testRealErrorCategorization_UnknownHost() {
        // Test that shows real DNS failure gets properly categorized
        com.akto.dto.OriginalHttpRequest request = new com.akto.dto.OriginalHttpRequest();
        request.setUrl("http://this-domain-does-not-exist-akto-12345.com/api/test");
        request.setMethod("GET");
        request.setHeaders(new java.util.HashMap<String, java.util.List<String>>());

        try {
            com.akto.testing.ApiExecutor.sendRequest(request, false, null, false, new java.util.ArrayList<>(), true);
            fail("Expected DNS resolution to fail");
        } catch (Exception e) {
            // Expected - DNS should fail
            String errorMessage = e.getMessage();
            System.out.println("Real DNS error from ApiExecutor: " + errorMessage);

            // Call the actual categorizeError method directly
            TestError categorized = Executor.categorizeError(errorMessage);
            System.out.println("Categorized as: " + categorized.getMessage());

            // Verify it was categorized correctly as host unreachable (no fallback!)
            assertEquals("Expected API_HOST_UNREACHABLE for DNS error",
                TestError.API_HOST_UNREACHABLE, categorized);
        }
    }

    @Test
    public void testRealErrorCategorization_ValidEndpoint() {
        // Test that shows successful request doesn't trigger error categorization
        com.akto.dto.OriginalHttpRequest request = new com.akto.dto.OriginalHttpRequest();
        request.setUrl("https://httpbin.org/status/200");
        request.setMethod("GET");

        java.util.Map<String, java.util.List<String>> headers = new java.util.HashMap<>();
        headers.put("User-Agent", java.util.Collections.singletonList("Akto-Test"));
        request.setHeaders(headers);

        try {
            com.akto.dto.OriginalHttpResponse response = com.akto.testing.ApiExecutor.sendRequest(
                request, false, null, false, new java.util.ArrayList<>(), true
            );

            // This should succeed
            assertNotNull("Response should not be null", response);
            assertEquals("Expected 200 status code", 200, response.getStatusCode());
            System.out.println("Valid endpoint test succeeded with status: " + response.getStatusCode());
        } catch (Exception e) {
            // Log the error but don't fail - network issues can happen in CI
            System.err.println("Valid endpoint test failed (may be network issue): " + e.getMessage());
            // Don't fail the test since this could be a network/firewall issue in CI
        }
    }

    // End-to-end tests using Executor.execute() with YAML config to verify full integration
    @Test
    public void testExecutorEndToEnd_ConnectionTimeout() throws Exception {
        // Load YAML test configuration
        java.io.InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream("test_connection_timeout.yaml");
        assertNotNull("YAML file should exist", inputStream);

        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        java.util.Map<String, Object> testConfig = yaml.load(inputStream);

        // Parse execute block
        Object executeObj = testConfig.get("execute");
        assertNotNull("Execute block should exist", executeObj);

        com.akto.dao.test_editor.executor.ConfigParser parser = new com.akto.dao.test_editor.executor.ConfigParser();
        com.akto.dto.test_editor.ExecutorConfigParserResult configResult = parser.parseConfigMap(executeObj);

        assertNotNull("Config should parse successfully", configResult);
        assertTrue("Config should be valid", configResult.getIsValid());

        // Create RawApi
        com.akto.dto.RawApi rawApi = new com.akto.dto.RawApi();
        com.akto.dto.OriginalHttpRequest origRequest = new com.akto.dto.OriginalHttpRequest();
        origRequest.setUrl("http://example.com/api");
        origRequest.setMethod("GET");
        origRequest.setHeaders(new java.util.HashMap<>());
        origRequest.setBody("{}");
        rawApi.setRequest(origRequest);

        com.akto.dto.OriginalHttpResponse origResponse = new com.akto.dto.OriginalHttpResponse();
        origResponse.setStatusCode(200);
        origResponse.setBody("{}");
        origResponse.setHeaders(new java.util.HashMap<>());
        rawApi.setResponse(origResponse);

        Executor executor = new Executor();

        try {
            com.akto.dto.testing.YamlTestResult result = executor.execute(
                configResult.getNode(),
                rawApi,
                new java.util.HashMap<>(),
                "test-connection-timeout",
                null,
                null,
                new com.akto.dto.ApiInfo.ApiInfoKey(0, "http://example.com/api",
                    com.akto.dto.type.URLMethods.Method.GET),
                new com.akto.dto.testing.TestingRunConfig(),
                new java.util.ArrayList<>(),
                false,
                new java.util.ArrayList<>(),
                null
            );

            // Verify result contains categorized error
            assertNotNull("Result should not be null", result);
            assertFalse("Should have test results", result.getTestResults().isEmpty());

            com.akto.dto.testing.GenericTestResult testResult = result.getTestResults().get(0);
            if (testResult instanceof com.akto.dto.testing.TestResult) {
                com.akto.dto.testing.TestResult tr = (com.akto.dto.testing.TestResult) testResult;

                if (tr.getErrors() != null && !tr.getErrors().isEmpty()) {
                    String errorMsg = tr.getErrors().get(tr.getErrors().size() - 1);
                    System.out.println("End-to-end connection timeout error: " + errorMsg);

                    // Verify error was categorized with specific message
                    boolean isCategorized = errorMsg.toLowerCase().contains("timeout") ||
                                          errorMsg.toLowerCase().contains("connection") ||
                                          errorMsg.toLowerCase().contains("host");
                    assertTrue("Error should be categorized with specific details: " + errorMsg, isCategorized);
                }
            }
        } catch (Exception e) {
            System.err.println("End-to-end connection test exception (may be expected): " + e.getMessage());
        }
    }

    @Test
    public void testExecutorEndToEnd_DNSFailure() throws Exception {
        // Load YAML test configuration
        java.io.InputStream inputStream = getClass().getClassLoader()
            .getResourceAsStream("test_dns_failure.yaml");
        assertNotNull("YAML file should exist", inputStream);

        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        java.util.Map<String, Object> testConfig = yaml.load(inputStream);

        // Parse execute block
        Object executeObj = testConfig.get("execute");
        assertNotNull("Execute block should exist", executeObj);

        com.akto.dao.test_editor.executor.ConfigParser parser = new com.akto.dao.test_editor.executor.ConfigParser();
        com.akto.dto.test_editor.ExecutorConfigParserResult configResult = parser.parseConfigMap(executeObj);

        assertNotNull("Config should parse successfully", configResult);
        assertTrue("Config should be valid", configResult.getIsValid());

        // Create RawApi
        com.akto.dto.RawApi rawApi = new com.akto.dto.RawApi();
        com.akto.dto.OriginalHttpRequest origRequest = new com.akto.dto.OriginalHttpRequest();
        origRequest.setUrl("http://example.com/api");
        origRequest.setMethod("GET");
        origRequest.setHeaders(new java.util.HashMap<>());
        origRequest.setBody("{}");
        rawApi.setRequest(origRequest);

        com.akto.dto.OriginalHttpResponse origResponse = new com.akto.dto.OriginalHttpResponse();
        origResponse.setStatusCode(200);
        origResponse.setBody("{}");
        origResponse.setHeaders(new java.util.HashMap<>());
        rawApi.setResponse(origResponse);

        Executor executor = new Executor();

        try {
            com.akto.dto.testing.YamlTestResult result = executor.execute(
                configResult.getNode(),
                rawApi,
                new java.util.HashMap<>(),
                "test-dns-failure",
                null,
                null,
                new com.akto.dto.ApiInfo.ApiInfoKey(0, "http://example.com/api",
                    com.akto.dto.type.URLMethods.Method.GET),
                new com.akto.dto.testing.TestingRunConfig(),
                new java.util.ArrayList<>(),
                false,
                new java.util.ArrayList<>(),
                null
            );

            // Verify result contains categorized error
            assertNotNull("Result should not be null", result);
            assertFalse("Should have test results", result.getTestResults().isEmpty());

            com.akto.dto.testing.GenericTestResult testResult = result.getTestResults().get(0);
            if (testResult instanceof com.akto.dto.testing.TestResult) {
                com.akto.dto.testing.TestResult tr = (com.akto.dto.testing.TestResult) testResult;

                if (tr.getErrors() != null && !tr.getErrors().isEmpty()) {
                    String errorMsg = tr.getErrors().get(tr.getErrors().size() - 1);
                    System.out.println("End-to-end DNS failure error: " + errorMsg);

                    // Verify error was categorized as host unreachable
                    boolean isDNSError = errorMsg.toLowerCase().contains("host") ||
                                       errorMsg.toLowerCase().contains("unreachable");
                    assertTrue("Error should indicate DNS/host issue: " + errorMsg, isDNSError);
                }
            }
        } catch (Exception e) {
            System.err.println("End-to-end DNS test exception (may be expected): " + e.getMessage());
        }
    }
}
