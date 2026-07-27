package com.akto.utility;

import com.akto.agent.ApiExecutionJobStore;
import com.akto.data_actor.DataActor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/agentQueryValidationResult. Request body: { "messageId": "...", "toolsMetadata": {...} }.
 * messageId is the x-akto-message-id used for the target-API call; it's resolved back to the
 * AgentConversationResult doc that call belongs to (linked in ApiExecutionJobStore when the
 * conversation result was stored), and that doc's toolsMetadata is replaced with the given value.
 */
public class AgentQueryValidationResultHandler implements HttpHandler {

    private final ApiExecutionJobStore jobStore;
    private final DataActor dataActor;

    public AgentQueryValidationResultHandler(ApiExecutionJobStore jobStore, DataActor dataActor) {
        this.jobStore = jobStore;
        this.dataActor = dataActor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        Map<String, Object> json = HttpUtil.parseJsonBody(rawBody, exchange);
        if (json == null) return;

        String messageId = HttpUtil.stringOrNull(json, "messageId");
        if (messageId == null || messageId.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "messageId is required");
            return;
        }

        Object toolsMetadataObj = json.get("toolsMetadata");
        if (!(toolsMetadataObj instanceof Map)) {
            HttpUtil.sendError(exchange, 400, "toolsMetadata is required");
            return;
        }

        String docId = jobStore.getConversationResultDocId(messageId);
        if (docId == null) {
            HttpUtil.sendError(exchange, 404, "no conversation result found for messageId " + messageId);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> toolsMetadata = (Map<String, Object>) toolsMetadataObj;
        dataActor.updateAgentConversationToolsMetadata(docId, toolsMetadata);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("messageId", messageId);
        resp.put("docId", docId);
        resp.put("status", "updated");
        HttpUtil.sendJson(exchange, 200, resp);
    }
}
