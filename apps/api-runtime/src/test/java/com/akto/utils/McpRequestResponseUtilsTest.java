package com.akto.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.mcp.McpJsonRpcModel;
import com.akto.util.Pair;
import org.junit.jupiter.api.Test;
import java.net.URL;

public class McpRequestResponseUtilsTest {

    private HttpResponseParams createHttpResponseParams(String payload, String url) {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(payload);
        reqParams.setUrl(url);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);

        return responseParams;
    }

    @Test
    public void testMcpToolsCallWithName() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"testTool\" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?sessionId=abc123";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call/testTool"));
        assertEquals("sessionId=abc123", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsCallWithoutName() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {}, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?token=xyz";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call"));
        assertEquals("token=xyz", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsCallWithWhitespaceName() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"   \" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?token=xyz";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call"));
        assertEquals("token=xyz", finalUrl.getQuery());
    }

    @Test
    public void testMcpOtherMethod() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        assertEquals("foo=bar", finalUrl.getQuery());
    }

    @Test
    public void testNonMcpJsonRpcRequest() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"other/method\", \"id\": 1 }";
        String url = "http://localhost:8080/api?x=1";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/other/method"));
        assertEquals("x=1", finalUrl.getQuery());
    }

    @Test
    public void testMalformedJson() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": ";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = null;
        try {
            afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        } catch (Exception e) {
            afterJsonRpc = responseParams;
        }
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        assertEquals(url, afterMcp.getRequestParams().getURL());
    }

    @Test
    public void testNullPayload() {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(null);
        reqParams.setUrl("http://localhost:8080/mcp");
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        assertEquals("http://localhost:8080/mcp", afterMcp.getRequestParams().getURL());
    }

    @Test
    public void testNullRequestParams() {
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(null);
        assertThrows(NullPointerException.class, () -> {
            HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
            McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        });
    }

    @Test
    public void testMcpToolsCallWithParamsAsString() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": \"notAnObject\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call"));
    }

    @Test
    public void testMcpToolsCallWithNameNull() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": null }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call"));
    }

    @Test
    public void testUrlEndsWithSlash() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp/?foo=bar";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        assertEquals("foo=bar", finalUrl.getQuery());
    }

    @Test
    public void testUrlWithEncodedCharacters() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp%20space?foo=bar";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().contains("mcp%20space"));
        assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        assertEquals("foo=bar", finalUrl.getQuery());
    }

    @Test
    public void testIsMcpRequestWithValidMcpMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        Pair<Boolean, McpJsonRpcModel> result = McpRequestResponseUtils.isMcpRequest(responseParams);
        assertTrue(result.getFirst());
        assertNotNull(result.getSecond());
        assertEquals("tools/list", result.getSecond().getMethod());
    }

    @Test
    public void testIsMcpRequestWithInvalidMcpMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"notamcp/method\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        Pair<Boolean, McpJsonRpcModel> result = McpRequestResponseUtils.isMcpRequest(responseParams);
        assertFalse(result.getFirst());
    }

    @Test
    public void testMcpToolsCallWithNameAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"testTool\" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call/testTool"));
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsCallWithoutNameAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {}, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/call"));
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testMcpOtherMethodWithQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testNonMcpJsonRpcRequestWithQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"other/method\", \"id\": 1 }";
        String url = "http://localhost:8080/api?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().endsWith("/other/method"));
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testMalformedJsonWithQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": "; // malformed
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = null;
        try {
            afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        } catch (Exception e) {
            afterJsonRpc = responseParams;
        }
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testUrlWithEncodedCharactersAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp%20space?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        HttpResponseParams afterMcp = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        assertTrue(finalUrl.getPath().contains("mcp%20space"));
        assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }
}