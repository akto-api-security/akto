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

        Map<String, List<String>> filteredHeaders = filterWebSocketHeaders(request.getHeaders());
        WebSocketConnection conn = new WebSocketConnection(connectionUrl, filteredHeaders, subcategoryId);
        try {
            // Connect (fresh, non-pooled connection for each request)
            WebSocketConnectionPool.createDirectConnection(connectionUrl, filteredHeaders, conn);
            conn.waitForConnection(timeoutMs);

            String requestBody = request.getBody();
            if (requestBody == null || requestBody.isEmpty()) {
                throw new IllegalArgumentException("WebSocket message body cannot be empty");
            }

            // Inject the URL into the request body as the "type" field
            requestBody = enrichMessageWithUrl(requestBody, request.getUrl());
            request.setBody(requestBody);

            // Drain any pending messages before sending to avoid response mismatch
            conn.drainPendingMessages();
            
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
        } finally {
            conn.close();
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

    private static String enrichMessageWithRequestId(String requestBody, String requestId) {
        if (requestId == null) {
            return requestBody;
        }
        try {
            BasicDBObject bodyObj = BasicDBObject.parse(requestBody);
            bodyObj.put("requestId", requestId);
            return bodyObj.toJson();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to inject requestId into WS message body: " + e.getMessage(), LogDb.TESTING);
            return requestBody;
        }
    }

    /**
     * For Kafka parallel execution: Opens a fresh WebSocket connection (non-pooled),
     * sends the message, receives the response, and closes the connection.
     * Each consumer task gets its own complete connection cycle to avoid message mixing.
     */
    public static OriginalHttpResponse sendMessageDirect(
            OriginalHttpRequest request, String wsUrl, Map<String, List<String>> headers, long timeoutMs, String requestId) {
        if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
        Map<String, List<String>> filteredHeaders = filterWebSocketHeaders(headers);
        WebSocketConnection conn = new WebSocketConnection(wsUrl, filteredHeaders, "kafka-task");
        try {
            // Connect
            WebSocketConnectionPool.createDirectConnection(wsUrl, filteredHeaders, conn);
            conn.waitForConnection(timeoutMs);
            
            // Send message
            String requestBody = request.getBody();
            if (requestBody == null || requestBody.isEmpty()) {
                throw new IllegalArgumentException("WebSocket message body cannot be empty");
            }
            // Inject URL into request body as "type" field and requestId for correlation
            if(!request.isConnectionString()){
                requestBody = enrichMessageWithUrl(requestBody, request.getUrl());
                requestBody = enrichMessageWithRequestId(requestBody, requestId);
                request.setBody(requestBody);
                
                // Drain any pending messages before sending to avoid response mismatch
                conn.drainPendingMessages();
                
                conn.sendMessage(requestBody);
            }
            
            // Get response with request ID matching
            String response = conn.getNextResponse(timeoutMs, requestId);
            
            return new OriginalHttpResponse(response, new HashMap<>(), 200);
        } catch (java.util.concurrent.TimeoutException e) {
            loggerMaker.errorAndAddToDb("Kafka WS timeout: " + e.getMessage(), LogDb.TESTING);
            return new OriginalHttpResponse("{\"error\": \"timeout\"}", new HashMap<>(), 504);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Kafka WS direct execution failed: " + e.getMessage(), LogDb.TESTING);
            return new OriginalHttpResponse("{\"error\": \"" + e.getMessage() + "\"}", new HashMap<>(), 500);
        } finally {
            conn.close();
        }
    }

    public static OriginalHttpResponse sendMessageDirect(
            OriginalHttpRequest request, String wsUrl, Map<String, List<String>> headers, long timeoutMs) {
        return sendMessageDirect(request, wsUrl, headers, timeoutMs, null);
    }

    public static void closeConnections() {
        WebSocketConnectionPool.closeAllConnections();
    }

    public static void closeConnection(String connectionUrl) {
        WebSocketConnectionPool.closeConnection(connectionUrl);
    }
}
