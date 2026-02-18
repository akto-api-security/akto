package com.akto.utility;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.testing.ApiExecutor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/execute. Request body: { "url", "method", "body", "headers", "conversationId" }.
 * headers is a JSON string (e.g. "{\"Content-Type\":\"application/json\"}"). Query params go in url.
 * conversationId is stored with the job for correlation.
 */
public class ExecuteApiHandler implements HttpHandler {

    private final ApiExecutionJobStore jobStore;
    private final ExecutorService executorService;

    public ExecuteApiHandler(ApiExecutionJobStore jobStore, ExecutorService executorService) {
        this.jobStore = jobStore;
        this.executorService = executorService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        Map<String, Object> json = HttpUtil.parseJsonBody(rawBody, exchange);
        if (json == null) return;

        String url = HttpUtil.stringOrNull(json, "url");
        String method = HttpUtil.stringOrNull(json, "method");
        String body = HttpUtil.stringOrNull(json, "body");
        String conversationId = HttpUtil.stringOrNull(json, "conversationId");
        Map<String, List<String>> headers = headersFromJson(json.get("headers"));

        if (url == null || url.isEmpty() || method == null || method.isEmpty()) {
            HttpUtil.sendError(exchange, 400, "url and method are required");
            return;
        }

        url = normalizeUrl(url);

        OriginalHttpRequest request = new OriginalHttpRequest(
                url,
                "",
                method,
                body != null ? body : "",
                headers,
                ""
        );

        String jobId = UUID.randomUUID().toString();
        jobStore.put(jobId, ApiExecutionJobStore.JobEntry.pending(conversationId));

        executorService.submit(() -> {
            try {
                Context.accountId.remove();
                TestingRunConfig testingRunConfig = new TestingRunConfig(0, new HashMap<>(), new ArrayList<>(), null, null, null);
                testingRunConfig.setConversationId(conversationId);
                OriginalHttpResponse response = ApiExecutor.sendRequest(
                        request,
                        true,
                        testingRunConfig,
                        false,
                        Collections.emptyList()
                );
                jobStore.update(jobId, ApiExecutionJobStore.JobEntry.completed(response, conversationId));
            } catch (Throwable t) {
                jobStore.update(jobId, ApiExecutionJobStore.JobEntry.failed(t.getMessage(), conversationId));
            } finally {
                Context.accountId.remove();
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId);
        response.put("url", url);
        response.put("method", method);
        if (conversationId != null) {
            response.put("conversationId", conversationId);
        }
        HttpUtil.sendJson(exchange, 200, response);
    }

    /**
     * Normalize URL so ApiExecutor handles all common forms:
     * - https://google.com/hello/world → unchanged
     * - /hello/world → unchanged
     * - hello/world → /hello/world
     */
    private static String normalizeUrl(String url) {
        if (url == null) return url;
        String u = url.trim();
        if (u.isEmpty() || u.startsWith("http://") || u.startsWith("https://") || u.startsWith("/")) {
            return u;
        }
        return "/" + u;
    }

    private static Map<String, List<String>> headersFromJson(Object headersObj) {
        if (headersObj == null) {
            return Collections.emptyMap();
        }
        if (headersObj instanceof String) {
            String s = ((String) headersObj).trim();
            return s.isEmpty() ? Collections.<String, List<String>>emptyMap() : OriginalHttpRequest.buildHeadersMap(s);
        }
        if (headersObj instanceof Map) {
            return OriginalHttpRequest.buildHeadersMap(HttpUtil.gson().toJson(headersObj));
        }
        return Collections.emptyMap();
    }
}
