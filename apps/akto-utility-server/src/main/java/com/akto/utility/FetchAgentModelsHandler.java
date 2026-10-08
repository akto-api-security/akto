package com.akto.utility;

import com.akto.data_actor.DataActor;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/fetchAgentModels (body ignored). Relays the LLM models saved on the
 * dashboard's Agents configuration page, so co-located modules (e.g. red teaming) get
 * them without their own database-abstractor URL or token. Responds with the same JSON
 * list the database-abstractor's /api/fetchAgentModels returns: [{name, type, params}, ...],
 * or 502 when it couldn't be reached (so callers keep their current config).
 */
public class FetchAgentModelsHandler implements HttpHandler {

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
        HttpUtil.sendJson(exchange, 200, models);
    }
}
