package com.akto.action;

import com.akto.action.guardrail.ParsedRequest;
import com.akto.action.guardrail.ProviderAdapter;
import com.akto.action.guardrail.ProviderRegistry;
import com.akto.dao.context.Context;
import com.akto.gateway.Gateway;
import com.akto.util.DeviceLabelUtil;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.HashMap;
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

            // Non-inspectable event or empty prompt -> allow without validating.
            if (frame.allowShortCircuit || frame.prompt == null || frame.prompt.isEmpty()) {
                response = adapter.verdict(true, null, frame.requestId);
                return Action.SUCCESS.toUpperCase();
            }

            Map<String, Object> requestData = buildEnvelope(frame);
            requestData.put("guardrails", "true");
            requestData.put("ingest_data", "true");

            Map<String, Object> result = gateway.processHttpProxy(requestData);
            Map<String, Object> guardrailsResult = extractGuardrailsResult(result);

            boolean allow = guardrailsResult == null || isAllowed(guardrailsResult);
            String reason = guardrailsResult == null ? null : stringOrNull(guardrailsResult.get("Reason"));
            response = adapter.verdict(allow, reason, frame.requestId);
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

    /**
     * Build the flat Akto envelope for processHttpProxy. Host names the collection
     * (one per provider); important metadata (webhook/request id, session, tenant,
     * model, actor) is carried through so it is recorded and available to policies.
     */
    private Map<String, Object> buildEnvelope(ParsedRequest frame) {
        String host = buildAgentHost(frame);

        Map<String, Object> requestHeaders = new HashMap<>();
        requestHeaders.put("host", host);
        requestHeaders.put("content-type", "application/json");

        Map<String, Object> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");

        // Match the http-proxy/browser payload shape: user text under "body".
        // model / session_id are carried in metadata (below), not here.
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("body", frame.prompt);

        Map<String, Object> tag = new HashMap<>();
        tag.put("gen-ai", "Gen AI");
        tag.put("ai-agent", provider.toLowerCase());
        tag.put("source", "ENDPOINT");

        Map<String, Object> meta = new HashMap<>();
        meta.put("webhook_id", frame.requestId);
        meta.put("session_id", frame.sessionId);
        meta.put("tenant_id", frame.tenantId);
        meta.put("model", frame.model);
        meta.put("actor", frame.actor);
        // Advisory only (e.g. claude-code = CLI, claude-ai = app). Recorded for
        // visibility/routing; never used in the allow/deny decision.
        meta.put("application", frame.application);
        if (frame.metadata != null) {
            meta.put("provider_metadata", frame.metadata);
        }

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("akto_connector", provider.toLowerCase());
        envelope.put("path", "/" + provider.toLowerCase() + "/guardrail");
        envelope.put("method", "POST");
        envelope.put("requestPayload", toJson(requestPayload));
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
        Object guardrailsResult = result.get("guardrailsResult");
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
     * Agent collection hostname, built server-side as {emailSlug}.ai-agent.{app}.
     * Mirrors the client-side endpoint-shield convention
     * ({deviceLabel}.ai-agent.{agentName}, see device.go GetAgentCollectionName)
     * but uses the incoming email as the slug because the device name and
     * machine-id are not available in the webhook — the machine-id hash suffix
     * is intentionally omitted.
     */
    private static String buildAgentHost(ParsedRequest frame) {
        String emailSlug = DeviceLabelUtil.fromEmail(extractEmail(frame.actor));
        if (emailSlug.isEmpty()) {
            emailSlug = "unknown";
        }
        return emailSlug + ".ai-agent." + normalizeApp(frame.application);
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
        return DeviceLabelUtil.slugify(a);
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
