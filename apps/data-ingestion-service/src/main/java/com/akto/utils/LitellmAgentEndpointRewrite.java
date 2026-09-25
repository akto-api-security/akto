package com.akto.utils;

import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Treats coding-agent traffic that reaches Akto through the LiteLLM connector (Akto's custom hook or
 * LiteLLM's built-in Akto guardrail) as Atlas (endpoint) traffic, the same as that agent's native
 * Akto connector sends it. The agent is recognised by its User-Agent (see AGENTS). Atlas keys on
 * three things, and the LiteLLM connector sets none of them:
 * <ul>
 *   <li>contextSource ENDPOINT: which guardrail policies the guardrails service loads;</li>
 *   <li>host {identity}.ai-agent.{agent} (MCP tool calls: {identity}.{agent}.{server}):
 *       agent/device-scoped policy matching and the collection name;</li>
 *   <li>tag source=ENDPOINT, plus ai-agent / mcp-client = {agent}: Atlas collection placement
 *       and agent grouping (the envelope contextSource is not carried past ingestion).</li>
 * </ul>
 * The identity (first host segment) is, in order: the user's email local part when the traffic
 * carries an email; else the client's device id (the client_device_id tag, e.g. from the Anthropic
 * metadata.user_id Claude Code sends), shortened; else the host the connector sent (the LiteLLM
 * agent name or proxy host). Every call of a conversation carries the same inputs, so it lands on
 * the same host.
 */
public final class LitellmAgentEndpointRewrite {

    static final String LITELLM_CONNECTOR = "litellm";
    // User-Agent prefix -> agent name: the agent's native Akto connector name (AKTO_CONNECTOR_VALUE)
    // plus a -litellm suffix, so traffic through LiteLLM shows as its own agent in Atlas.
    static final String LITELLM_AGENT_SUFFIX = "-litellm";
    static final Map<String, String> AGENTS = new LinkedHashMap<>();
    static {
        AGENTS.put("claude-cli/", "claudecli" + LITELLM_AGENT_SUFFIX);
        AGENTS.put("opencode/", "opencode" + LITELLM_AGENT_SUFFIX);
    }
    // Any LiteLLM client can opt into Atlas by sending this header with ENDPOINT, e.g. an agent not
    // in AGENTS; its agent name is then taken from its User-Agent (see agentFromUserAgent).
    static final String CONTEXT_SOURCE_HEADER = "x-akto-contextsource";
    static final String UNKNOWN_AGENT = "unknown";
    static final String INSTALLER_USER_EMAIL_HEADER = "x-akto-installer-user_email";
    static final String SPEND_LOGS_METADATA_HEADER = "x-litellm-spend-logs-metadata";
    static final String USER_EMAIL_KEY = "user_email";
    static final String MCP_SERVER_NAME_TAG = "mcp_server_name";
    static final String DEVICE_ID_TAG = "client_device_id";
    // Claude Code's device id is 64 hex characters, one more than a host label allows; a 16-character
    // prefix still tells installs apart.
    static final int DEVICE_ID_LENGTH = 16;

    private LitellmAgentEndpointRewrite() {}

    /**
     * Rewrites contextSource, the host request header and the tag of requestData in place when it
     * is LiteLLM connector traffic from a known coding agent (AGENTS), or from any client that sends
     * x-akto-contextsource: ENDPOINT; anything else is left untouched.
     */
    public static void apply(Map<String, Object> requestData) {
        if (!LITELLM_CONNECTOR.equalsIgnoreCase(asString(requestData.get("akto_connector")))) {
            return;
        }
        BasicDBObject headers = parseObject(asString(requestData.get("requestHeaders")));
        String userAgent = header(headers, "user-agent");
        String agent = agentFor(userAgent);
        if (agent == null && Constants.AKTO_ENDPOINT_SOURCE_VALUE.equalsIgnoreCase(header(headers, CONTEXT_SOURCE_HEADER))) {
            agent = agentFromUserAgent(userAgent);
        }
        if (agent == null) {
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
            ? AgentHostUtils.identitySlug(identity) + "." + agent + "." + AgentHostUtils.identitySlug(tag.getString(MCP_SERVER_NAME_TAG))
            : AgentHostUtils.agentHost(identity, agent);

        putHeader(headers, "host", host);
        if (email != null && header(headers, INSTALLER_USER_EMAIL_HEADER) == null) {
            headers.put(INSTALLER_USER_EMAIL_HEADER, email);
        }
        tag.put(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
        tag.put(mcp ? Constants.AKTO_MCP_CLIENT_TAG : Constants.AKTO_AI_AGENT_TAG, agent);

        requestData.put("requestHeaders", headers.toJson());
        requestData.put("tag", tag.toJson());
        requestData.put("contextSource", Constants.AKTO_ENDPOINT_SOURCE_VALUE);
    }

    /** Agent segment for a known coding-agent User-Agent (prefix match, any version and casing), else null. */
    static String agentFor(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String ua = userAgent.toLowerCase();
        for (Map.Entry<String, String> e : AGENTS.entrySet()) {
            if (ua.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Agent name for a client not in AGENTS: the product in its User-Agent (up to the first "/" or
     * space), slugified, plus the -litellm suffix; e.g. "OpenAI/Python 1.40.0" -> "openai-litellm".
     */
    static String agentFromUserAgent(String userAgent) {
        String product = userAgent == null ? "" : userAgent.trim().split("[/\\s]", 2)[0];
        String slug = AgentHostUtils.slugify(product);
        return (slug.isEmpty() ? UNKNOWN_AGENT : slug) + LITELLM_AGENT_SUFFIX;
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
