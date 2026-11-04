package com.akto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

            // sent as prepared by ApiExecutor: type injected, then the pre-request script
            String requestBody = request.getBody();
            if (requestBody == null || requestBody.isEmpty()) {
                throw new IllegalArgumentException("WebSocket message body cannot be empty");
            }

            // Drain any pending messages before sending to avoid response mismatch
            conn.drainPendingMessages();

            conn.sendMessage(requestBody);

            if (COLLECT_REPLIES) {
                return repliesResponse(conn.collectReplies(timeoutMs, TEST_REPLY_QUIET_MS));
            }
            String response = conn.getNextResponse(timeoutMs);

            return new OriginalHttpResponse(
                response,
                new HashMap<>(),
                200
            );

        } catch (WebSocketConnection.ClosedException e) {
            loggerMaker.errorAndAddToDb("WebSocket closed: " + e.getMessage(), LogDb.TESTING);
            return closedResponse(conn, e.getMessage());
        } catch (TimeoutException e) {
            loggerMaker.errorAndAddToDb("WebSocket timeout: " + e.getMessage(), LogDb.TESTING);
            return errorResponse("timeout", 504);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("WebSocket execution failed: " + e.getMessage(), LogDb.TESTING);
            return errorResponse(e.getMessage(), 500);
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

    static Map<String, List<String>> filterWebSocketHeaders(Map<String, List<String>> headers) {
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
            return initialMessageResponse(conn);
        } catch (WebSocketConnection.ClosedException e) {
            loggerMaker.errorAndAddToDb("WS connection string closed: " + e.getMessage(), LogDb.TESTING);
            return closedResponse(conn, e.getMessage());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("WS connection string connect failed: " + e.getMessage(), LogDb.TESTING);
            return errorResponse(e.getMessage(), 500);
        } finally {
            conn.close();
        }
    }

    /**
     * Connect-only, for importing a connection string: 101 with the server's handshake headers when accepted,
     * else the refusal (e.g. 401/403). Any greeting is not kept, as a connection string's body must stay empty.
     */
    public static OriginalHttpResponse handshake(String wsUrl, Map<String, List<String>> headers, long timeoutMs) {
        if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
        Map<String, List<String>> filteredHeaders = filterWebSocketHeaders(headers);
        WebSocketConnection conn = new WebSocketConnection(wsUrl, filteredHeaders, "connection-string");
        try {
            WebSocketConnectionPool.createDirectConnection(wsUrl, filteredHeaders, conn);
            conn.waitForConnection(timeoutMs);
            return new OriginalHttpResponse("", conn.getHandshakeHeaders(), 101);
        } catch (WebSocketConnection.ClosedException e) {
            loggerMaker.errorAndAddToDb("WS handshake refused: " + e.getMessage(), LogDb.TESTING);
            return closedResponse(conn, e.getMessage());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("WS handshake failed: " + e.getMessage(), LogDb.TESTING);
            return errorResponse(e.getMessage(), 500);
        } finally {
            conn.close();
        }
    }

    // a server that sends a greeting does so right after the handshake; one that doesn't must not cost the full timeout
    private static final long INITIAL_MESSAGE_WAIT_MS = 3000;

    /** A connection-string endpoint is tested by connecting: its greeting, if any, is the response. */
    private static OriginalHttpResponse initialMessageResponse(WebSocketConnection conn)
            throws InterruptedException, WebSocketConnection.ClosedException {
        String responseBody;
        try {
            responseBody = conn.getNextResponse(INITIAL_MESSAGE_WAIT_MS);
        } catch (TimeoutException e) {
            responseBody = "{}";
        }
        return new OriginalHttpResponse(responseBody, new HashMap<>(), 200);
    }

    /**
     * WebSocket has no status line, so a rejection is mapped to the HTTP status templates check:
     * the refused handshake's own status, else the server's close code.
     */
    static int statusForClosedConnection(int handshakeStatus, int closeCode) {
        if (handshakeStatus > 0) {
            return handshakeStatus;
        }
        // application close codes often carry an HTTP status: 4401 -> 401, 4403 -> 403
        if (closeCode >= 4400 && closeCode <= 4599) {
            return closeCode - 4000;
        }
        switch (closeCode) {
            case 0:
                return 500; // no handshake response and no close frame: the connection itself failed
            case 1008:
                return 403; // policy violation, the usual way servers reject credentials
            case 1011:
                return 500; // server error
            default:
                return 400; // closed without replying: the message was not accepted
        }
    }

    // normal tests: every reply frame until the server goes quiet; "false" keeps only the latest frame of the first burst
    static final boolean COLLECT_REPLIES = !"false".equalsIgnoreCase(System.getenv("AKTO_WS_COLLECT_REPLIES"));

    private static final ObjectMapper mapper = new ObjectMapper();

    /** The reply frames as one response: a single frame as is, several as a JSON array (one element per frame). */
    static OriginalHttpResponse repliesResponse(List<String> frames) {
        OriginalHttpResponse response = new OriginalHttpResponse(replyBody(frames), new HashMap<>(), 200);
        response.setStreamingChunks(frames);
        return response;
    }

    static String replyBody(List<String> frames) {
        if (frames.size() == 1) {
            return frames.get(0);
        }
        ArrayNode body = mapper.createArrayNode();
        for (String frame : frames) {
            try {
                body.add(mapper.readTree(frame));
            } catch (Exception e) {
                body.add(frame); // not JSON: kept as a string
            }
        }
        return body.toString();
    }

    static OriginalHttpResponse closedResponse(WebSocketConnection conn, String message) {
        BasicDBObject body = new BasicDBObject("error", message);
        if (conn.getCloseCode() > 0) {
            body.put("closeCode", conn.getCloseCode());
            body.put("closeReason", conn.getCloseReason());
        }
        return new OriginalHttpResponse(body.toJson(), new HashMap<>(),
                statusForClosedConnection(conn.getHandshakeStatus(), conn.getCloseCode()));
    }

    static OriginalHttpResponse errorResponse(String message, int status) {
        return new OriginalHttpResponse(new BasicDBObject("error", message).toJson(), new HashMap<>(), status);
    }

    // the keys a frame names its message type with, in the order ingestion reads them
    private static final List<String> MESSAGE_TYPE_KEYS = Arrays.asList("type", "messageType", "event");

    /**
     * Enriches a WebSocket message body with its message type (the URL path): set on the key the frame
     * already uses (type, messageType or event), else added as "type". Parses the body as JSON and
     * returns the modified JSON; if parsing fails, returns the original body unchanged.
     */
    static String enrichMessageWithUrl(String requestBody, String url) {
        try {
            // Remove leading slash if present
            String cleanUrl = url;
            if (cleanUrl != null && cleanUrl.startsWith("/")) {
                cleanUrl = cleanUrl.substring(1);
            }
            
            BasicDBObject bodyObj = BasicDBObject.parse(requestBody);
            String typeKey = "type";
            for (String key : MESSAGE_TYPE_KEYS) {
                if (bodyObj.containsKey(key)) {
                    typeKey = key;
                    break;
                }
            }
            bodyObj.put(typeKey, cleanUrl);
            return bodyObj.toJson();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to inject URL into WS message body: " + e.getMessage(), LogDb.TESTING);
            return requestBody;
        }
    }

    static String enrichMessageWithRequestId(String requestBody, String requestId) {
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
        return sendMessageDirect(request, wsUrl, headers, timeoutMs, requestId, false);
    }

    // after the last reply frame, how long a multi-frame (streamed) reply may go quiet before it is complete;
    // agents can pause for a tool call or retrieval mid-answer, so the default is generous
    static final long DEFAULT_REPLY_QUIET_MS = 15000;
    static final long REPLY_QUIET_MS = replyQuietMs(System.getenv("AKTO_WS_REPLY_QUIET_MS"), DEFAULT_REPLY_QUIET_MS);
    // normal tests: a reply (an ack, an error, a few frames) normally completes well within this
    static final long DEFAULT_TEST_REPLY_QUIET_MS = 1000;
    static final long TEST_REPLY_QUIET_MS = replyQuietMs(System.getenv("AKTO_WS_TEST_REPLY_QUIET_MS"), DEFAULT_TEST_REPLY_QUIET_MS);

    static long replyQuietMs(String value, long defaultMs) {
        try {
            long ms = Long.parseLong(value.trim());
            return ms > 0 ? ms : defaultMs;
        } catch (Exception e) {
            return defaultMs;
        }
    }

    /**
     * collectAllReplies (red teaming): the reply is every frame the server sends until it goes quiet,
     * so an agent's streamed answer arrives whole.
     */
    public static OriginalHttpResponse sendMessageDirect(
            OriginalHttpRequest request, String wsUrl, Map<String, List<String>> headers, long timeoutMs, String requestId,
            boolean collectAllReplies) {
        if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
        Map<String, List<String>> filteredHeaders = filterWebSocketHeaders(headers);
        WebSocketConnection conn = new WebSocketConnection(wsUrl, filteredHeaders, "kafka-task");
        try {
            // Connect
            WebSocketConnectionPool.createDirectConnection(wsUrl, filteredHeaders, conn);
            conn.waitForConnection(timeoutMs);
            
            // a connection-string endpoint has no message to send
            if (request.isConnectionString()) {
                return initialMessageResponse(conn);
            }

            // sent as prepared by ApiExecutor: type and requestId injected, then the pre-request script
            String requestBody = request.getBody();
            if (requestBody == null || requestBody.isEmpty()) {
                throw new IllegalArgumentException("WebSocket message body cannot be empty");
            }

            // Drain any pending messages before sending to avoid response mismatch
            conn.drainPendingMessages();

            conn.sendMessage(requestBody);

            if (collectAllReplies) {
                return repliesResponse(conn.collectReplies(timeoutMs, REPLY_QUIET_MS));
            }
            if (COLLECT_REPLIES) {
                return repliesResponse(conn.collectReplies(timeoutMs, TEST_REPLY_QUIET_MS));
            }
            // the frame echoing requestId when the server echoes it, else the first reply
            String response = conn.getNextResponse(timeoutMs, requestId);

            return new OriginalHttpResponse(response, new HashMap<>(), 200);
        } catch (WebSocketConnection.ClosedException e) {
            loggerMaker.errorAndAddToDb("Kafka WS closed: " + e.getMessage(), LogDb.TESTING);
            return closedResponse(conn, e.getMessage());
        } catch (java.util.concurrent.TimeoutException e) {
            loggerMaker.errorAndAddToDb("Kafka WS timeout: " + e.getMessage(), LogDb.TESTING);
            return errorResponse("timeout", 504);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Kafka WS direct execution failed: " + e.getMessage(), LogDb.TESTING);
            return errorResponse(e.getMessage(), 500);
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
