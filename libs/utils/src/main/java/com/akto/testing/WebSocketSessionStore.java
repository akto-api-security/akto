package com.akto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Red teaming: one WebSocket per conversation, opened on its first turn and reused by the later ones,
 * so an agent that keeps the chat on the connection sees every turn. A dropped connection is reopened
 * on the next turn; the socket is closed when the conversation ends or after it sits idle.
 */
public class WebSocketSessionStore {

    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketSessionStore.class, LogDb.TESTING);

    private static final long IDLE_MS = idleSeconds(System.getenv("AKTO_WS_SESSION_IDLE_SECONDS")) * 1000L;

    private static final class Session {
        final WebSocketConnection conn;
        final String url;
        volatile long lastUsedAt = System.currentTimeMillis();

        Session(WebSocketConnection conn, String url) {
            this.conn = conn;
            this.url = url;
        }
    }

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<String, Object> locks = new ConcurrentHashMap<>();
    // conversations that already had a connection: opening another one means the agent may have lost the earlier turns
    private static final Set<String> openedConversations = ConcurrentHashMap.newKeySet();

    static long idleSeconds(String value) {
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? seconds : 300;
        } catch (Exception e) {
            return 300;
        }
    }

    /** Sends one turn of the conversation and returns every reply frame (see WebSocketExecutor.repliesResponse). */
    public static OriginalHttpResponse send(String conversationId, OriginalHttpRequest request, String wsUrl,
            Map<String, List<String>> headers, long timeoutMs) {
        closeIdle();
        String body = request.getBody();
        if (body == null || body.isEmpty()) {
            return WebSocketExecutor.errorResponse("WebSocket message body cannot be empty", 500);
        }
        // turns of one conversation are sent one at a time
        synchronized (locks.computeIfAbsent(conversationId, k -> new Object())) {
            Session session = sessions.get(conversationId);
            boolean reused = session != null && session.conn.isConnected() && Objects.equals(session.url, wsUrl);
            if (!reused && session != null) {
                remove(conversationId, session);
            }
            try {
                if (!reused) {
                    session = open(conversationId, wsUrl, headers, timeoutMs);
                }
                try {
                    return exchange(conversationId, session, body, timeoutMs);
                } catch (IllegalStateException | WebSocketConnection.ClosedException e) {
                    if (!reused) {
                        throw e;
                    }
                    // the server dropped the kept connection since the last turn: reconnect once (open() logs the lost context)
                    loggerMaker.infoAndAddToDb("WebSocket session for conversation " + conversationId + " was dropped: " + e.getMessage(), LogDb.TESTING);
                    remove(conversationId, session);
                    session = open(conversationId, wsUrl, headers, timeoutMs);
                    return exchange(conversationId, session, body, timeoutMs);
                }
            } catch (WebSocketConnection.ClosedException e) {
                loggerMaker.errorAndAddToDb("WebSocket session closed: " + e.getMessage(), LogDb.TESTING);
                Session failed = current(conversationId, session);
                OriginalHttpResponse response = failed != null ? WebSocketExecutor.closedResponse(failed.conn, e.getMessage())
                        : WebSocketExecutor.errorResponse(e.getMessage(), 500);
                remove(conversationId, failed);
                return response;
            } catch (TimeoutException e) {
                loggerMaker.errorAndAddToDb("WebSocket session timeout: " + e.getMessage(), LogDb.TESTING);
                remove(conversationId, current(conversationId, session));
                return WebSocketExecutor.errorResponse("timeout", 504);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("WebSocket session send failed: " + e.getMessage(), LogDb.TESTING);
                remove(conversationId, current(conversationId, session));
                return WebSocketExecutor.errorResponse(e.getMessage(), 500);
            }
        }
    }

    private static Session open(String conversationId, String wsUrl, Map<String, List<String>> headers, long timeoutMs) throws Exception {
        if (!openedConversations.add(conversationId)) {
            // dropped by the server, closed after a reply or after sitting idle: the new connection is a new
            // session for an agent that keeps the chat on the connection, so this multi-turn test may continue
            // without its earlier turns
            loggerMaker.errorAndAddToDb("WebSocket conversation " + conversationId + " reconnected mid-conversation,"
                    + " the agent may have lost the earlier turns", LogDb.TESTING);
        }
        Map<String, List<String>> filteredHeaders = WebSocketExecutor.filterWebSocketHeaders(headers);
        WebSocketConnection conn = new WebSocketConnection(wsUrl, filteredHeaders, "conversation-" + conversationId);
        Session session = new Session(conn, wsUrl);
        // stored before connecting, so a refused handshake is still closed and its status reported
        sessions.put(conversationId, session);
        WebSocketConnectionPool.createDirectConnection(wsUrl, filteredHeaders, conn);
        conn.waitForConnection(timeoutMs);
        loggerMaker.infoAndAddToDb("WebSocket session opened for conversation " + conversationId, LogDb.TESTING);
        return session;
    }

    // the session a failed turn used: open() stores a new one before connecting, so a refused handshake is found here
    private static Session current(String conversationId, Session fallback) {
        Session stored = sessions.get(conversationId);
        return stored != null ? stored : fallback;
    }

    private static OriginalHttpResponse exchange(String conversationId, Session session, String body, long timeoutMs) throws Exception {
        session.lastUsedAt = System.currentTimeMillis();
        session.conn.drainPendingMessages();
        session.conn.sendMessage(body);
        OriginalHttpResponse response = WebSocketExecutor.repliesResponse(
                session.conn.collectReplies(timeoutMs, WebSocketExecutor.REPLY_QUIET_MS));
        session.lastUsedAt = System.currentTimeMillis();
        if (!session.conn.isConnected()) {
            remove(conversationId, session); // the server closed after replying: the next turn reconnects
        }
        return response;
    }

    /** The conversation has ended: closes its connection, if one is open. */
    public static void closeConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }
        closeSession(conversationId);
        locks.remove(conversationId);
        openedConversations.remove(conversationId);
    }

    private static void closeSession(String conversationId) {
        Session session = sessions.get(conversationId);
        if (session != null) {
            remove(conversationId, session);
        }
    }

    public static void closeAll() {
        for (String conversationId : sessions.keySet()) {
            closeConversation(conversationId);
        }
    }

    static int openSessionCount() {
        return sessions.size();
    }

    private static void closeIdle() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (now - entry.getValue().lastUsedAt > IDLE_MS) {
                loggerMaker.infoAndAddToDb("Closing idle WebSocket session for conversation " + entry.getKey(), LogDb.TESTING);
                // the conversation may still have turns to send, so a later turn's reconnect is still reported
                closeSession(entry.getKey());
            }
        }
    }

    private static void remove(String conversationId, Session session) {
        if (session == null) {
            return;
        }
        sessions.remove(conversationId, session);
        try {
            session.conn.close();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error closing WebSocket session: " + e.getMessage(), LogDb.TESTING);
        }
    }
}
