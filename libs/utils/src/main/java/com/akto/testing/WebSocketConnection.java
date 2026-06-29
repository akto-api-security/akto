package com.akto.testing;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
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
    private boolean isConnected = false;
    private boolean isClosed = false;
    private String closeReason;
    private int closeCode;

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

    public String getNextResponse(long timeoutMs) throws TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TimestampedMessage msg = receivedMessages.poll();
            if (msg != null) {
                if (msg.receivedAt < lastSentAt || isPingPong(msg.content)) {
                    // Pre-send frame or ping/pong — discard and keep polling.
                    continue;
                }
                loggerMaker.infoAndAddToDb("WebSocket response received: " + msg.content.substring(0, Math.min(100, msg.content.length())), LogDb.TESTING);
                return msg.content;
            }
            Thread.sleep(10);
        }
        throw new TimeoutException("No response received within " + timeoutMs + "ms");
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

    public void waitForConnection(long timeoutMs) throws TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            while (!isConnected && System.currentTimeMillis() < deadline) {
                long waitTime = deadline - System.currentTimeMillis();
                if (waitTime > 0) {
                    lock.wait(waitTime);
                }
            }
            if (!isConnected) {
                throw new TimeoutException("WebSocket connection not established within " + timeoutMs + "ms");
            }
        }
    }
}
