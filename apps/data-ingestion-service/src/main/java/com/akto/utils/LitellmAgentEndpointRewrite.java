package com.akto.utils;

import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Treats traffic that reaches Akto through the LiteLLM connector (Akto's custom hook or LiteLLM's
 * built-in Akto guardrail) as Atlas (endpoint) traffic when the client sends
 * x-akto-contextsource: ENDPOINT or the LiteLLM config asks for it with an akto_vxlan_id directive
 * (VxlanPolicyDirective, e.g. policy:ENDPOINT:PII Strict); otherwise it stays Argus traffic, whoever the client is.
 * The agent name comes from the client's User-Agent: known coding agents by AGENTS, any other
 * client by agentFromUserAgent. Atlas keys on three things, and the LiteLLM connector sets none of them:
 * <ul>
 *   <li>contextSource ENDPOINT: which guardrail policies the guardrails service loads;</li>
 *   <li>host {identity}.ai-agent.{agent} (MCP tool calls: {identity}.{agent}.{server}):
 *       agent/device-scoped policy matching and the collection name;</li>
 *   <li>tag source=ENDPOINT, plus ai-agent / mcp-client = {agent}: Atlas collection placement
 *       and agent grouping (the envelope contextSource is not carried past ingestion).</li>
 * </ul>
 * The identity (first host segment) is, in order: the user's email local part when the traffic
 * carries an email (X-OpenWebUI-User-Email first, then x-akto-installer-user_email, the user_email
 * tag, x-litellm-spend-logs-metadata, then the LiteLLM virtual key's owner when its user_id tag is an
 * email); else the client's device id (the client_device_id tag, e.g. from the Anthropic
 * metadata.user_id Claude Code sends), shortened; else the host the connector sent (the LiteLLM
 * agent name or proxy host). Every call of a conversation carries the same inputs, so it lands on
 * the same host. It also gets a session id (sessionId) when it carries none, since the Atlas traces
 * page only lists traffic that has one.
 */
public final class LitellmAgentEndpointRewrite {

    static final String LITELLM_CONNECTOR = "litellm";
    // User-Agent prefix -> agent name for known coding agents: the agent's native Akto connector name
    // (AKTO_CONNECTOR_VALUE) plus a -litellm suffix, so traffic through LiteLLM shows as its own agent.
    static final String LITELLM_AGENT_SUFFIX = "-litellm";
    static final Map<String, String> AGENTS = new LinkedHashMap<>();
    static {
        AGENTS.put("claude-cli/", "claudecli" + LITELLM_AGENT_SUFFIX);
        AGENTS.put("opencode/", "opencode" + LITELLM_AGENT_SUFFIX);
    }
    // A LiteLLM client opts into Atlas by sending this header with ENDPOINT (or via VxlanPolicyDirective).
    static final String CONTEXT_SOURCE_HEADER = "x-akto-contextsource";
    static final String UNKNOWN_AGENT = "unknown";
    // Open WebUI's logged-in user, sent when Open WebUI runs with ENABLE_FORWARD_USER_INFO_HEADERS=true.
    static final String OPENWEBUI_USER_EMAIL_HEADER = "x-openwebui-user-email";
    static final String INSTALLER_USER_EMAIL_HEADER = "x-akto-installer-user_email";
    static final String SPEND_LOGS_METADATA_HEADER = "x-litellm-spend-logs-metadata";
    // Session id mini-runtime groups a trace under; Atlas traces without one are not listed.
    static final String AKTO_SESSION_ID_HEADER = "x-akto-installer-akto_session_id";
    // Per-conversation session ids clients already send: OpenCode, Claude Code.
    static final String[] CLIENT_SESSION_ID_HEADERS = {"x-session-id", "x-claude-code-session-id"};
    static final String GENERATED_SESSION_PREFIX = "litellm-";
    static final String USER_EMAIL_KEY = "user_email";
    // Owner of the LiteLLM virtual key (user_api_key_user_id), which LiteLLM's Akto guardrail sends in the tag.
    static final String KEY_USER_ID_TAG = "user_id";
    static final String MCP_SERVER_NAME_TAG = "mcp_server_name";
    static final String DEVICE_ID_TAG = "client_device_id";
    // Claude Code's device id is 64 hex characters, one more than a host label allows; a 16-character
    // prefix still tells installs apart.
    static final int DEVICE_ID_LENGTH = 16;

    private LitellmAgentEndpointRewrite() {}

    /**
     * Rewrites contextSource, the host request header and the tag of requestData in place when it
     * is LiteLLM connector traffic that sends x-akto-contextsource: ENDPOINT or whose contextSource is already
     * ENDPOINT (set by VxlanPolicyDirective); anything else is left untouched.
     *
     * @return the user email the rewritten traffic carries, or null when it carries none or nothing was rewritten
     */
    public static String apply(Map<String, Object> requestData) {
        if (!LITELLM_CONNECTOR.equalsIgnoreCase(asString(requestData.get("akto_connector")))) {
            return null;
        }
        BasicDBObject headers = parseObject(asString(requestData.get("requestHeaders")));
        if (!Constants.AKTO_ENDPOINT_SOURCE_VALUE.equalsIgnoreCase(header(headers, CONTEXT_SOURCE_HEADER))
            && !Constants.AKTO_ENDPOINT_SOURCE_VALUE.equalsIgnoreCase(asString(requestData.get("contextSource")))) {
            return null;
        }
        String userAgent = header(headers, "user-agent");
        String known = agentFor(userAgent);
        String agent = known != null ? known : agentFromUserAgent(userAgent);
        BasicDBObject tag = parseObject(asString(requestData.get("tag")));

        String email = firstNonEmpty(
            header(headers, OPENWEBUI_USER_EMAIL_HEADER),
            header(headers, INSTALLER_USER_EMAIL_HEADER),
            tag.getString(USER_EMAIL_KEY),
            parseObject(header(headers, SPEND_LOGS_METADATA_HEADER)).getString(USER_EMAIL_KEY),
            emailOrNull(tag.getString(KEY_USER_ID_TAG)));
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
        if (header(headers, AKTO_SESSION_ID_HEADER) == null) {
            headers.put(AKTO_SESSION_ID_HEADER, sessionId(headers, host));
        }
        tag.put(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
        tag.put(mcp ? Constants.AKTO_MCP_CLIENT_TAG : Constants.AKTO_AI_AGENT_TAG, agent);

        requestData.put("requestHeaders", headers.toJson());
        requestData.put("tag", tag.toJson());
        requestData.put("contextSource", Constants.AKTO_ENDPOINT_SOURCE_VALUE);
        return email;
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

    /**
     * The client's own session id when it sends one, so each conversation is its own session;
     * otherwise one session per user/agent host per UTC day, so the traffic still has a session.
     */
    static String sessionId(BasicDBObject headers, String host) {
        for (String name : CLIENT_SESSION_ID_HEADERS) {
            String id = header(headers, name);
            if (id != null) {
                return id;
            }
        }
        return GENERATED_SESSION_PREFIX + host + "-" + LocalDate.now(ZoneOffset.UTC);
    }

    /** The value when it is an email address, else null (a key's user_id need not be one). */
    static String emailOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        return at > 0 && at == trimmed.lastIndexOf('@') && at < trimmed.length() - 1 ? trimmed : null;
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
