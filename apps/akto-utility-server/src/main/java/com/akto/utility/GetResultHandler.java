package com.akto.utility;

import com.akto.dto.OriginalHttpResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/result. Request body: { "jobId": "..." }. Returns execution result (200), pending (202), or not found (404).
 */
public class GetResultHandler implements HttpHandler {

    private final ApiExecutionJobStore jobStore;

    public GetResultHandler(ApiExecutionJobStore jobStore) {
        this.jobStore = jobStore;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        Map<String, Object> json = HttpUtil.parseJsonBody(rawBody, exchange);
        if (json == null) return;

        Object v = json.get("jobId");
        String jobId = v == null ? null : v.toString().trim();
        if (jobId == null || jobId.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "jobId is required");
            return;
        }

        ApiExecutionJobStore.JobEntry entry = jobStore.get(jobId);
        if (entry == null) {
            HttpUtil.sendJson(exchange, 404, HttpUtil.mapOf("error", "Job not found or expired", "jobId", jobId));
            return;
        }

        Map<String, Object> baseResp = new LinkedHashMap<>();
        baseResp.put("jobId", jobId);
        if (entry.getConversationId() != null) {
            baseResp.put("conversationId", entry.getConversationId());
        }

        switch (entry.getStatus()) {
            case PENDING:
                baseResp.put("status", "pending");
                HttpUtil.sendJson(exchange, 202, baseResp);
                return;
            case COMPLETED:
                baseResp.put("status", "completed");
                OriginalHttpResponse resp = entry.getResponse();
                if (resp != null) {
                    baseResp.put("statusCode", resp.getStatusCode());
                    baseResp.put("headers", resp.getHeaders());
                    baseResp.put("body", resp.getBody());
                }
                HttpUtil.sendJson(exchange, 200, baseResp);
                return;
            case FAILED:
                baseResp.put("status", "failed");
                baseResp.put("error", entry.getErrorMessage() != null ? entry.getErrorMessage() : "Execution failed");
                HttpUtil.sendJson(exchange, 200, baseResp);
                return;
            default:
                HttpUtil.sendError(exchange, 500, "Unknown job status");
        }
    }
}
