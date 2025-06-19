package com.akto.jsonrpc;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;

public class JsonRpcUtilsTest {

    private HttpResponseParams createHttpResponseParams(String payload, String url) {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(payload);
        reqParams.setUrl(url);
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
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
    public void testNullPayload() {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(null);
        reqParams.setUrl("http://localhost:8080/api");
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        HttpResponseParams resp = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        assertEquals("http://localhost:8080/api", resp.getRequestParams().getURL());
    }

    @Test
    public void testNullRequestParams() {
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(null);
        assertThrows(NullPointerException.class, () -> JsonRpcUtils.parseJsonRpcResponse(responseParams));
    }

    @Test
    public void testIsJsonRpcRequestTrue() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"foo\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        assertTrue(JsonRpcUtils.isJsonRpcRequest(responseParams));
    }

    @Test
    public void testIsJsonRpcRequestFalse() {
        String payload = "{ \"foo\": \"bar\" }";
        HttpResponseParams responseParams = createHttpResponseParams(payload, "http://localhost:8080/api");
        assertFalse(JsonRpcUtils.isJsonRpcRequest(responseParams));
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
} 