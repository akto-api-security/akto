package com.akto.utility;

import com.akto.data_actor.DataActor;
import com.akto.utils.elasticsearch.AgentQueryRecord;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/agentQueryRecords. Request body: { "messageId": "..." }.
 */
public class FetchAgentQueryRecordsHandler implements HttpHandler {

    private final DataActor dataActor;

    public FetchAgentQueryRecordsHandler(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        Map<String, Object> json = HttpUtil.parseJsonBody(rawBody, exchange);
        if (json == null) return;

        Object v = json.get("messageId");
        String messageId = v == null ? null : v.toString().trim();
        if (messageId == null || messageId.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "messageId is required");
            return;
        }

        List<AgentQueryRecord> records = dataActor.fetchAgentQueryRecords(messageId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("messageId", messageId);
        resp.put("agentQueryRecords", records);
        HttpUtil.sendJson(exchange, 200, resp);
    }
}
