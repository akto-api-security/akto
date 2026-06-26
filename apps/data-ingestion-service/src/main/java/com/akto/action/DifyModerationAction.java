package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements Dify's Moderation API Extension contract directly inside the
 * data-ingestion-service, so no separate adapter service is required.
 *
 * Dify POSTs {@code { "point": <extension_point>, "params": {...} }} and expects a
 * moderation verdict back (see
 * https://docs.dify.ai/en/use-dify/workspace/api-extension/moderation-api-extension).
 *
 * This action translates input/output moderation calls into the same flat envelope
 * consumed by {@link Gateway#processHttpProxy(Map)} (guardrails + ingestion) and maps
 * Akto's {@code guardrailsResult} back into Dify's moderation response.
 *
 * It is fail-open: if guardrails/ingestion error out, content is allowed through.
 */
@lombok.Getter
@lombok.Setter
public class DifyModerationAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DifyModerationAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String AKTO_CONNECTOR_NAME = "dify";
    private static final String DEFAULT_PRESET_RESPONSE = "Your content violates our usage policy.";

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    // Dify request body: { "point": ..., "params": { ... } }
    private String point;
    private Map<String, Object> params;

    // Serialised directly as the response body (see struts.xml root param)
    private Map<String, Object> difyResponse;

    public String moderation() {
        try {
            if ("ping".equals(point)) {
                difyResponse = new HashMap<>();
                difyResponse.put("result", "pong");
                return Action.SUCCESS.toUpperCase();
            }

            if (params == null) {
                params = new HashMap<>();
            }

            if ("app.moderation.input".equals(point)) {
                difyResponse = handleInput();
            } else if ("app.moderation.output".equals(point)) {
                difyResponse = handleOutput();
            } else {
                loggerMaker.info("Dify: unsupported extension point: " + point);
                difyResponse = allowResponse();
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            // Fail-open: never let guardrails break the host app.
            loggerMaker.errorAndAddToDb("Error in Dify moderation action (fail-open): " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            difyResponse = allowResponse();
            return Action.SUCCESS.toUpperCase();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleInput() {
        String appId = stringOrDefault(params.get("app_id"), "unknown");

        Map<String, Object> content = new HashMap<>();
        content.put("inputs", params.getOrDefault("inputs", new HashMap<>()));
        content.put("query", params.get("query"));

        Map<String, Object> requestData = buildEnvelope(appId,
            "/dify/apps/" + appId + "/moderation/input", content, null);
        requestData.put("guardrails", "true");
        requestData.put("ingest_data", "true");

        Map<String, Object> result = gateway.processHttpProxy(requestData);
        Map<String, Object> guardrailsResult = extractGuardrailsResult(result);

        if (guardrailsResult == null) {
            return allowResponse();
        }

        if (!isAllowed(guardrailsResult)) {
            return blockedResponse(stringOrDefault(guardrailsResult.get("Reason"), DEFAULT_PRESET_RESPONSE));
        }

        Map<String, Object> modifiedBody = extractModifiedBody(guardrailsResult.get("ModifiedPayload"));
        if (modifiedBody != null) {
            Map<String, Object> overridden = new HashMap<>();
            overridden.put("flagged", true);
            overridden.put("action", "overridden");
            overridden.put("inputs", modifiedBody.getOrDefault("inputs", params.getOrDefault("inputs", new HashMap<>())));
            overridden.put("query", modifiedBody.getOrDefault("query", params.get("query")));
            return overridden;
        }

        return allowResponse();
    }

    private Map<String, Object> handleOutput() {
        String appId = stringOrDefault(params.get("app_id"), "unknown");
        Object text = params.getOrDefault("text", "");

        // Gateway requires a non-empty requestPayload; Dify does not pass the original
        // query on output, so reuse the output text as the request body context.
        Map<String, Object> requestData = buildEnvelope(appId,
            "/dify/apps/" + appId + "/moderation/output", text, text);
        requestData.put("response_guardrails", "true");
        requestData.put("ingest_data", "true");

        Map<String, Object> result = gateway.processHttpProxy(requestData);
        Map<String, Object> guardrailsResult = extractGuardrailsResult(result);

        if (guardrailsResult == null) {
            return allowResponse();
        }

        if (!isAllowed(guardrailsResult)) {
            return blockedResponse(stringOrDefault(guardrailsResult.get("Reason"), DEFAULT_PRESET_RESPONSE));
        }

        Object modifiedBody = extractModifiedBodyRaw(guardrailsResult.get("ModifiedPayload"));
        if (modifiedBody != null) {
            Map<String, Object> overridden = new HashMap<>();
            overridden.put("flagged", true);
            overridden.put("action", "overridden");
            overridden.put("text", modifiedBody instanceof String ? modifiedBody : text);
            return overridden;
        }

        return allowResponse();
    }

    /**
     * Build the flat traffic envelope consumed by Gateway.processHttpProxy().
     * A non-empty requestPayload is always required by the gateway.
     */
    private Map<String, Object> buildEnvelope(String appId, String path, Object requestBody, Object responseBody) {
        String host = appId + ".dify.agent";

        Map<String, Object> tags = new HashMap<>();
        tags.put("gen-ai", "Gen AI");
        tags.put("ai-agent", AKTO_CONNECTOR_NAME);
        tags.put("source", "AGENTIC");

        Map<String, Object> requestHeaders = new HashMap<>();
        requestHeaders.put("host", host);
        requestHeaders.put("content-type", "application/json");

        Map<String, Object> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", "application/json");

        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("body", requestBody);

        String responsePayload = "{}";
        if (responseBody != null) {
            Map<String, Object> respBodyMap = new HashMap<>();
            respBodyMap.put("body", responseBody);
            responsePayload = toJsonString(respBodyMap);
        }

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("akto_connector", AKTO_CONNECTOR_NAME);
        envelope.put("path", path);
        envelope.put("method", "POST");
        envelope.put("requestPayload", toJsonString(requestPayload));
        envelope.put("responsePayload", responsePayload);
        envelope.put("requestHeaders", toJsonString(requestHeaders));
        envelope.put("responseHeaders", toJsonString(responseHeaders));
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
        envelope.put("tag", toJsonString(tags));
        envelope.put("metadata", toJsonString(tags));
        envelope.put("contextSource", "AGENTIC");
        return envelope;
    }

    private static String resolveAccountId() {
        Integer accountId = Context.accountId.get();
        return accountId != null ? String.valueOf(accountId) : "1000000";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractGuardrailsResult(Map<String, Object> result) {
        if (result == null) return null;
        Object guardrailsResult = result.get("guardrailsResult");
        if (guardrailsResult instanceof Map) {
            return (Map<String, Object>) guardrailsResult;
        }
        return null;
    }

    private static boolean isAllowed(Map<String, Object> guardrailsResult) {
        Object val = guardrailsResult.get("Allowed");
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return Boolean.parseBoolean(val.toString());
        // Default to allowed when guardrails did not return a verdict (fail-open).
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractModifiedBody(Object modifiedPayload) {
        Object body = extractModifiedBodyRaw(modifiedPayload);
        return body instanceof Map ? (Map<String, Object>) body : null;
    }

    /**
     * Akto returns the full modified request as {@code {"body": ...}} in ModifiedPayload.
     */
    @SuppressWarnings("unchecked")
    private Object extractModifiedBodyRaw(Object modifiedPayload) {
        if (modifiedPayload == null) return null;
        try {
            Map<String, Object> obj;
            if (modifiedPayload instanceof String) {
                String str = (String) modifiedPayload;
                if (str.isEmpty()) return null;
                obj = objectMapper.readValue(str, Map.class);
            } else if (modifiedPayload instanceof Map) {
                obj = (Map<String, Object>) modifiedPayload;
            } else {
                return null;
            }
            return obj.containsKey("body") ? obj.get("body") : obj;
        } catch (Exception e) {
            loggerMaker.info("Dify: failed to parse ModifiedPayload: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> allowResponse() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("flagged", false);
        resp.put("action", "direct_output");
        resp.put("preset_response", "");
        return resp;
    }

    private static Map<String, Object> blockedResponse(String reason) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("flagged", true);
        resp.put("action", "direct_output");
        resp.put("preset_response", (reason == null || reason.isEmpty()) ? DEFAULT_PRESET_RESPONSE : reason);
        return resp;
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) return fallback;
        String str = value.toString();
        return str.isEmpty() ? fallback : str;
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            loggerMaker.error("Dify: failed to serialize to JSON: " + e.getMessage(), e);
            return "{}";
        }
    }
}
