package com.akto.jsonrpc;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import java.util.Map;
import com.akto.util.Pair;

public class JsonRpcUtilsTest {

    private HttpResponseParams createHttpResponseParams(String payload, String url) {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(payload);
        reqParams.setUrl(url);
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        responseParams.statusCode = 200;
        return responseParams;
    }

    @Test
    public void testValidJsonRpcRequestWithMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"testMethod\", \"id\": 1 }";
        String url = "http://localhost:8080/api";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        assertTrue(result.getRequestParams().getURL().contains("testMethod"));
    }

    @Test
    public void testValidJsonRpcRequestWithEmptyMethod() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"\", \"id\": 1 }";
        String url = "http://localhost:8080/api";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        URL originalUrl = new URL(url);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        assertEquals(originalUrl, finalUrl);
    }

    @Test
    public void testValidJsonRpcRequestWithNoMethodKey() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"id\": 1 }";
        String url = "http://localhost:8080/api";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        URL originalUrl = new URL(url);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        assertEquals(originalUrl, finalUrl);
    }

    @Test
    public void testNonJsonRpcRequest() throws Exception {
        String payload = "{ \"foo\": \"bar\" }";
        String url = "http://localhost:8080/api";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        URL originalUrl = new URL(url);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        assertEquals(originalUrl, finalUrl);
    }

    @Test
    public void testJsonRpcRequestWithMethodAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"testMethod\", \"id\": 1 }";
        String url = "http://localhost:8080/api?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().contains("testMethod"));
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testJsonRpcRequestWithEmptyMethodAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"\", \"id\": 1 }";
        String url = "http://localhost:8080/api?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        URL originalUrl = new URL(url);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        assertEquals(originalUrl.getPath(), finalUrl.getPath());
        assertEquals(originalUrl.getQuery(), finalUrl.getQuery());
    }

    @Test
    public void testNonJsonRpcRequestWithQueryParams() throws Exception {
        String payload = "{ \"foo\": \"bar\" }";
        String url = "http://localhost:8080/api?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams result = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        URL originalUrl = new URL(url);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        assertEquals(originalUrl.getPath(), finalUrl.getPath());
        assertEquals(originalUrl.getQuery(), finalUrl.getQuery());
    }

    @Test
    public void testMissingJsonRpcKey() {
        String payload = "{ \"foo\": \"bar\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 200;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testWrongJsonRpcVersion() {
        String payload = "{ \"jsonrpc\": \"1.0\", \"method\": \"foo\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 200;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNotNull(result.getSecond());
    }

    @Test
    public void testEmptyOrInvalidMap() {
        String payload = "";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 200;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testStatusCodeOutOfRange() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"foo\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 199;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNotNull(result.getSecond());
        responseParams.statusCode = 300;
        result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNotNull(result.getSecond());
    }

    @Test
    public void testValidJsonRpc() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"foo\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 200;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertTrue(result.getFirst());
        assertNotNull(result.getSecond());
    }

    @Test
    public void testEmptyOrMissingMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 200;
        HttpResponseParams resultParams = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        assertEquals(responseParams.getRequestParams().getURL(), resultParams.getRequestParams().getURL());

        payload = "{ \"jsonrpc\": \"2.0\" }";
        responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 200;
        resultParams = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        assertEquals(responseParams.getRequestParams().getURL(), resultParams.getRequestParams().getURL());
    }

    @Test
    public void testNullRequestPayload() {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(null);
        reqParams.setUrl("http://localhost:8080/api");
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        responseParams.statusCode = 200;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNull(result.getSecond());
    }

    @Test
    public void testStatusCodeGreaterThan400() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"foo\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        responseParams.statusCode = 401;
        Pair<Boolean, Map<String, Object>> result = JsonRpcUtils.validateAndParseJsonRpc(responseParams);
        assertFalse(result.getFirst());
        assertNotNull(result.getSecond());
    }
} 