package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.TestingRunConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The PRE_REQUEST script on a WebSocket attempt: one run shapes the handshake (connectionUrl,
 * queryParams, headers) and the frame (payload).
 */
public class ApiExecutorUtilWebSocketScriptTest {

    private static final String CAPTURED_URL = "wss://chat.example.com/socket?token=old&room=1";

    @BeforeEach
    public void setUp() {
        ApiExecutorUtil.resetForTest();
    }

    @AfterEach
    public void tearDown() {
        ApiExecutorUtil.resetForTest();
    }

    @Test
    public void withoutScriptTheAttemptGoesOutAsCaptured() {
        ApiExecutorUtil.installScriptsForTest(null, null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config());

        assertEquals(CAPTURED_URL, attempt.connectionUrl);
        assertEquals("Bearer old", attempt.headers.get("authorization").get(0));
        assertEquals("{\"type\":\"chat.send\",\"text\":\"hi\"}", attempt.payload);
    }

    @Test
    public void scriptChangesHandshakeAndFrameInOneRun() {
        ApiExecutorUtil.installScriptsForTest(
                "if (protocol === 'websocket' && phase === 'connect') {\n"
                        + "  queryParams = 'token=new&room=1';\n"
                        + "  headers['authorization'] = ['Bearer new'];\n"
                        + "  var msg = JSON.parse(payload);\n"
                        + "  msg.sig = 'signed';\n"
                        + "  payload = JSON.stringify(msg);\n"
                        + "}\n", null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config());

        assertEquals("wss://chat.example.com/socket?token=new&room=1", attempt.connectionUrl);
        assertEquals("Bearer new", attempt.headers.get("authorization").get(0));
        assertEquals("{\"type\":\"chat.send\",\"text\":\"hi\",\"sig\":\"signed\"}", attempt.payload);
    }

    @Test
    public void scriptCanRemoveHeadersQueryParamsAndPayloadFields() {
        ApiExecutorUtil.installScriptsForTest(
                "headers.remove('authorization');\n"
                        + "delete headers['x-trace'];\n"
                        + "headers['x-new'] = ['added'];\n"
                        + "queryParams = queryParams.split('&').filter(function (p) { return p.indexOf('token=') !== 0; }).join('&');\n"
                        + "var msg = JSON.parse(payload);\n"
                        + "delete msg.type;\n"
                        + "payload = JSON.stringify(msg);\n", null);
        OriginalHttpRequest request = message();
        request.getHeaders().put("x-trace", Collections.singletonList("t1"));

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(request, CAPTURED_URL, config());

        assertNull(attempt.headers.get("authorization"));
        assertNull(attempt.headers.get("x-trace"));
        assertEquals("added", attempt.headers.get("x-new").get(0));
        assertEquals("wss://chat.example.com/socket?room=1", attempt.connectionUrl);
        assertEquals("{\"text\":\"hi\"}", attempt.payload);
    }

    @Test
    public void scriptCanReplaceTheWholeConnectionUrlAndHeaders() {
        ApiExecutorUtil.installScriptsForTest(
                "connectionUrl = 'ws://localhost:8080/other?v=2';\n"
                        + "headers = {cookie: ['sid=1']};\n"
                        + "payload = 'plain text frame';\n", null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config());

        // the script set no queryParams, so the query in the URL it returned is used
        assertEquals("ws://localhost:8080/other?v=2", attempt.connectionUrl);
        assertEquals(1, attempt.headers.size());
        assertEquals("sid=1", attempt.headers.get("cookie").get(0));
        assertEquals("plain text frame", attempt.payload);
    }

    @Test
    public void scriptSeesTheCapturedQueryAndKeepsItWhenUntouched() {
        ApiExecutorUtil.installScriptsForTest(
                "headers['x-seen'] = [connectionUrl + '|' + queryParams];\n", null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config());

        assertEquals("wss://chat.example.com/socket|token=old&room=1", attempt.headers.get("x-seen").get(0));
        assertEquals(CAPTURED_URL, attempt.connectionUrl);
    }

    @Test
    public void scriptSuppliesTheConnectionUrlWhenNoneWasCaptured() {
        ApiExecutorUtil.installScriptsForTest(
                "if (!connectionUrl) { connectionUrl = 'wss://staging.example.com/ws?token=abc'; }\n", null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), null, config());

        assertEquals("wss://staging.example.com/ws?token=abc", attempt.connectionUrl);
    }

    @Test
    public void noUrlCapturedAndNoneFromScriptStaysNull() {
        ApiExecutorUtil.installScriptsForTest("var untouched = true;\n", null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), null, config());

        assertNull(attempt.connectionUrl);
    }

    @Test
    public void failingScriptFallsBackToTheCapturedAttempt() {
        ApiExecutorUtil.installScriptsForTest("throw new Error('boom');\n", null);

        ApiExecutorUtil.WebSocketAttempt attempt = ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config());

        assertEquals(CAPTURED_URL, attempt.connectionUrl);
        assertEquals("Bearer old", attempt.headers.get("authorization").get(0));
    }

    @Test
    public void executeOncePerConversationReusesTheCachedHandshake() {
        ApiExecutorUtil.installScriptsForTest(
                "var executeOncePerConversation = true;\n"
                        + "cachedQueryParams = 'token=cached';\n"
                        + "cachedHeaders = {authorization: ['Bearer cached']};\n", null);
        TestingRunConfig config = config();

        ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config);
        ApiExecutorUtil.WebSocketAttempt second = ApiExecutorUtil.prepareWebSocketAttempt(message(), CAPTURED_URL, config);

        assertEquals("wss://chat.example.com/socket?token=cached", second.connectionUrl);
        assertEquals("Bearer cached", second.headers.get("authorization").get(0));
    }

    @Test
    public void httpScriptsCanCheckTheProtocol() {
        ApiExecutorUtil.installScriptsForTest(
                "if (protocol === 'websocket') { method = 'WS-ONLY'; } else { method = 'PUT'; }\n", null);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList("application/json"));
        OriginalHttpRequest http = new OriginalHttpRequest("https://example.com/api", "", "POST", "{}", headers, "");

        ApiExecutorUtil.calculateHashAndAddAuth(http, true, config());

        assertEquals("PUT", http.getMethod());
    }

    private static TestingRunConfig config() {
        TestingRunConfig config = new TestingRunConfig();
        config.setConversationId("ws-conv");
        return config;
    }

    private static OriginalHttpRequest message() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("authorization", Collections.singletonList("Bearer old"));
        OriginalHttpRequest request = new OriginalHttpRequest(
                "/chat.send", "", "WS", "{\"type\":\"chat.send\",\"text\":\"hi\"}", headers, "WEBSOCKET");
        request.setProtocolType(OriginalHttpRequest.ProtocolType.WEBSOCKET);
        return request;
    }
}
