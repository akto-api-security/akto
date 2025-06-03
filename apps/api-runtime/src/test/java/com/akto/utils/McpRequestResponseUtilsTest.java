package com.akto.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.mcp.McpRequestResponseUtils;
import org.junit.jupiter.api.Test;

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
    public void testValidMcpRequestWithToolCall() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": { \"name\": \"testTool\" }, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?sessionId=abc123";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        assertEquals("http://localhost:8080/mcp/tools/call/testTool?sessionId=abc123", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testValidMcpRequestWithoutToolName() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {}, \"id\": 1 }";
        String url = "http://localhost:8080/mcp?token=xyz";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        assertEquals("http://localhost:8080/mcp/tools/call?token=xyz", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testNonMcpJsonRpcRequest() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"other/method\", \"id\": 1 }";
        String url = "http://localhost:8080/api";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        // Should remain unchanged
        assertEquals("http://localhost:8080/api", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testInvalidJsonRpcRequest() {
        String payload = "{ \"some\": \"invalid jsonrpc\" }";
        String url = "http://localhost:8080/mcp";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        // Should remain unchanged
        assertEquals("http://localhost:8080/mcp", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testValidMcpRequestWithoutQueryParams() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        assertEquals("http://localhost:8080/mcp/tools/list", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testEmptyPayload() {
        String payload = "";
        String url = "http://localhost:8080/mcp";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        // Should remain unchanged
        assertEquals("http://localhost:8080/mcp", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testUrlWithoutQueryParams() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        assertEquals("http://localhost:8080/mcp/tools/list", modifiedParams.getRequestParams().getURL());
    }

    @Test
    public void testPayloadWithUnsupportedMcpMethod() {
        String payload = "{ \"jsonrpc\": \"2.0\", \"method\": \"unsupported/method\", \"id\": 1 }";
        String url = "http://localhost:8080/mcp";

        HttpResponseParams responseParams = createHttpResponseParams(payload, url);

        HttpResponseParams modifiedParams = McpRequestResponseUtils.parseMcpResponseParams(responseParams);

        // Should remain unchanged
        assertEquals("http://localhost:8080/mcp", modifiedParams.getRequestParams().getURL());
    }
}