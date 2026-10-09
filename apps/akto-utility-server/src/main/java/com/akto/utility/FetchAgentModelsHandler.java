package com.akto.utility;

import com.akto.data_actor.DataActor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/fetchAgentModels (body ignored). Relays the LLM models saved on the
 * dashboard's Agents configuration page, so co-located modules (e.g. red teaming) get
 * them without their own database-abstractor URL or token. Responds with the "red-teaming"
 * entries (name trimmed, case-insensitive) of the database-abstractor's /api/fetchAgentModels
 * list: [{name, type, params}, ...], or 502 when it couldn't be reached (so callers keep
 * their current config).
 */
public class FetchAgentModelsHandler implements HttpHandler {

    private static final String RED_TEAMING_MODEL_NAME = "red-teaming";

    private final DataActor dataActor;

    public FetchAgentModelsHandler(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        List<Map<String, Object>> models = dataActor.fetchAgentModels();
        if (models == null) {
            HttpUtil.sendError(exchange, 502, "Failed to fetch agent models");
            return;
        }
        // Only the model red teaming uses; don't relay every saved provider key.
        List<Map<String, Object>> redTeaming = new ArrayList<>();
        for (Map<String, Object> m : models) {
            Object name = m.get("name");
            if (name instanceof String && ((String) name).trim().equalsIgnoreCase(RED_TEAMING_MODEL_NAME)) {
                redTeaming.add(m);
            }
        }
        HttpUtil.sendJson(exchange, 200, redTeaming);
    }
}
