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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

    private ServerSocket server;
    private volatile String receivedBody;
    private volatile String reply;

    // a plain socket server (Java 8, no com.sun.net.httpserver): answers every request with reply
    @BeforeEach
    void startService() throws Exception {
        reply = "{\"attempts\": [], \"aiSummaryTraces\": [], \"skipped\": false}";
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread serverThread = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    receivedBody = readBody(socket.getInputStream());
                    byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                    OutputStream os = socket.getOutputStream();
                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + out.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    os.write(out);
                    os.flush();
                } catch (IOException e) {
                    // server closed by stopService
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void stopService() throws IOException {
        server.close();
    }

    // the request body; the client always sends Content-Length
    private static String readBody(InputStream in) throws IOException {
        int contentLength = 0;
        String line;
        while (!(line = readLine(in)).isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        byte[] body = new byte[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = in.read(body, read, contentLength - read);
            if (n < 0) break;
            read += n;
        }
        return new String(body, 0, read, StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (c != '\r') line.append((char) c);
        }
        return line.toString();
    }

    private AutomatedAgenticTestExecutor executor() {
        return new AutomatedAgenticTestExecutor("http://127.0.0.1:" + server.getLocalPort());
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
    void theRunContextIsSentOnlyWhenTheRunIsKnown() throws Exception {
        Map<String, Object> context = AutomatedAgenticTestExecutor.runContext("65a1b2c3d4e5f60718293a4b", 1111, "GET", "/api/v2/groups/STRING/alerts");
        executor().executeAgenticTest(captured(), "X", null, null, context);
        JsonNode sent = sent().get("runContext");
        assertEquals("65a1b2c3d4e5f60718293a4b", sent.get("testingRunResultSummaryId").asText());
        assertEquals(1111, sent.get("apiCollectionId").asInt());
        assertEquals("GET", sent.get("method").asText());
        assertEquals("/api/v2/groups/STRING/alerts", sent.get("url").asText());

        // null fields are left out; no summary id means no run context at all
        assertFalse(AutomatedAgenticTestExecutor.runContext("abc", null, null, null).containsKey("url"));
        assertNull(AutomatedAgenticTestExecutor.runContext(null, 1111, "GET", "/x"));
        executor().executeAgenticTest(captured(), "X", null, null, null);
        assertFalse(sent().has("runContext"));
        executor().executeAgenticTest(captured(), "X", "use org 7", null);
        assertFalse(sent().has("runContext"));
        executor().verifyFinding(captured(), captured(), "X");
        assertFalse(sent().has("runContext"));
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
