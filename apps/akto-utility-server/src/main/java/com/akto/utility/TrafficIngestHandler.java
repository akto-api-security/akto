package com.akto.utility;

import com.akto.ingest.TrafficIngestQueue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles POST /utility/ingestTraffic.
 *
 * Accepts a JSON array of traffic records or a single JSON object.
 * Each element must be a JSON object in the same format as Kafka traffic messages.
 * Data is enqueued into {@link TrafficIngestQueue} for processing by mini-runtime.
 *
 * Responses:
 *   200  {"queued": N}              — all messages accepted
 *   429  {"queued": N, "dropped": M} — queue full, some messages dropped
 *   400  {"error": "..."}           — malformed request
 */
public class TrafficIngestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;

        List<String> messages = extractMessages(rawBody.trim());
        if (messages == null || messages.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Expected JSON object or array of objects");
            return;
        }

        TrafficIngestQueue queue = TrafficIngestQueue.getInstance();
        int queued = 0;
        int dropped = 0;
        for (String msg : messages) {
            if (queue.offer(msg)) {
                queued++;
            } else {
                dropped++;
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("queued", queued);
        if (dropped > 0) {
            resp.put("dropped", dropped);
        }
        HttpUtil.sendJson(exchange, dropped > 0 ? 429 : 200, resp);
    }

    /**
     * Parses the request body into individual raw JSON strings.
     * Accepts a JSON array [{...}, {...}] or a single object {...}.
     */
    static List<String> extractMessages(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            if (body.charAt(0) == '[') {
                JsonArray arr = HttpUtil.gson().fromJson(body, JsonArray.class);
                List<String> msgs = new ArrayList<>(arr.size());
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        msgs.add(HttpUtil.gson().toJson(el));
                    }
                }
                return msgs.isEmpty() ? null : msgs;
            } else if (body.charAt(0) == '{') {
                List<String> msgs = new ArrayList<>(1);
                msgs.add(body);
                return msgs;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
