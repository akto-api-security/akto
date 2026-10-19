package com.akto.jobs.executors.ibmwatsonx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only client for an IBM watsonx Orchestrate instance: agents, tools, the AI gateway
 * (models, MCP toolkits), controls, connections, agent identity and conversation traces.
 *
 * Every call signs in with the stored API key when needed, retries once after a 401 (expired
 * token) and up to {@link #MAX_ATTEMPTS} times on rate limits and IBM server errors.
 */
public class WatsonxClient {

    static final int MAX_ATTEMPTS = 3;
    /** IBM's limit for one traces query: fromTimestamp..toTimestamp may span at most 4 hours. */
    public static final long MAX_TRACE_WINDOW_MS = 4 * 60 * 60 * 1000L;
    public static final int TRACE_PAGE_SIZE = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String instanceUrl;
    private final WatsonxAuth auth;
    private final WatsonxTransport transport;
    long retryDelayMs = 2000;

    public WatsonxClient(String instanceUrl, WatsonxAuth auth, WatsonxTransport transport) {
        this.instanceUrl = normalizeInstanceUrl(instanceUrl);
        this.auth = auth;
        this.transport = transport;
    }

    /** The Service instance URL without a trailing slash; null if it is not one (must contain /instances/). */
    public static String normalizeInstanceUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.startsWith("https://") && trimmed.contains("/instances/") ? trimmed : null;
    }

    public String instanceUrl() {
        return instanceUrl;
    }

    // ---------------------------------------------------------------------------------------
    // Agents and tools
    // ---------------------------------------------------------------------------------------

    public JsonNode agents() throws IOException {
        return array(get("/v1/orchestrate/agents", null));
    }

    /** The deployed (live) definition of an agent, version as in its environments' current_version. */
    public JsonNode release(String agentId, String version) throws IOException {
        return optional("/v1/orchestrate/agents/" + agentId + "/releases/" + version, null);
    }

    public JsonNode tools() throws IOException {
        return array(get("/v1/orchestrate/tools", null));
    }

    // ---------------------------------------------------------------------------------------
    // Control plane (optional: not every plan / user can read every part)
    // ---------------------------------------------------------------------------------------

    public JsonNode externalAgents() throws IOException {
        return array(optional("/v1/orchestrate/agents/external-chat", null));
    }

    public JsonNode toolkits() throws IOException {
        return array(optional("/v1/orchestrate/toolkits", null));
    }

    public JsonNode models() throws IOException {
        return array(field(optional("/v1/orchestrate/models/list", null), "resources"));
    }

    public JsonNode controlTypes() throws IOException {
        return array(field(optional("/v1/orchestrate/policies/artifacts", null), "artifacts"));
    }

    public JsonNode controlBindings() throws IOException {
        return array(field(optional("/v1/orchestrate/policies/bindings", null), "bindings"));
    }

    public JsonNode connections() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("limit", "500");
        return array(field(optional("/v1/orchestrate/connections/applications", params), "applications"));
    }

    /**
     * Agent identity and permissions as Orchestrate's Security control center shows them (creator,
     * last updater, connections with scopes, accessible tools). Served by Orchestrate's UI backend:
     * not a documented API, so any failure only drops these details (returns null).
     */
    public JsonNode identity(String agentId, boolean external) {
        try {
            String host = URI.create(instanceUrl).getHost();
            String uiHost = host.startsWith("api.") ? host.substring("api.".length()) : host;
            Map<String, String> params = new HashMap<>();
            params.put("agent_category", external ? "external" : "native");
            WatsonxTransport.Response response = call("https://" + uiHost + "/mfe_connections/api/v1/connections/agents/"
                    + agentId + "/identity", params);
            return response.ok() ? MAPPER.readTree(response.body) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Conversation traces
    // ---------------------------------------------------------------------------------------

    /** One page of traces with their steps (observations) inline, oldest first. */
    public JsonNode tracesPage(String fromIso, String toIso, int page) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("fromTimestamp", fromIso);
        params.put("toTimestamp", toIso);
        params.put("orderBy", "timestamp.asc");
        params.put("page", String.valueOf(page));
        params.put("limit", String.valueOf(TRACE_PAGE_SIZE));
        params.put("include_trace_details", "true");
        return get("/v1/agentops-v3/traces", params);
    }

    // ---------------------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------------------

    JsonNode get(String path, Map<String, String> params) throws IOException {
        WatsonxTransport.Response response = call(instanceUrl + path, params);
        if (!response.ok()) {
            throw new WatsonxApiException(response.status, "IBM watsonx Orchestrate answered HTTP " + response.status
                    + " for " + path + ": " + abbreviate(response.body), false);
        }
        return response.body.isEmpty() ? JsonNodeFactory.instance.objectNode() : MAPPER.readTree(response.body);
    }

    /** Like {@link #get}, but a resource this tenant or user cannot read (403/404) is null instead of an error. */
    JsonNode optional(String path, Map<String, String> params) throws IOException {
        try {
            return get(path, params);
        } catch (WatsonxApiException e) {
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private WatsonxTransport.Response call(String url, Map<String, String> params) throws IOException {
        String fullUrl = url + query(params);
        boolean reSignedIn = false;
        for (int attempt = 1; ; attempt++) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + auth.token());
            headers.put("Accept", "application/json");
            WatsonxTransport.Response response;
            try {
                response = transport.send("GET", fullUrl, headers, null, null);
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new WatsonxApiException("Could not reach IBM watsonx Orchestrate: " + e.getMessage(), e);
                }
                pause(attempt);
                continue;
            }
            if (response.status == 401 && !reSignedIn) {
                auth.invalidate();   // token expired or was revoked: sign in again once
                reSignedIn = true;
                continue;
            }
            if (response.status == 401) {
                throw new WatsonxApiException(401, WatsonxAuth.KEY_REJECTED, true);
            }
            if ((response.status == 429 || response.status >= 500) && attempt < MAX_ATTEMPTS) {
                pause(attempt);
                continue;
            }
            return response;
        }
    }

    private void pause(int attempt) throws IOException {
        try {
            Thread.sleep(retryDelayMs * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static String query(Map<String, String> params) throws UnsupportedEncodingException {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> param : params.entrySet()) {
            query.append(query.length() == 0 ? '?' : '&')
                    .append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8.name())).append('=')
                    .append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8.name()));
        }
        return query.toString();
    }

    private static JsonNode field(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        return node.isArray() ? node : node.get(name);
    }

    private static JsonNode array(JsonNode node) {
        return node != null && node.isArray() ? node : JsonNodeFactory.instance.arrayNode();
    }

    private static String abbreviate(String text) {
        return text == null ? "" : (text.length() > 300 ? text.substring(0, 300) + "…" : text);
    }
}
