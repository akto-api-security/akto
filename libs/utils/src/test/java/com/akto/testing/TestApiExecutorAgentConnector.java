package com.akto.testing;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.dto.OriginalHttpRequest;

public class TestApiExecutorAgentConnector {

    @Test
    public void testReverseN8NHostname() {
        // Setup
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("wf123.n8n.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("n8n"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://wf123.n8n.example.com/webhook/test",
            "", "POST", "{}", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify
        assertEquals("https://n8n.example.com/webhook/test", result);
    }

    @Test
    public void testReverseLangchainHostname() {
        // Setup
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("abc123.langchain.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("langchain"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://abc123.langchain.example.com/api/v1/runs",
            "", "GET", "", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify
        assertEquals("https://langchain.example.com/api/v1/runs", result);
    }

    @Test
    public void testReverseCopilotHostname() {
        // Setup
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("conv456.copilot.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("copilot"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://conv456.copilot.example.com/copilot/conversation/123",
            "", "POST", "{}", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify
        assertEquals("https://copilot.example.com/copilot/conversation/123", result);
    }

    @Test
    public void testNonAgentConnectorAPI() {
        // Setup - No custom header
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("api.example.com"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://api.example.com/test",
            "", "GET", "", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Should return original URL
        assertEquals("https://api.example.com/test", result);
    }

    @Test
    public void testWithPortAndPath() {
        // Setup
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("abc123.langchain.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("langchain"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://abc123.langchain.example.com:8443/api/v1/test?key=value#section",
            "", "POST", "{}", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Port, path, query, and fragment should be preserved
        assertEquals("https://langchain.example.com:8443/api/v1/test?key=value#section", result);
    }

    @Test
    public void testMultipleSubdomains() {
        // Setup - Test with multiple subdomain levels
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("abc123.api.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("n8n"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://abc123.api.example.com/webhook/payment",
            "", "POST", "{}", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Should remove only the first subdomain
        assertEquals("https://api.example.com/webhook/payment", result);
    }

    @Test
    public void testWithQueryParameters() {
        // Setup
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("wf789.n8n.company.io"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("n8n"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://wf789.n8n.company.io/webhook/user?id=123&action=create",
            "", "POST", "{\"user\":\"test\"}", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify
        assertEquals("https://n8n.company.io/webhook/user?id=123&action=create", result);
    }

    @Test
    public void testUnknownConnectorSource() {
        // Setup - Unknown connector source
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("xyz789.unknown.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("unknown-source"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://xyz789.unknown.example.com/test",
            "", "GET", "", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Should return original URL for unknown sources
        assertEquals("https://xyz789.unknown.example.com/test", result);
    }

    @Test
    public void testMissingHostHeader() {
        // Setup - Missing host header
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-akto-agent-connector-source", Arrays.asList("n8n"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://wf123.n8n.example.com/test",
            "", "GET", "", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Should return original URL
        assertEquals("https://wf123.n8n.example.com/test", result);
    }

    @Test
    public void testSinglePartHostname() {
        // Setup - Single part hostname (no dots)
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("localhost"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("n8n"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "http://localhost/test",
            "", "GET", "", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Should return original URL (can't strip subdomain from single part)
        assertEquals("http://localhost/test", result);
    }

    @Test
    public void testCaseInsensitiveConnectorSource() {
        // Setup - Test case insensitivity
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Arrays.asList("wf123.n8n.example.com"));
        headers.put("x-akto-agent-connector-source", Arrays.asList("N8N"));

        OriginalHttpRequest request = new OriginalHttpRequest(
            "https://wf123.n8n.example.com/webhook/test",
            "", "POST", "{}", headers, "HTTP/1.1"
        );

        // Execute
        String result = ApiExecutor.reverseAgentConnectorHostname(request);

        // Verify - Should work with uppercase connector source
        assertEquals("https://n8n.example.com/webhook/test", result);
    }
}
