package com.akto.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The custom prompt goes to the smart testing service as userPrompt, and its skip reason comes back. */
class AutomatedAgenticTestExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String receivedBody;
    private String reply;

    @BeforeEach
    void startService() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        com.sun.net.httpserver.HttpHandler handler = exchange -> {
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        };
        server.createContext("/pentest", handler);
        server.createContext("/verify", handler);
        server.start();
        reply = "{\"attempts\": [], \"aiSummaryTraces\": [], \"skipped\": false}";
    }

    @AfterEach
    void stopService() {
        server.stop(0);
    }

    private AutomatedAgenticTestExecutor executor() {
        return new AutomatedAgenticTestExecutor("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static RawApi captured() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList("api.example.test"));
        OriginalHttpRequest request = new OriginalHttpRequest("/api/v2/groups/STRING/alerts", "pretty=false", "GET", null, headers, "HTTP/1.1");
        OriginalHttpResponse response = new OriginalHttpResponse("{\"results\": []}", new HashMap<>(), 200);
        return new RawApi(request, response, "");
    }

    private JsonNode sent() throws Exception {
        return MAPPER.readTree(receivedBody);
    }

    @Test
    void thePromptIsSentTrimmedAsUserPrompt() throws Exception {
        executor().executeAgenticTest(captured(), "SMART_CROSS_ROUTE_OBJECT_READ", "  Org id: 69f8. Find the rest yourself.  ");
        assertEquals("Org id: 69f8. Find the rest yourself.", sent().get("userPrompt").asText());
        assertEquals("SMART_CROSS_ROUTE_OBJECT_READ", sent().get("testSubType").asText());
    }

    @Test
    void noPromptMeansNoUserPromptField() throws Exception {
        executor().executeAgenticTest(captured(), "X");
        assertFalse(sent().has("userPrompt"));
        executor().executeAgenticTest(captured(), "X", "   ");
        assertFalse(sent().has("userPrompt"));
    }

    @Test
    void aSkippedTestBringsItsReasonBack() {
        reply = "{\"attempts\": [], \"aiSummaryTraces\": [], \"skipped\": true, \"skipReason\": \"the request could not be made to work\"}";
        PentestExecutionResult result = executor().executeAgenticTest(captured(), "X", "find the ids yourself");
        assertTrue(result.getTestResults().isEmpty());
        assertEquals("the request could not be made to work", result.getSkipReason());
    }

    @Test
    void aTestThatRanHasNoSkipReason() {
        reply = "{\"attempts\": [{\"vulnerable\": true, \"percentageMatch\": 0, \"errors\": [], \"severity\": \"HIGH\"}],"
                + " \"aiSummaryTraces\": [], \"skipped\": false, \"skipReason\": \"ignored when not skipped\"}";
        PentestExecutionResult result = executor().executeAgenticTest(captured(), "X", null);
        assertEquals(1, result.getTestResults().size());
        assertTrue(result.getTestResults().get(0).getVulnerable());
        assertNull(result.getSkipReason());
    }

    @Test
    void verifyingAFindingSendsBothRequestsAndNoPrompt() throws Exception {
        reply = "{\"attempts\": [{\"vulnerable\": true}], \"aiSummaryTraces\": []}";
        PentestExecutionResult result = executor().verifyFinding(captured(), captured(), "X");
        assertTrue(result.getTestResults().get(0).getVulnerable());
        assertTrue(sent().has("finding") && !sent().get("finding").has("testSubType"));
        assertFalse(sent().has("userPrompt"));
    }

    @Test
    void theDefaultExecutorUsesTheConfiguredService() {
        // only builds the client; nothing is sent
        assertNotNull(new AutomatedAgenticTestExecutor());
    }

    @Test
    void aResultBuiltWithoutAReasonHasNone() {
        assertNull(new PentestExecutionResult(Collections.emptyList(), null).getSkipReason());
        assertEquals("why", new PentestExecutionResult(Collections.emptyList(), null, "why").getSkipReason());
    }
}
