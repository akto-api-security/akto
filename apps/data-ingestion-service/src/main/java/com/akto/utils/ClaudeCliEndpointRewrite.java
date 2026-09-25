package com.akto.utils;

import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

import java.util.Map;

/**
 * Treats Claude Code traffic that reaches Akto through the LiteLLM connector as Atlas (endpoint)
 * traffic, the same as the native Claude CLI hooks send it. Atlas keys on three things, and the
 * LiteLLM hook sets none of them:
 * <ul>
 *   <li>contextSource ENDPOINT: which guardrail policies the guardrails service loads;</li>
 *   <li>host {identity}.ai-agent.claudecli (MCP tool calls: {identity}.claudecli.{server}):
 *       agent/device-scoped policy matching and the collection name;</li>
 *   <li>tag source=ENDPOINT, plus ai-agent / mcp-client = claudecli: Atlas collection placement
 *       and agent grouping (the envelope contextSource is not carried past ingestion).</li>
 * </ul>
 * The identity (first host segment) is, in order: the user's email local part when the traffic
 * carries an email; else Claude Code's own device id (the hook's client_device_id tag, from the
 * Anthropic metadata.user_id Claude Code sends), shortened; else the host the hook sent (the
 * LiteLLM agent name or proxy host). The hook tags verdict, ingest and tool-call traffic alike, so
 * every call of a conversation lands on the same host.
 */
public final class ClaudeCliEndpointRewrite {

    static final String LITELLM_CONNECTOR = "litellm";
    static final String CLAUDE_CLI_USER_AGENT_PREFIX = "claude-cli/";
    // Same agent segment the native Claude CLI hooks use (AKTO_CONNECTOR_VALUE), so policies
    // scoped to the Claude CLI agent match both.
    static final String CLAUDE_CLI_AGENT = "claudecli";
    static final String INSTALLER_USER_EMAIL_HEADER = "x-akto-installer-user_email";
    static final String SPEND_LOGS_METADATA_HEADER = "x-litellm-spend-logs-metadata";
    static final String USER_EMAIL_KEY = "user_email";
    static final String MCP_SERVER_NAME_TAG = "mcp_server_name";
    static final String DEVICE_ID_TAG = "client_device_id";
    // Claude Code's device id is 64 hex characters, one more than a host label allows; a 16-character
    // prefix still tells installs apart.
    static final int DEVICE_ID_LENGTH = 16;

    private ClaudeCliEndpointRewrite() {}

    /**
     * Rewrites contextSource, the host request header and the tag of requestData in place when it
     * is Claude Code traffic from the LiteLLM connector; anything else is left untouched.
     */
    public static void apply(Map<String, Object> requestData) {
        if (!LITELLM_CONNECTOR.equalsIgnoreCase(asString(requestData.get("akto_connector")))) {
            return;
        }
        BasicDBObject headers = parseObject(asString(requestData.get("requestHeaders")));
        String userAgent = header(headers, "user-agent");
        if (userAgent == null || !userAgent.toLowerCase().startsWith(CLAUDE_CLI_USER_AGENT_PREFIX)) {
            return;
        }
        BasicDBObject tag = parseObject(asString(requestData.get("tag")));

        String email = firstNonEmpty(
            header(headers, INSTALLER_USER_EMAIL_HEADER),
            tag.getString(USER_EMAIL_KEY),
            parseObject(header(headers, SPEND_LOGS_METADATA_HEADER)).getString(USER_EMAIL_KEY));
        String identity = email != null
            ? AgentHostUtils.emailLocalPart(email)
            : firstNonEmpty(shortDeviceId(tag.getString(DEVICE_ID_TAG)), header(headers, "host"));

        boolean mcp = tag.containsField(Constants.AKTO_MCP_SERVER_TAG);
        String host = mcp
            ? AgentHostUtils.identitySlug(identity) + "." + CLAUDE_CLI_AGENT + "." + AgentHostUtils.identitySlug(tag.getString(MCP_SERVER_NAME_TAG))
            : AgentHostUtils.agentHost(identity, CLAUDE_CLI_AGENT);

        putHeader(headers, "host", host);
        if (email != null && header(headers, INSTALLER_USER_EMAIL_HEADER) == null) {
            headers.put(INSTALLER_USER_EMAIL_HEADER, email);
        }
        tag.put(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
        tag.put(mcp ? Constants.AKTO_MCP_CLIENT_TAG : Constants.AKTO_AI_AGENT_TAG, CLAUDE_CLI_AGENT);

        requestData.put("requestHeaders", headers.toJson());
        requestData.put("tag", tag.toJson());
        requestData.put("contextSource", Constants.AKTO_ENDPOINT_SOURCE_VALUE);
    }

    private static String shortDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        String trimmed = deviceId.trim();
        return trimmed.length() > DEVICE_ID_LENGTH ? trimmed.substring(0, DEVICE_ID_LENGTH) : trimmed;
    }

    private static String header(BasicDBObject headers, String name) {
        for (String key : headers.keySet()) {
            if (name.equalsIgnoreCase(key)) {
                String value = headers.getString(key);
                return value == null || value.trim().isEmpty() ? null : value.trim();
            }
        }
        return null;
    }

    /** Sets a header, replacing an existing one whatever its casing. */
    private static void putHeader(BasicDBObject headers, String name, String value) {
        headers.keySet().removeIf(name::equalsIgnoreCase);
        headers.put(name, value);
    }

    private static BasicDBObject parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new BasicDBObject();
        }
        try {
            return BasicDBObject.parse(json);
        } catch (Exception e) {
            return new BasicDBObject();
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }
}
