package com.akto.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.SmartAgentConfig;
import com.akto.dto.testing.SmartAgentKnowledgeFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    private static SmartAgentConfig smartAgent() {
        SmartAgentConfig agent = new SmartAgentConfig();
        agent.setProfileId("p1");
        agent.setProfileName("Shop agent");
        agent.setSkills(Arrays.asList("OBJECT_OWNERSHIP", "TENANT_ISOLATION"));
        agent.setCustomSkills(Collections.singletonList(new SmartAgentKnowledgeFile("Refunds", "refund an order twice")));
        agent.setInstructions("Order 7 is mine.");
        String sha = "ab" + new String(new char[62]).replace('\0', 'c');
        agent.setKnowledgeFiles(Arrays.asList(new SmartAgentKnowledgeFile("flows.md", "checkout then pay"),
                new SmartAgentKnowledgeFile("spec.yaml", null, sha, 1200)));
        agent.setKnowledgeLinks(Collections.singletonList("https://docs.example.test/openapi.json"));
        return agent;
    }

    @Test
    void theRunsSmartAgentIsSentWithEachSmartTestAndAStoredFileOnlyAsItsId() throws Exception {
        executor().executeAgenticTest(captured(), "X", null, smartAgent());
        JsonNode sent = sent().get("smartAgent");
        assertEquals("Shop agent", sent.get("profileName").asText());
        assertEquals("TENANT_ISOLATION", sent.get("skills").get(1).asText());
        assertEquals("refund an order twice", sent.get("customSkills").get(0).get("content").asText());
        assertFalse(sent.get("allowCreateIdentities").asBoolean());
        assertEquals("Order 7 is mine.", sent.get("instructions").asText());
        assertEquals("https://docs.example.test/openapi.json", sent.get("knowledgeLinks").get(0).asText());
        assertEquals("checkout then pay", sent.get("knowledgeFiles").get(0).get("content").asText());
        assertFalse(sent.get("knowledgeFiles").get(1).has("content"));
        assertEquals(1200, sent.get("knowledgeFiles").get(1).get("size").asInt());
        assertTrue(sent.get("knowledgeFiles").get(1).get("sha256").asText().startsWith("ab"));

        executor().verifyFinding(captured(), captured(), "X", smartAgent());
        assertEquals("Shop agent", sent().get("smartAgent").get("profileName").asText());
        assertFalse(sent().get("finding").has("smartAgent"));
    }

    @Test
    void withoutASmartAgentTheRequestIsExactlyAsBefore() throws Exception {
        executor().executeAgenticTest(captured(), "X", "use org 7", null);
        assertFalse(sent().has("smartAgent"));
        executor().verifyFinding(captured(), captured(), "X");
        assertFalse(sent().has("smartAgent"));
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
