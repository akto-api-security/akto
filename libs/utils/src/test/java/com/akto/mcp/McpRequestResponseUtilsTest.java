package com.akto.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.jsonrpc.JsonRpcUtils;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.util.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URL;

public class McpRequestResponseUtilsTest {

    private HttpResponseParams createHttpResponseParams(String payload, String url) {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(payload);
        reqParams.setUrl(url);
        reqParams.setApiCollectionId(123456789);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        responseParams.statusCode = 200;

        return responseParams;
    }

    @Test
    public void testMcpToolsCallWithName() throws Exception {
        // Mock DataActor to avoid NullPointerException during audit log insertion
        com.akto.data_actor.DataActor mockDataActor = Mockito.mock(com.akto.data_actor.DataActor.class);
        Mockito.doNothing().when(mockDataActor).insertMCPAuditDataLog(Mockito.any());
        java.lang.reflect.Field field = com.akto.mcp.McpRequestResponseUtils.class.getDeclaredField("dataActor");
        field.setAccessible(true);
        field.set(null, mockDataActor);

        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"testTool\" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?sessionId=abc123";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call/testTool"));
        Assertions.assertEquals("sessionId=abc123", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsCallWithoutName() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {}, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?token=xyz";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call"));
        Assertions.assertEquals("token=xyz", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsCallWithWhitespaceName() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"   \" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?token=xyz";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call"));
        Assertions.assertEquals("token=xyz", finalUrl.getQuery());
    }

    @Test
    public void testMcpOtherMethod() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        Assertions.assertEquals("foo=bar", finalUrl.getQuery());
    }

    @Test
    public void testNonMcpJsonRpcRequest() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"other/method\", \"id\": 1 }";
        String url = "http://localhost:8080/api?x=1";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/other/method"));
        Assertions.assertEquals("x=1", finalUrl.getQuery());
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
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        Assertions.assertEquals(url, afterMcp.getRequestParams().getURL());
    }

    @Test
    public void testNullPayload() {
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.setPayload(null);
        String url = "http://localhost:8080/mcp";
        reqParams.setUrl(url);
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(reqParams);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        Assertions.assertEquals(url, afterMcp.getRequestParams().getURL());
    }

    @Test
    public void testNullRequestParams() {
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(null);
        Assertions.assertThrows(NullPointerException.class, () -> {
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
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call"));
    }

    @Test
    public void testMcpToolsCallWithNameNull() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": null }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call"));
    }

    @Test
    public void testUrlEndsWithSlash() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp/?foo=bar";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        Assertions.assertEquals("foo=bar", finalUrl.getQuery());
    }

    @Test
    public void testUrlWithEncodedCharacters() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp%20space?foo=bar";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().contains("mcp%20space"));
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        Assertions.assertEquals("foo=bar", finalUrl.getQuery());
    }

    @Test
    public void testIsMcpRequestWithValidMcpMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        Pair<Boolean, McpJsonRpcModel> result = McpRequestResponseUtils.isMcpRequest(responseParams);
        Assertions.assertTrue(result.getFirst());
        Assertions.assertNotNull(result.getSecond());
        Assertions.assertEquals("tools/list", result.getSecond().getMethod());
    }

    @Test
    public void testIsMcpRequestWithInvalidMcpMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"notamcp/method\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        Pair<Boolean, McpJsonRpcModel> result = McpRequestResponseUtils.isMcpRequest(responseParams);
        Assertions.assertFalse(result.getFirst());
    }

    @Test
    public void testMcpToolsCallWithNameAndQueryParams() throws Exception {
        // Mock DataActor to avoid NullPointerException during audit log insertion
        com.akto.data_actor.DataActor mockDataActor = Mockito.mock(com.akto.data_actor.DataActor.class);
        Mockito.doNothing().when(mockDataActor).insertMCPAuditDataLog(Mockito.any());
        java.lang.reflect.Field field = com.akto.mcp.McpRequestResponseUtils.class.getDeclaredField("dataActor");
        field.setAccessible(true);
        field.set(null, mockDataActor);

        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"testTool\" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call/testTool"));
        Assertions.assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsCallWithoutNameAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {}, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call"));
        Assertions.assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testMcpOtherMethodWithQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        Assertions.assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testNonMcpJsonRpcRequestWithQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"other/method\", \"id\": 1 }";
        String url = "http://localhost:8080/api?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/other/method"));
        Assertions.assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
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
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testUrlWithEncodedCharactersAndQueryParams() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp%20space?foo=bar&baz=qux";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        HttpResponseParams afterMcp = afterMcpList.get(0);
        URL finalUrl = new URL(afterMcp.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().contains("mcp%20space"));
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/list"));
        Assertions.assertEquals("foo=bar&baz=qux", finalUrl.getQuery());
    }

    @Test
    public void testMcpToolsListWithResponsePayload() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        
        // Set the response payload with 2 tools
        String responsePayload = "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"scan_running_hashes\",\"description\":\"Scan hashes from running processes using VirusTotal and return a summarized threat report.\",\"inputSchema\":{\"properties\":{},\"type\":\"object\"},\"outputSchema\":{\"properties\":{\"result\":{\"title\":\"Result\",\"type\":\"string\"}},\"required\":[\"result\"],\"title\":\"_WrappedResult\",\"type\":\"object\",\"x-fastmcp-wrap-result\":true}},{\"name\":\"example_usage\",\"description\":\"Example usage message for Claude users.\",\"inputSchema\":{\"properties\":{},\"type\":\"object\"},\"outputSchema\":{\"properties\":{\"result\":{\"title\":\"Result\",\"type\":\"string\"}},\"required\":[\"result\"],\"title\":\"_WrappedResult\",\"type\":\"object\",\"x-fastmcp-wrap-result\":true}}]}}";
        responseParams.setPayload(responsePayload);
        
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        
        // Should return 3 items: original tools/list + 2 tools/call requests
        Assertions.assertEquals(3, afterMcpList.size());
        
        // First item should be the original tools/list request
        HttpResponseParams originalRequest = afterMcpList.get(0);
        URL originalUrl = new URL(originalRequest.getRequestParams().getURL());
        Assertions.assertTrue(originalUrl.getPath().endsWith("/tools/list"));
        
        // Next two items should be tools/call requests for each tool
        HttpResponseParams firstToolCall = afterMcpList.get(1);
        URL firstToolUrl = new URL(firstToolCall.getRequestParams().getURL());
        Assertions.assertTrue(firstToolUrl.getPath().endsWith("/tools/call/scan_running_hashes"));
        
        HttpResponseParams secondToolCall = afterMcpList.get(2);
        URL secondToolUrl = new URL(secondToolCall.getRequestParams().getURL());
        Assertions.assertTrue(secondToolUrl.getPath().endsWith("/tools/call/example_usage"));
    }

    @Test
    public void testMcpToolsCallNormalRequest() throws Exception {
        // Mock DataActor to avoid NullPointerException during audit log insertion
        com.akto.data_actor.DataActor mockDataActor = Mockito.mock(com.akto.data_actor.DataActor.class);
        Mockito.doNothing().when(mockDataActor).insertMCPAuditDataLog(Mockito.any());
        java.lang.reflect.Field field = com.akto.mcp.McpRequestResponseUtils.class.getDeclaredField("dataActor");
        field.setAccessible(true);
        field.set(null, mockDataActor);

        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"testTool\" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        
        // Should return only 1 item for normal tools/call request
        Assertions.assertEquals(1, afterMcpList.size());
        
        HttpResponseParams result = afterMcpList.get(0);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/call/testTool"));
    }

    @Test
    public void testMcpToolsListWithInvalidResponse() throws Exception {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";
        HttpResponseParams responseParams = createHttpResponseParams(payload, url);
        
        // Set invalid response payload (malformed JSON)
        String invalidResponsePayload = "data: {\"jsonrpc\":hnhn w\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"scan_running_hashes\",\"description\":\"Scan hashes from running processes using VirusTotal and return a summarized threat report.\",\"inputSchema\":{\"properties\":{},\"type\":\"object\"},\"outputSchema\":{\"properties\":{\"result\":{\"title\":\"Result\",\"type\":\"string\"}},\"required\":[\"result\"],\"title\":\"_WrappedResult\",\"type\":\"object\",\"x-fastmcp-wrap-result\":true}},{\"name\":\"example_usage\",\"description\":\"Example usage message for Claude users.\",\"inputSchema\":{\"properties\":{},\"type\":\"object\"},\"outputSchema\":{\"properties\":{\"result\":{\"title\":\"Result\",\"type\":\"string\"}},\"required\":[\"result\"],\"title\":\"_WrappedResult\",\"type\":\"object\",\"x-fastmcp-wrap-result\":true}}]}}";
        responseParams.setPayload(invalidResponsePayload);
        
        HttpResponseParams afterJsonRpc = JsonRpcUtils.parseJsonRpcResponse(responseParams);
        java.util.List<HttpResponseParams> afterMcpList = McpRequestResponseUtils.parseMcpResponseParams(afterJsonRpc);
        
        // Should return only 1 item (original tools/list request) when response is invalid
        Assertions.assertEquals(1, afterMcpList.size());
        
        HttpResponseParams result = afterMcpList.get(0);
        URL finalUrl = new URL(result.getRequestParams().getURL());
        Assertions.assertTrue(finalUrl.getPath().endsWith("/tools/list"));
    }
}