package com.akto.testing;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketConnectionPool {

    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketConnectionPool.class, LogDb.TESTING);
    private static final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    private static final Map<Integer, String> connectionUrlCache = new ConcurrentHashMap<>();  // Cache connection URL by collectionId
    private static final Object poolLock = new Object();
    private static final Dispatcher dispatcher;
    static {
        dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(64);
    }
    // Single shared client so the dispatcher stays alive for all active WebSocket connections
    private static final OkHttpClient sharedClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)  // 0 = no read timeout (WS is persistent)
            .dispatcher(dispatcher)
            .build();

    public static WebSocketConnection getOrCreate(String url, 
                                                   Map<String, List<String>> headers,
                                                   String subcategoryId,
                                                   long connectionTimeoutMs) throws Exception {
        // Key by url + authorization so different test-role tokens get distinct connections.
        String connectionKey = buildConnectionKey(url, headers);
        synchronized (poolLock) {
            WebSocketConnection existing = connections.get(connectionKey);
            if (existing != null && existing.isConnected()) {
                loggerMaker.infoAndAddToDb("Reusing existing WebSocket connection to: " + url, LogDb.TESTING);
                return existing;
            }

            WebSocketConnection connection = new WebSocketConnection(url, headers, subcategoryId);
            createOkHttpWebSocket(url, headers, connection);
            
            try {
                connection.waitForConnection(connectionTimeoutMs);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Failed to establish WebSocket connection to " + url + ": " + e.getMessage(), LogDb.TESTING);
                throw e;
            }

            connections.put(connectionKey, connection);
            loggerMaker.infoAndAddToDb("Created new WebSocket connection to: " + url, LogDb.TESTING);
            return connection;
        }
    }

    private static String buildConnectionKey(String url, Map<String, List<String>> headers) {
        String auth = "";
        if (headers != null) {
            List<String> authValues = headers.get("authorization");
            if (authValues != null && !authValues.isEmpty()) {
                auth = authValues.get(0);
            }
        }
        return url + "|" + auth;
    }

    private static void createOkHttpWebSocket(String url, Map<String, List<String>> headers, WebSocketConnection wsConnection) {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(url);

            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        requestBuilder.addHeader(entry.getKey(), entry.getValue().get(0));
                    }
                }
            }

            Request request = requestBuilder.build();

            okhttp3.WebSocketListener listener = new okhttp3.WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                    wsConnection.onOpen(webSocket);
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    wsConnection.onMessage(text);
                }

                @Override
                public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                    wsConnection.onMessage(bytes);
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    wsConnection.onClosing(webSocket, code, reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    wsConnection.onClosed(webSocket, code, reason);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                    wsConnection.onFailure(webSocket, t, response);
                }
            };

            // Use shared client - never shut it down so dispatcher threads stay alive for callbacks
            sharedClient.newWebSocket(request, listener);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating OkHttp WebSocket: " + e.getMessage(), LogDb.TESTING);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a fresh WebSocket connection without adding it to the pool.
     * Used for connection-string endpoints that must open and close per test.
     */
    public static void createDirectConnection(String url, Map<String, List<String>> headers, WebSocketConnection wsConnection) {
        createOkHttpWebSocket(url, headers, wsConnection);
    }

    public static void closeConnection(String url) {
        synchronized (poolLock) {
            String prefix = url + "|";
            List<String> keysToRemove = new ArrayList<>();
            for (String key : connections.keySet()) {
                if (key.startsWith(prefix)) {
                    keysToRemove.add(key);
                }
            }
            for (String key : keysToRemove) {
                WebSocketConnection conn = connections.remove(key);
                if (conn != null) {
                    conn.close();
                }
            }
            if (!keysToRemove.isEmpty()) {
                loggerMaker.infoAndAddToDb("Closed WebSocket connection(s) to: " + url, LogDb.TESTING);
            }
        }
    }

    public static void closeAllConnections() {
        synchronized (poolLock) {
            for (WebSocketConnection conn : connections.values()) {
                try {
                    conn.close();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error closing WebSocket connection: " + e.getMessage(), LogDb.TESTING);
                }
            }
            connections.clear();
            loggerMaker.infoAndAddToDb("All WebSocket connections closed", LogDb.TESTING);
        }
    }

    public static WebSocketConnection getConnection(String url, Map<String, List<String>> headers) {
        return connections.get(buildConnectionKey(url, headers));
    }

    public static int getActiveConnectionCount() {
        return connections.size();
    }

    public static void cacheConnectionUrl(int collectionId, String connectionUrl) {
        connectionUrlCache.put(collectionId, connectionUrl);
        loggerMaker.infoAndAddToDb("Cached WebSocket connection URL for collection: " + collectionId + " -> " + connectionUrl, LogDb.TESTING);
    }

    public static String getCachedConnectionUrl(int collectionId) {
        return connectionUrlCache.get(collectionId);
    }

    public static boolean hasConnectionUrlCache(int collectionId) {
        return connectionUrlCache.containsKey(collectionId);
    }

    public static void clearConnectionUrlCache() {
        connectionUrlCache.clear();
        loggerMaker.infoAndAddToDb("Cleared connection URL cache for WebSocket", LogDb.TESTING);
    }
}
