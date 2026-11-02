package com.akto.testing;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import okhttp3.WebSocket;
import okio.ByteString;

import java.util.*;
import java.util.concurrent.*;

public class WebSocketConnection {

    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketConnection.class, LogDb.TESTING);

    /** A server frame together with the wall-clock time it was received. */
    private static class TimestampedMessage {
        final String content;
        final long receivedAt;
        TimestampedMessage(String content) {
            this.content = content;
            this.receivedAt = System.currentTimeMillis();
        }
    }

    private final String url;
    private final Map<String, List<String>> headers;
    private final String subcategoryId;
    private WebSocket webSocket;
    private final Queue<TimestampedMessage> receivedMessages = new ConcurrentLinkedQueue<>();
    /** Wall-clock time of the most recent sendMessage call; responses received before this are ignored. */
    private volatile long lastSentAt = 0;
    private final Object lock = new Object();
    private volatile boolean isConnected = false;
    private volatile boolean isClosed = false;
    private volatile String closeReason;
    private volatile int closeCode;
    /** HTTP status of a refused handshake; 0 when the handshake succeeded or never got a response. */
    private volatile int handshakeStatus;
    private volatile String failureMessage;

    public WebSocketConnection(String url, Map<String, List<String>> headers, String subcategoryId) {
        this.url = url;
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.subcategoryId = subcategoryId;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
        synchronized (lock) {
            this.isConnected = true;
            lock.notifyAll();
        }
    }

    public boolean isConnected() {
        return isConnected && !isClosed;
    }

    public void sendMessage(String payload) throws Exception {
        if (!isConnected() || webSocket == null) {
            throw new IllegalStateException("WebSocket is not connected");
        }
        // Stamp before the actual send so any message with receivedAt < lastSentAt
        // is definitively a pre-send frame (e.g. connection-open event) and will be skipped.
        lastSentAt = System.currentTimeMillis();
        boolean sent = webSocket.send(payload);
        if (!sent) {
            throw new Exception("Failed to send message: " + payload);
        }
        loggerMaker.infoAndAddToDb("WebSocket message sent: " + payload.substring(0, Math.min(100, payload.length())), LogDb.TESTING);
    }

    /** Thrown when the server closed (or refused) the connection before replying. */
    public static class ClosedException extends Exception {
        public ClosedException(String message) {
            super(message);
        }
    }

    // after the first reply frame, how long to wait for a later one (e.g. an ack followed by the real reply)
    private static final long SETTLE_MS = 300;

    public String getNextResponse(long timeoutMs) throws TimeoutException, InterruptedException, ClosedException {
        return getNextResponse(timeoutMs, null);
    }

    /**
     * The server's reply to the last sent message: the frame echoing requestId if the server echoes it,
     * else the latest frame received within SETTLE_MS of the first one. Stops waiting as soon as the
     * server closes the connection.
     */
    public String getNextResponse(long timeoutMs, String requestId) throws TimeoutException, InterruptedException, ClosedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long settleUntil = -1;
        TimestampedMessage resultMsg = null;
        try {
            while (true) {
                TimestampedMessage requestIdMatch = null;
                TimestampedMessage latest = null;
                for (TimestampedMessage candidate : receivedMessages) {
                    if (candidate.receivedAt >= lastSentAt && !isPingPong(candidate.content)) {
                        latest = candidate;
                        if (messageContainsRequestId(candidate.content, requestId)) {
                            requestIdMatch = candidate;
                        }
                    }
                }

                long now = System.currentTimeMillis();
                if (requestIdMatch != null) {
                    resultMsg = requestIdMatch;
                    break;
                }
                if (latest != null) {
                    if (settleUntil < 0) {
                        settleUntil = now + SETTLE_MS;
                    }
                    if (now >= settleUntil || now >= deadline || isClosed) {
                        resultMsg = latest;
                        break;
                    }
                } else if (isClosed) {
                    throw new ClosedException("Connection closed before a reply (code " + closeCode + ")");
                } else if (now >= deadline) {
                    throw new TimeoutException("No response received within " + timeoutMs + "ms");
                }
                Thread.sleep(10);
            }
        } finally {
            receivedMessages.clear();
        }

        loggerMaker.infoAndAddToDb("WebSocket response received" + (requestId != null ? " (requestId=" + requestId + ")" : "") + ": "
                + resultMsg.content.substring(0, Math.min(100, resultMsg.content.length())), LogDb.TESTING);
        return resultMsg.content;
    }

    public void onMessage(String text) {
        receivedMessages.add(new TimestampedMessage(text));
    }

    public void onMessage(ByteString bytes) {
        try {
            receivedMessages.add(new TimestampedMessage(bytes.utf8()));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error converting WebSocket binary message: " + e.getMessage(), LogDb.TESTING);
        }
    }

    public void onOpen(WebSocket webSocket) {
        synchronized (lock) {
            this.webSocket = webSocket;
            this.isConnected = true;
            lock.notifyAll();
        }
        loggerMaker.infoAndAddToDb("WebSocket connected to: " + url, LogDb.TESTING);
    }

    public void onClosing(WebSocket webSocket, int code, String reason) {
        loggerMaker.infoAndAddToDb("WebSocket closing with code " + code + ": " + reason, LogDb.TESTING);
    }

    public void onClosed(WebSocket webSocket, int code, String reason) {
        synchronized (lock) {
            this.isClosed = true;
            this.closeCode = code;
            this.closeReason = reason;
            lock.notifyAll();
        }
        loggerMaker.infoAndAddToDb("WebSocket closed with code " + code + ": " + reason, LogDb.TESTING);
    }

    public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
        synchronized (lock) {
            // a refused handshake comes back as a plain HTTP response (e.g. 401/403) instead of 101
            if (response != null && response.code() != 101) {
                this.handshakeStatus = response.code();
            }
            this.failureMessage = t != null ? t.getMessage() : null;
            this.isClosed = true;
            lock.notifyAll();
        }
        loggerMaker.errorAndAddToDb("WebSocket failure: " + t.getMessage(), LogDb.TESTING);
    }

    public void close() {
        if (webSocket != null && !isClosed) {
            webSocket.close(1000, "Test completed");
            synchronized (lock) {
                isClosed = true;
                lock.notifyAll();
            }
            loggerMaker.infoAndAddToDb("WebSocket closed for subcategory: " + subcategoryId, LogDb.TESTING);
        }
    }


    public void drainPendingMessages() {
        receivedMessages.clear();
        lastSentAt = System.currentTimeMillis();
    }

    private boolean isPingPong(String msg) {
        if (msg == null || msg.isEmpty()) {
            return true;
        }
        return msg.equalsIgnoreCase("PING") || msg.equalsIgnoreCase("PONG");
    }

    private boolean messageContainsRequestId(String messageContent, String requestId) {
        if (requestId == null || messageContent == null) {
            return false;
        }
        try {
            BasicDBObject msgObj = BasicDBObject.parse(messageContent);
            // Check if the message contains the matching requestId
            String msgRequestId = msgObj.getString("requestId");
            if (msgRequestId != null && msgRequestId.equals(requestId)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            // If message is not JSON, skip it
            return false;
        }
    }

    public String getUrl() {
        return url;
    }

    public Map<String, List<String>> getHeaders() {
        return new HashMap<>(headers);
    }

    public String getSubcategoryId() {
        return subcategoryId;
    }

    public int getCloseCode() {
        return closeCode;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public int getHandshakeStatus() {
        return handshakeStatus;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void waitForConnection(long timeoutMs) throws TimeoutException, InterruptedException, ClosedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            // a failed handshake closes the connection: stop waiting instead of running out the timeout
            while (!isConnected && !isClosed && System.currentTimeMillis() < deadline) {
                long waitTime = deadline - System.currentTimeMillis();
                if (waitTime > 0) {
                    lock.wait(waitTime);
                }
            }
            if (!isConnected) {
                if (isClosed) {
                    throw new ClosedException("WebSocket connection refused"
                            + (handshakeStatus > 0 ? " (HTTP " + handshakeStatus + ")" : "")
                            + (failureMessage != null ? ": " + failureMessage : ""));
                }
                throw new TimeoutException("WebSocket connection not established within " + timeoutMs + "ms");
            }
        }
    }
}
