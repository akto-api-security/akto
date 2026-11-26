package com.akto.action;

import com.akto.action.guardrail.ParsedRequest;
import com.akto.action.guardrail.ProviderAdapter;
import com.akto.action.guardrail.ProviderRegistry;
import com.akto.dao.context.Context;
import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.akto.util.Constants;
import com.akto.utils.AgentHostUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic AI-provider guardrail webhook: POST /api/v1/webhooks/{provider}/guardrail.
 *
 * The provider is a path variable ({@link #provider}, from the URL wildcard), not
 * a hardcoded endpoint — a {@link ProviderAdapter} looked up in
 * {@link ProviderRegistry} handles that provider's request/verdict format. The
 * shared core is identical for every provider: parse -> flatten the transcript ->
 * run guardrails AND ingest via {@link Gateway#processHttpProxy(Map)} with both
 * flags forced true (so an API collection is built) -> return the adapter's verdict.
 *
 * Adding ChatGPT (etc.) is one new adapter registered in ProviderRegistry — no
 * change here and no new struts action.
 *
 * Fail-open: any internal error returns the provider's allow verdict.
 */
@lombok.Getter
@lombok.Setter
public class ProviderGuardrailAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ProviderGuardrailAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String INFERENCE_HOOKS_TAG = "inference-hooks";
    private static final String AGENT_EMAIL_TAG = "ai-agent-email";
    private static final String TOOL_CALL_EVENT = "tool_call";
    private static final String ENDPOINT_PREFIX = "inference-hook-";
    private static final String INSTALLER_HEADER_PREFIX = "x-akto-installer-";
    private static final String SESSION_HEADER = INSTALLER_HEADER_PREFIX + "akto_session_id";
    private static final String MESSAGE_HEADER = INSTALLER_HEADER_PREFIX + "akto_message_id";
    private static final String USER_EMAIL_HEADER = INSTALLER_HEADER_PREFIX + "user_email";
    private static final String MCP_TOOL_PREFIX = "mcp__";
    private static final String MCP_SEPARATOR = "__";
    private static final String MCP_SERVER_TAG_VALUE = "MCP Server";
    private static final String MCP_PATH = "/mcp";
    private static final String TOOL_CALL_SEGMENT = "/tool-call";
    private static final String THIRD_PARTY_TOOL = "third_party";

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    // Set from the URL wildcard {1} via struts param.
    private String provider;

    // Serialized as the response body (the provider's verdict).
    private Map<String, Object> response;

    public String guardrail() {
        ProviderAdapter adapter = ProviderRegistry.get(provider);
        if (adapter == null) {
            loggerMaker.warn("Provider guardrail: unknown provider '" + provider + "'");
            response = new HashMap<>();
            response.put("error", "unknown provider");
            return Action.ERROR.toUpperCase();
        }

        try {
            Map<String, Object> body = readBody();
            ParsedRequest frame = adapter.parse(body);

            if (frame.allowShortCircuit || frame.prompt == null || frame.prompt.isEmpty()) {
                response = adapter.verdict(true, null, frame.requestId);
                return Action.SUCCESS.toUpperCase();
            }

            List<ParsedRequest.ToolCall> calls = frame.toolCalls != null
                    ? frame.toolCalls
                    : Collections.<ParsedRequest.ToolCall>singletonList(null);
            Map<String, Object> denied = null;
            for (ParsedRequest.ToolCall call : calls) {
                Map<String, Object> guardrailsResult = process(frame, call);
                if (guardrailsResult != null && !isAllowed(guardrailsResult)) {
                    denied = guardrailsResult;
                    break;
                }
            }

            String reason = denied == null ? null : stringOrNull(denied.get("Reason"));
            response = adapter.verdict(denied == null, reason, frame.requestId);
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            // Fail-open: never let guardrails break inference. Log to console only
            // (not the DB) — this is an inline hot path, and per-request DB writes
            // would amplify load during a downstream outage.
            loggerMaker.error("Provider guardrail error (fail-open) - provider: " + provider, e);
            response = adapter.verdict(true, null, null);
            return Action.SUCCESS.toUpperCase();
        }
    }

    private Map<String, Object> process(ParsedRequest frame, ParsedRequest.ToolCall call) {
        Map<String, Object> requestData = buildEnvelope(frame, call);
        // Judge only what is new; the full transcript in requestPayload is still ingested.
        if (call == null && frame.verdictPrompt != null) {
            requestData.put(Gateway.GUARDRAILS_REQUEST_PAYLOAD,
                    toJson(Collections.singletonMap("body", frame.verdictPrompt)));
        }
        // The model's last response, judged against response policies and recorded on the trace.
        // It already reached the user, so the verdict below ignores it (request side only).
        if (call == null && frame.lastResponse != null) {
            requestData.put("responsePayload", toJson(Collections.singletonMap("body", frame.lastResponse)));
            requestData.put("response_guardrails", "true");
        }
        requestData.put("guardrails", "true");
        requestData.put("ingest_data", "true");
        return extractGuardrailsResult(gateway.processHttpProxy(requestData));
    }

    private static String[] mcpTarget(ParsedRequest.ToolCall call) {
        if (call == null) {
            return null;
        }
        if (call.name.startsWith(MCP_TOOL_PREFIX)) {
            String rest = call.name.substring(MCP_TOOL_PREFIX.length());
            int sep = rest.indexOf(MCP_SEPARATOR);
            if (sep > 0 && sep + MCP_SEPARATOR.length() < rest.length()) {
                return new String[]{rest.substring(0, sep), rest.substring(sep + MCP_SEPARATOR.length())};
            }
        }
        if (call.info != null && THIRD_PARTY_TOOL.equals(call.info.get("type")) && call.info.get("toolset_name") != null) {
            String server = call.info.get("toolset_name").toString();
            if (!server.isEmpty()) {
                return new String[]{server, call.name};
            }
        }
        return null;
    }

    private static String toolPathSegment(String toolName) {
        String segment = toolName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return segment.isEmpty() ? "unknown" : segment;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value);
        }
    }

    private Map<String, Object> requestPayload(ParsedRequest frame, ParsedRequest.ToolCall call, String[] mcp) {
        Map<String, Object> payload = new HashMap<>();
        if (mcp != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("name", mcp[1]);
            params.put("arguments", call.input != null ? call.input : new HashMap<String, Object>());
            payload.put("jsonrpc", "2.0");
            payload.put("id", call.id);
            payload.put("method", "tools/call");
            payload.put("params", params);
        } else if (call != null) {
            payload.put("body", call.text);
            payload.put("tool_name", call.name);
            payload.put("tool_use_id", call.id);
        } else {
            payload.put("body", frame.prompt);
        }
        return payload;
    }

    /**
     * Build the flat Akto envelope for processHttpProxy. Host names the collection
     * (one per provider); important metadata (webhook/request id, session, tenant,
     * model, actor) is carried through so it is recorded and available to policies.
     */
    private Map<String, Object> buildEnvelope(ParsedRequest frame, ParsedRequest.ToolCall call) {
        String[] mcp = mcpTarget(call);
        String app = normalizeApp(frame.application);
        String host = mcp != null
                ? AgentHostUtils.mcpHost(endpointId(frame), app, mcp[0])
                : buildAgentHost(frame);

        Map<String, Object> requestHeaders = new HashMap<>();
        requestHeaders.put("host", host);
        requestHeaders.put("content-type", "application/json");
        putIfPresent(requestHeaders, SESSION_HEADER, frame.sessionId);
        putIfPresent(requestHeaders, MESSAGE_HEADER, frame.requestId);
        putIfPresent(requestHeaders, USER_EMAIL_HEADER, extractEmail(frame.actor));

        Map<String, Object> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");

        Map<String, Object> tag = new HashMap<>();
        tag.put("source", "ENDPOINT");
        tag.put(Constants.AKTO_GUARDRAIL_MODE, Constants.AKTO_GUARDRAIL_MODE_INLINE);
        tag.put(INFERENCE_HOOKS_TAG, provider.toLowerCase());
        if (mcp != null) {
            tag.put(Constants.AKTO_MCP_SERVER_TAG, MCP_SERVER_TAG_VALUE);
            tag.put("mcp-client", agentTagValue(app));
        } else {
            tag.put("gen-ai", "Gen AI");
            tag.put("ai-agent", agentTagValue(app));
        }
        if (frame.application != null && !frame.application.isEmpty()) {
            tag.put("product-surface", frame.application.toLowerCase().replaceAll("[^a-z0-9]+", "_"));
        }
        String usernameSlug = actorSlug(frame);
        if (!usernameSlug.isEmpty()) {
            tag.put("username", usernameSlug);
        }
        String email = extractEmail(frame.actor);
        if (email != null && !email.trim().isEmpty()) {
            tag.put(AGENT_EMAIL_TAG, email.trim());
            loggerMaker.info("Provider guardrail: tagged {} with {}={} for agent_users sync - provider: {}, request: {}",
                    host, AGENT_EMAIL_TAG, email.trim(), provider, frame.requestId);
        } else {
            loggerMaker.info("Provider guardrail: no actor email, {} not tagged and no agent_users row will be created - host: {}, provider: {}, request: {}",
                    AGENT_EMAIL_TAG, host, provider, frame.requestId);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("webhook_id", frame.requestId);
        meta.put("session_id", frame.sessionId);
        meta.put("tenant_id", frame.tenantId);
        meta.put("model", frame.model);
        meta.put("actor", frame.actor);
        meta.put("application", frame.application);
        if (frame.metadata != null) {
            meta.put("provider_metadata", frame.metadata);
        }
        meta.put("event_type", frame.eventType);
        if (call != null) {
            meta.put("tool_name", call.name);
            meta.put("tool_use_id", call.id);
            if (call.info != null) {
                meta.put("tool_info", call.info);
            }
        }
        if (mcp != null) {
            meta.put("mcp_server", mcp[0]);
        }

        String path = "/" + provider.toLowerCase() + "/guardrail";
        if (mcp != null) {
            path = MCP_PATH;
        } else if (call != null) {
            path += TOOL_CALL_SEGMENT + "/" + toolPathSegment(call.name);
        } else if (TOOL_CALL_EVENT.equals(frame.eventType)) {
            path += TOOL_CALL_SEGMENT;
        }

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("akto_connector", provider.toLowerCase());
        envelope.put("path", path);
        envelope.put("method", "POST");
        envelope.put("requestPayload", toJson(requestPayload(frame, call, mcp)));
        envelope.put("responsePayload", "{}");
        envelope.put("requestHeaders", toJson(requestHeaders));
        envelope.put("responseHeaders", toJson(responseHeaders));
        envelope.put("ip", "0.0.0.0");
        envelope.put("destIp", "127.0.0.1");
        envelope.put("time", String.valueOf(System.currentTimeMillis()));
        envelope.put("statusCode", "200");
        envelope.put("type", "HTTP/1.1");
        envelope.put("status", "200");
        envelope.put("akto_account_id", resolveAccountId());
        envelope.put("akto_vxlan_id", "0");
        envelope.put("is_pending", "false");
        envelope.put("source", "MIRRORING");
        envelope.put("tag", toJson(tag));
        envelope.put("metadata", toJson(meta));
        envelope.put("contextSource", "ENDPOINT");
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        String raw;
        try (BufferedReader reader = request.getReader()) {
            raw = reader.lines().collect(Collectors.joining("\n"));
        }
        if (raw == null || raw.trim().isEmpty()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(raw, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractGuardrailsResult(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        Object guardrailsResult = result.get(Gateway.REQUEST_GUARDRAILS_RESULT);
        return guardrailsResult instanceof Map ? (Map<String, Object>) guardrailsResult : null;
    }

    private static boolean isAllowed(Map<String, Object> guardrailsResult) {
        Object val = guardrailsResult.get("Allowed");
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val != null) {
            return Boolean.parseBoolean(val.toString());
        }
        return true; // fail-open when no verdict present
    }

    /**
     * Agent collection hostname, built server-side as inference-hook-{emailSlug}.ai-agent.{app}.
     * Mirrors the client-side endpoint-shield convention
     * ({deviceLabel}.ai-agent.{agentName}, see device.go GetAgentCollectionName)
     * but uses the incoming email as the slug because the device name and
     * machine-id are not available in the webhook — the machine-id hash suffix
     * is intentionally omitted.
     */
    private static String buildAgentHost(ParsedRequest frame) {
        return AgentHostUtils.agentHost(endpointId(frame), normalizeApp(frame.application));
    }

    // Endpoint ID is the first host segment; the prefix keeps hook endpoints apart from real devices.
    private static String endpointId(ParsedRequest frame) {
        return ENDPOINT_PREFIX + AgentHostUtils.identitySlug(actorIdentity(frame.actor));
    }

    private static String actorSlug(ParsedRequest frame) {
        return AgentHostUtils.slugify(actorIdentity(frame.actor));
    }

    private static String actorIdentity(Map<String, Object> actor) {
        String email = extractEmail(actor);
        if (email != null && !email.trim().isEmpty()) {
            return AgentHostUtils.emailLocalPart(email);
        }
        Object id = actor == null ? null : actor.get("id");
        return id != null ? id.toString() : null;
    }

    // Registry-known ai-agent values keep each Claude surface in its own group instead of folding everything into Claude CLI.
    private String agentTagValue(String app) {
        if ("claude".equalsIgnoreCase(provider)) {
            if ("claude-cli".equals(app)) {
                return "claude-cli";
            }
            if ("cowork".equals(app)) {
                return "claude_cowork";
            }
            if ("claude-app".equals(app)) {
                return "claude-app";
            }
        }
        return provider.toLowerCase();
    }


    private static String extractEmail(Map<String, Object> actor) {
        if (actor == null) {
            return null;
        }
        Object email = actor.get("email_address");
        if (email == null) {
            email = actor.get("email");
        }
        return email != null ? email.toString() : null;
    }

    /**
     * Map source.application to the collection's app segment. Advisory open
     * string: known values are mapped, anything else passes through slugified,
     * and it is never rejected.
     */
    private static String normalizeApp(String application) {
        if (application == null || application.isEmpty()) {
            return "unknown";
        }
        String a = application.toLowerCase();
        if (a.equals("claude-code") || a.startsWith("claude-cli")) {
            return "claude-cli";
        }
        if (a.equals("claude-ai")) {
            return "claude-app";
        }
        return AgentHostUtils.slugify(a);
    }

    private static String resolveAccountId() {
        Integer accountId = Context.accountId.get();
        return accountId != null ? String.valueOf(accountId) : "1000000";
    }

    private static String stringOrNull(Object v) {
        return v != null ? v.toString() : null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            loggerMaker.error("Provider guardrail: failed to serialize JSON: " + e.getMessage(), e);
            return "{}";
        }
    }
}
