package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.TestingRunConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cross-checks the conversationState handoff used by agentic multi-shot scripts:
 * post-request publishes a value from the first response; every later pre-request in
 * the same conversation sees that same value (1 → 2,3,4…), not N → N+1.
 */
public class ApiExecutorUtilConversationStateTest {

    private static final String POST_PUBLISH_SCRIPT =
            "var conversationState;\n"
                    + "var parsed = JSON.parse(String(body));\n"
                    + "if (parsed.conversation_token) {\n"
                    + "  conversationState = String(parsed.conversation_token);\n"
                    + "}\n";

    private static final String PRE_CONTINUE_SCRIPT =
            "var conversationState;\n"
                    + "if (conversationState != null && String(conversationState).length > 0) {\n"
                    + "  method = 'PUT';\n"
                    + "  var parsed = JSON.parse(String(payload));\n"
                    + "  parsed.conversation_token = String(conversationState);\n"
                    + "  payload = JSON.stringify(parsed);\n"
                    + "}\n";

    @BeforeEach
    public void setUp() {
        ApiExecutorUtil.resetForTest();
    }

    @AfterEach
    public void tearDown() {
        ApiExecutorUtil.resetForTest();
    }

    @Test
    public void commentedExecuteOnceMarkerIsIgnored() {
        assertFalse(ApiExecutorUtil.hasExecuteOncePerConversationMarker(
                "// var executeOncePerConversation = true;\nvar x = 1;"));
        assertFalse(ApiExecutorUtil.hasExecuteOncePerConversationMarker(
                "/* var executeOncePerConversation = true; */\nvar x = 1;"));
        assertTrue(ApiExecutorUtil.hasExecuteOncePerConversationMarker(
                "var executeOncePerConversation = true;\nvar x = 1;"));
    }

    @Test
    public void firstTurnPreRequestSeesNullConversationState() {
        ApiExecutorUtil.installScriptsForTest(PRE_CONTINUE_SCRIPT, POST_PUBLISH_SCRIPT);

        TestingRunConfig config = configWithConversation("conv-1");
        OriginalHttpRequest request = request("{\"message\":\"hello turn 1\"}");

        ApiExecutorUtil.calculateHashAndAddAuth(request, true, config);

        assertEquals("POST", request.getMethod());
        assertFalse(request.getBody().contains("conversation_token"));
    }

    @Test
    public void tokenFromTurnOneIsReusedOnLaterTurns() {
        ApiExecutorUtil.installScriptsForTest(PRE_CONTINUE_SCRIPT, POST_PUBLISH_SCRIPT);
        TestingRunConfig config = configWithConversation("conv-1");

        OriginalHttpRequest turn1 = request("{\"message\":\"turn 1\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(turn1, true, config);
        assertEquals("POST", turn1.getMethod());

        OriginalHttpResponse turn1Response = new OriginalHttpResponse(
                "{\"conversation_token\":\"token-from-turn-1\",\"status\":\"OK\"}",
                new HashMap<>(),
                200);
        ApiExecutorUtil.runPostRequestScript(turn1, turn1Response, true, config);

        for (int i = 2; i <= 4; i++) {
            OriginalHttpRequest later = request("{\"message\":\"turn " + i + "\"}");
            ApiExecutorUtil.calculateHashAndAddAuth(later, true, config);

            assertEquals("PUT", later.getMethod(), "turn " + i + " should continue the conversation");
            assertTrue(later.getBody().contains("\"conversation_token\":\"token-from-turn-1\""),
                    "turn " + i + " body=" + later.getBody());
            assertTrue(later.getBody().contains("\"message\":\"turn " + i + "\""));
        }
    }

    @Test
    public void firstWriteWinsWhenLaterResponsesPublishDifferentTokens() {
        ApiExecutorUtil.installScriptsForTest(PRE_CONTINUE_SCRIPT, POST_PUBLISH_SCRIPT);
        TestingRunConfig config = configWithConversation("conv-1");

        OriginalHttpRequest turn1 = request("{\"message\":\"turn 1\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(turn1, true, config);
        ApiExecutorUtil.runPostRequestScript(
                turn1,
                new OriginalHttpResponse("{\"conversation_token\":\"first-token\"}", new HashMap<>(), 200),
                true,
                config);

        OriginalHttpRequest turn2 = request("{\"message\":\"turn 2\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(turn2, true, config);
        ApiExecutorUtil.runPostRequestScript(
                turn2,
                new OriginalHttpResponse(
                        "{\"conversation_token\":\"second-token-should-be-ignored\"}",
                        new HashMap<>(),
                        200),
                true,
                config);

        OriginalHttpRequest turn3 = request("{\"message\":\"turn 3\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(turn3, true, config);

        assertEquals("PUT", turn3.getMethod());
        assertTrue(turn3.getBody().contains("\"conversation_token\":\"first-token\""));
        assertFalse(turn3.getBody().contains("second-token-should-be-ignored"));
    }

    @Test
    public void conversationStateIsIsolatedPerConversationId() {
        ApiExecutorUtil.installScriptsForTest(PRE_CONTINUE_SCRIPT, POST_PUBLISH_SCRIPT);

        TestingRunConfig convA = configWithConversation("conv-A");
        TestingRunConfig convB = configWithConversation("conv-B");

        OriginalHttpRequest a1 = request("{\"message\":\"a1\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(a1, true, convA);
        ApiExecutorUtil.runPostRequestScript(
                a1,
                new OriginalHttpResponse("{\"conversation_token\":\"token-A\"}", new HashMap<>(), 200),
                true,
                convA);

        OriginalHttpRequest b1 = request("{\"message\":\"b1\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(b1, true, convB);
        ApiExecutorUtil.runPostRequestScript(
                b1,
                new OriginalHttpResponse("{\"conversation_token\":\"token-B\"}", new HashMap<>(), 200),
                true,
                convB);

        OriginalHttpRequest a2 = request("{\"message\":\"a2\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(a2, true, convA);
        assertTrue(a2.getBody().contains("\"conversation_token\":\"token-A\""));

        OriginalHttpRequest b2 = request("{\"message\":\"b2\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(b2, true, convB);
        assertTrue(b2.getBody().contains("\"conversation_token\":\"token-B\""));
    }

    @Test
    public void commentedExecuteOnceDoesNotSkipPreScriptOnLaterTurns() {
        String preWithCommentedMarker =
                "// var executeOncePerConversation = true;\n"
                        + "method = 'PATCH';\n"
                        + "payload = JSON.stringify({touched: true, conversationState: String(conversationState)});\n";

        ApiExecutorUtil.installScriptsForTest(preWithCommentedMarker, POST_PUBLISH_SCRIPT);
        TestingRunConfig config = configWithConversation("conv-cache-bug");

        OriginalHttpRequest turn1 = request("{\"message\":\"t1\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(turn1, true, config);
        assertEquals("PATCH", turn1.getMethod());
        ApiExecutorUtil.runPostRequestScript(
                turn1,
                new OriginalHttpResponse("{\"conversation_token\":\"cached-bug-token\"}", new HashMap<>(), 200),
                true,
                config);

        OriginalHttpRequest turn2 = request("{\"message\":\"t2\"}");
        ApiExecutorUtil.calculateHashAndAddAuth(turn2, true, config);

        // If the commented marker had enabled caching, turn2 would keep POST and the original body.
        assertEquals("PATCH", turn2.getMethod());
        assertTrue(turn2.getBody().contains("\"touched\":true"));
        assertTrue(turn2.getBody().contains("cached-bug-token"));
    }

    private static TestingRunConfig configWithConversation(String conversationId) {
        TestingRunConfig config = new TestingRunConfig();
        config.setConversationId(conversationId);
        return config;
    }

    private static OriginalHttpRequest request(String body) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList("application/json"));
        return new OriginalHttpRequest(
                "https://example.com/api/v1/resource",
                "",
                "POST",
                body,
                headers,
                "application/json");
    }
}
