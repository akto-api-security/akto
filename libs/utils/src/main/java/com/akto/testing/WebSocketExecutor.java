package com.akto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;

import java.util.*;
import java.util.concurrent.TimeoutException;

public class WebSocketExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketExecutor.class, LogDb.TESTING);
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    public static OriginalHttpResponse sendMessage(OriginalHttpRequest request,
                                                    String connectionUrl,
                                                    String subcategoryId,
                                                    long timeoutMs) 
        throws Exception {

        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        try {
            WebSocketConnection conn = WebSocketConnectionPool.getOrCreate(
                connectionUrl,
                filterWebSocketHeaders(request.getHeaders()),
                subcategoryId,
                timeoutMs
            );

            String requestBody = request.getBody();
            if (requestBody == null || requestBody.isEmpty()) {
                throw new IllegalArgumentException("WebSocket message body cannot be empty");
            }

            // Inject the URL into the request body as the "type" field
            requestBody = enrichMessageWithUrl(requestBody, request.getUrl());
            request.setBody(requestBody);

            conn.sendMessage(requestBody);

            String response = conn.getNextResponse(timeoutMs);

            return new OriginalHttpResponse(
                response,
                new HashMap<>(),
                200
            );

        } catch (TimeoutException e) {
            loggerMaker.errorAndAddToDb("WebSocket timeout: " + e.getMessage(), LogDb.TESTING);
            return new OriginalHttpResponse(
                "{\"error\": \"timeout\"}",
                new HashMap<>(),
                504
            );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("WebSocket execution failed: " + e.getMessage(), LogDb.TESTING);
            return new OriginalHttpResponse(
                "{\"error\": \"" + e.getMessage() + "\"}",
                new HashMap<>(),
                500
            );
        }
    }

    public static OriginalHttpResponse sendMessage(OriginalHttpRequest request,
                                                    String connectionUrl,
                                                    String subcategoryId) 
        throws Exception {
        return sendMessage(request, connectionUrl, subcategoryId, DEFAULT_TIMEOUT_MS);
    }

    // Headers that OkHttp manages internally during the WebSocket handshake.
    // Passing them manually causes conflicts and silent connection failures.
    private static final Set<String> WS_PROTOCOL_HEADERS = new HashSet<>(Arrays.asList(
        "upgrade", "connection", "sec-websocket-key", "sec-websocket-version",
        "sec-websocket-extensions", "sec-websocket-accept", "host"
    ));

    private static Map<String, List<String>> filterWebSocketHeaders(Map<String, List<String>> headers) {
        if (headers == null) return new HashMap<>();
        Map<String, List<String>> filtered = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!WS_PROTOCOL_HEADERS.contains(entry.getKey().toLowerCase())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * For WebSocket connection-string endpoints: opens a fresh (unpooled) connection to
     * {@code wsUrl}, captures the initial server message, closes immediately, and returns 200.
     */
    public static OriginalHttpResponse connectAndGetInitialMessage(
            String wsUrl, Map<String, List<String>> headers, long timeoutMs) {
        if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
        Map<String, List<String>> filteredHeaders = filterWebSocketHeaders(headers);
        WebSocketConnection conn = new WebSocketConnection(wsUrl, filteredHeaders, "connection-string");
        try {
            WebSocketConnectionPool.createDirectConnection(wsUrl, filteredHeaders, conn);
            conn.waitForConnection(timeoutMs);
            String responseBody;
            try {
                responseBody = conn.getNextResponse(timeoutMs);
            } catch (java.util.concurrent.TimeoutException e) {
                responseBody = "{}";
            }
            return new OriginalHttpResponse(responseBody, new HashMap<>(), 200);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("WS connection string connect failed: " + e.getMessage(), LogDb.TESTING);
            return new OriginalHttpResponse("{\"error\": \"" + e.getMessage() + "\"}", new HashMap<>(), 500);
        } finally {
            conn.close();
        }
    }

    /**
     * Enriches a WebSocket message body by injecting the URL path as the "type" field.
     * Parses the body as JSON, sets type to the URL, and returns the modified JSON.
     * If parsing fails, returns the original body unchanged.
     */
    private static String enrichMessageWithUrl(String requestBody, String url) {
        try {
            // Remove leading slash if present
            String cleanUrl = url;
            if (cleanUrl != null && cleanUrl.startsWith("/")) {
                cleanUrl = cleanUrl.substring(1);
            }
            
            BasicDBObject bodyObj = BasicDBObject.parse(requestBody);
            bodyObj.put("type", cleanUrl);
            return bodyObj.toJson();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to inject URL into WS message body: " + e.getMessage(), LogDb.TESTING);
            return requestBody;
        }
    }

    public static void closeConnections() {
        WebSocketConnectionPool.closeAllConnections();
    }

    public static void closeConnection(String connectionUrl) {
        WebSocketConnectionPool.closeConnection(connectionUrl);
    }
}
