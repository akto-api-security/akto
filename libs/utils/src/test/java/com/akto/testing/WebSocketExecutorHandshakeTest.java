package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.dto.OriginalHttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Connect-only handshake used when importing a WebSocket agent's connection string. */
public class WebSocketExecutorHandshakeTest {

    private static final String CONNECTION_PATH = "/qa4/ws/insights-ai";

    private MockAgentWebSocketServer agent;

    @BeforeEach
    public void setUp() throws Exception {
        agent = new MockAgentWebSocketServer();
    }

    @AfterEach
    public void tearDown() throws Exception {
        agent.close();
    }

    private static Map<String, List<String>> headers(String cookie) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("origin", new ArrayList<>(Collections.singletonList("https://agent.example.com")));
        if (cookie != null) {
            headers.put("cookie", new ArrayList<>(Collections.singletonList(cookie)));
        }
        return headers;
    }

    @Test
    public void acceptedHandshakeReturns101WithItsHeadersAndNoBody() {
        OriginalHttpResponse response = WebSocketExecutor.handshake(agent.url(CONNECTION_PATH), headers("sid=abc"), 5000);

        assertEquals(101, response.getStatusCode());
        assertEquals("", response.getBody());
        assertTrue(response.getHeaders().containsKey("sec-websocket-accept"), response.getHeaders().toString());
        // nothing is sent over the connection
        assertTrue(agent.receivedFrames.isEmpty());
        assertEquals(CONNECTION_PATH, agent.handshakes.get(0).path);
        assertEquals("sid=abc", agent.handshakes.get(0).headers.get("cookie"));
    }

    @Test
    public void refusedHandshakeReturnsTheRefusalStatus() {
        OriginalHttpResponse response = WebSocketExecutor.handshake(agent.url(CONNECTION_PATH), headers(null), 5000);

        assertEquals(401, response.getStatusCode());
    }
}
