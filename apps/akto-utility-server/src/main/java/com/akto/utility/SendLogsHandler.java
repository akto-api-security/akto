package com.akto.utility;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.Log;

import java.io.IOException;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/sendLogs. Central endpoint for logging from agentic testing (or other) modules.
 * Request body: { "type": "info"|"error", "message": "string", "timestamp": epochSeconds, "moduleType": "AGENTIC_TESTING" }.
 * Timestamp uses Context.now() (epoch seconds) when not provided. Persists via DataActor.insertAgenticTestingLog (LogDb.AGENTIC_TESTING).
 */
public class SendLogsHandler implements HttpHandler {

    private static final String LOG_KEY_INFO = "info";
    private static final String LOG_KEY_ERROR = "error";

    private final DataActor dataActor;

    public SendLogsHandler(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        Map<String, Object> json = HttpUtil.parseJsonBody(rawBody, exchange);
        if (json == null) return;

        String type = HttpUtil.stringOrNull(json, "type");
        String message = HttpUtil.stringOrNull(json, "message");
        int ts = HttpUtil.intOrDefault(json.get("timestamp"), Context.now());
        String moduleTypeStr = HttpUtil.stringOrNull(json, "moduleType");

        if (message == null) {
            message = "";
        }

        String key = LOG_KEY_INFO;
        if ("error".equalsIgnoreCase(type != null ? type.trim() : "")) {
            key = LOG_KEY_ERROR;
        }
        String moduleType = (moduleTypeStr != null && !moduleTypeStr.trim().isEmpty()) ? moduleTypeStr.trim() : "AGENTIC_TESTING";

        Log log = new Log(message, key, ts);

        try {
            switch (moduleType) {
                case "AGENTIC_TESTING":
                    dataActor.insertAgenticTestingLog(log);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 500, "Failed to save log: " + e.getMessage());
            return;
        }

        HttpUtil.sendJson(exchange, 200, HttpUtil.mapOf("ok", true));
    }
}
