package com.akto.agent;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentLearningRequestTest {

    private static final ApiInfo.ApiInfoKey KEY = new ApiInfo.ApiInfoKey(1779231193, "https://agent.example/chat", URLMethods.Method.POST);

    private HttpServer server;

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static Map<String, Object> templateVars() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("testSubType", "BUSINESS_ALIGNMENT_COMPETITOR_CHECK_BASE64");
        varMap.put("testCategory", "AGENTIC_BUSINESS_ALIGNMENT");
        varMap.put("wordList_strategy", Arrays.asList("Encode the request in Base64.", "unused second entry"));
        varMap.put("wordList_base_prompt", Collections.singletonList("Ask which competitor is better."));
        return varMap;
    }

    @Test
    public void chatBodyCarriesTheTargetAndTheTemplateFields() {
        Map<String, Object> body = AgentClient.buildChatRequestBody("p", "c1", true, "summary1", false,
                AgentLearningContext.from(KEY, templateVars()));

        assertEquals(1779231193, body.get("apiCollectionId"));
        assertEquals("https://agent.example/chat", body.get("url"));
        assertEquals("POST", body.get("method"));
        assertEquals("BUSINESS_ALIGNMENT_COMPETITOR_CHECK_BASE64", body.get("testSubType"));
        assertEquals("AGENTIC_BUSINESS_ALIGNMENT", body.get("testCategory"));
        assertEquals("Encode the request in Base64.", body.get("strategy"));
        assertEquals("Ask which competitor is better.", body.get("basePrompt"));
        assertEquals("summary1", body.get("testingRunResultSummaryId"));
        assertEquals(true, body.get("isLastRequest"));
    }

    @Test
    public void unknownTemplateFieldsAreLeftOutNotSentEmpty() {
        Map<String, Object> varMap = new HashMap<>();
        varMap.put("testSubType", "   ");
        varMap.put("wordList_strategy", new ArrayList<String>());
        varMap.put("wordList_base_prompt", Collections.singletonList(new String(new char[AgentLearningContext.MAX_WORDLIST_CHARS + 1]).replace('\0', 'a')));

        Map<String, Object> body = AgentClient.buildChatRequestBody("p", "c1", false, "summary1", false,
                AgentLearningContext.from(KEY, varMap));

        assertEquals("POST", body.get("method"));
        for (String absent : Arrays.asList("testSubType", "testCategory", "strategy", "basePrompt")) {
            assertFalse(body.containsKey(absent), absent);
        }
    }

    @Test
    public void withoutATargetNoIdentityIsSent() {
        assertNull(AgentLearningContext.from(null, templateVars()));
        assertNull(AgentLearningContext.from(new ApiInfo.ApiInfoKey(1, "https://a", null), templateVars()));

        Map<String, Object> body = AgentClient.buildChatRequestBody("p", "c1", false, "summary1", false, null);
        for (String absent : Arrays.asList("apiCollectionId", "url", "method", "testSubType")) {
            assertFalse(body.containsKey(absent), absent);
        }
    }

    private AgentClient clientAnswering(List<Integer> statuses, AtomicInteger calls, StringBuilder lastBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/consolidateLearnings", exchange -> {
            int call = calls.getAndIncrement();
            lastBody.setLength(0);
            lastBody.append(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            int status = statuses.get(Math.min(call, statuses.size() - 1));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return new AgentClient("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    @Test
    public void consolidateRetriesAServerErrorThenStopsOnAccept() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        StringBuilder body = new StringBuilder();
        AgentClient client = clientAnswering(Arrays.asList(503, 202), calls, body);

        assertTrue(client.consolidateLearnings("summary1", 1));
        assertEquals(2, calls.get());
        assertEquals("{\"testingRunResultSummaryId\":\"summary1\"}", body.toString());
    }

    @Test
    public void consolidateDoesNotRetryARefusedRequest() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        AgentClient client = clientAnswering(Collections.singletonList(400), calls, new StringBuilder());

        assertFalse(client.consolidateLearnings("summary1", 1));
        assertEquals(1, calls.get());
    }

    @Test
    public void consolidateGivesUpAfterTheLastAttempt() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        AgentClient client = clientAnswering(Collections.singletonList(500), calls, new StringBuilder());

        assertFalse(client.consolidateLearnings("summary1", 1));
        assertEquals(AgentClient.CONSOLIDATE_MAX_ATTEMPTS, calls.get());
    }

    @Test
    public void consolidateReportsAnUnreachableServerAsNotAccepted() {
        // Port 1 is reserved and closed, so every attempt fails to connect.
        assertFalse(new AgentClient("http://127.0.0.1:1").consolidateLearnings("summary1", 1));
    }

    @Test
    public void onlyRunsWithAgenticTestsAreQueuedForConsolidation() {
        AgentClient client = new AgentClient("http://127.0.0.1:1");

        assertFalse(client.consolidateLearningsInBackground("never-ran-agentic-tests"));
        assertFalse(client.consolidateLearningsInBackground(null));
    }
}
