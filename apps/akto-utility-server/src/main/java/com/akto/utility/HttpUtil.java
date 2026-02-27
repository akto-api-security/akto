package com.akto.utility;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/**
 * Shared HTTP and JSON helpers for JDK HttpServer handlers.
 */
public final class HttpUtil {

    private static final Gson GSON = new Gson();
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    /** Error message when method is not POST. */
    public static final String ERROR_METHOD_NOT_ALLOWED = "Method not allowed";
    /** Error message when body is missing. */
    public static final String ERROR_BODY_REQUIRED = "Request body is required";
    /** Error message when JSON is invalid. */
    public static final String ERROR_INVALID_JSON = "Invalid JSON";

    private HttpUtil() {}

    /** Shared Gson instance for JSON (de)serialization. */
    public static Gson gson() {
        return GSON;
    }

    /**
     * Ensures request method is POST. If not, sends 405 and returns false.
     * @return true if method is POST, false otherwise (response already sent)
     */
    public static boolean requirePost(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, mapOf("error", ERROR_METHOD_NOT_ALLOWED));
            return false;
        }
        return true;
    }

    /**
     * Reads request body; if null or empty, sends 400 and returns null.
     * @return body string or null if missing (response already sent)
     */
    public static String requireRequestBody(HttpExchange exchange) throws IOException {
        String rawBody = readRequestBody(exchange);
        if (rawBody == null || rawBody.isEmpty()) {
            sendJson(exchange, 400, mapOf("error", ERROR_BODY_REQUIRED));
            return null;
        }
        return rawBody;
    }

    /**
     * Parses body as JSON map. On invalid JSON or parse exception, sends 400 and returns null.
     * @return parsed map or null if invalid (response already sent)
     */
    public static Map<String, Object> parseJsonBody(String rawBody, HttpExchange exchange) throws IOException {
        try {
            Map<String, Object> json = GSON.fromJson(rawBody, new TypeToken<Map<String, Object>>() {}.getType());
            if (json == null) {
                sendJson(exchange, 400, mapOf("error", ERROR_INVALID_JSON));
                return null;
            }
            return json;
        } catch (Exception e) {
            sendJson(exchange, 400, mapOf("error", "Invalid request body: " + e.getMessage()));
            return null;
        }
    }

    /**
     * Sends a JSON error response with a single "error" key.
     */
    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, mapOf("error", message));
    }

    public static Map<String, Object> mapOf(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    public static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Extract string from JSON map; null if missing or null. */
    public static String stringOrNull(Map<String, Object> json, String key) {
        if (json == null) return null;
        Object v = json.get(key);
        if (v == null) return null;
        return v.toString();
    }

    /** Parse number from JSON value (Number or String); null if missing or invalid. */
    public static Integer numberToInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try {
                return (int) Double.parseDouble((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Parse number from JSON value; returns defaultValue if missing or invalid. Use for int when null is not needed. */
    public static int intOrDefault(Object v, int defaultValue) {
        Integer n = numberToInt(v);
        return n != null ? n : defaultValue;
    }
}
