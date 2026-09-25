package com.akto.utils;

import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LitellmAgentEndpointRewriteTest {

    private static final String CLAUDE_CLI_UA = "claude-cli/2.1.282 (external, sdk-cli)";
    // Claude Code's metadata.user_id device_id: 64 hex characters.
    private static final String DEVICE_ID = "e5682ef8c5847e7f62e4e2bc2124da809f6b8b98591f2ffb56f5c199bac68ebd";
    private static final String DEVICE_HOST = "e5682ef8c5847e7f.ai-agent.claudecli-litellm";

    /** Envelope as the LiteLLM hook sends it: headers and tag are JSON strings. */
    private static Map<String, Object> envelope(String connector, BasicDBObject headers, BasicDBObject tag) {
        Map<String, Object> data = new HashMap<>();
        data.put("akto_connector", connector);
        data.put("requestHeaders", headers.toJson());
        data.put("tag", tag.toJson());
        data.put("contextSource", "AGENTIC");
        return data;
    }

    private static BasicDBObject claudeCodeHeaders() {
        return new BasicDBObject("user-agent", CLAUDE_CLI_UA)
            .append("x-claude-code-session-id", "4d0e795e")
            .append("host", "localhost:4000")
            .append("content-type", "application/json");
    }

    /** Tag with no client identity, as sent by clients that carry no metadata.user_id. */
    private static BasicDBObject baseTag() {
        return new BasicDBObject("gen-ai", "Gen AI").append("litellm", "LiteLLM")
            .append("call_type", "anthropic_messages").append("model", "claude-opus-5-5");
    }

    /** Tag of the pre-call verdict: the hook reads Claude Code's metadata.user_id from the request body. */
    private static BasicDBObject verdictTag() {
        return baseTag().append("client_device_id", DEVICE_ID).append("client_session_id", "4d0e795e");
    }

    /** Tag of the post-call ingest of the same turn: also carries LiteLLM's own session ids. */
    private static BasicDBObject ingestTag() {
        return verdictTag().append("session_id", "4d0e795e").append("litellm_call_id", "1ddebc2e");
    }

    private static BasicDBObject headers(Map<String, Object> data) {
        return BasicDBObject.parse(data.get("requestHeaders").toString());
    }

    private static BasicDBObject tag(Map<String, Object> data) {
        return BasicDBObject.parse(data.get("tag").toString());
    }

    @Test
    public void claudeCodeViaLitellmBecomesAtlasClaudeCliTraffic() {
        Map<String, Object> data = envelope("litellm", claudeCodeHeaders(), ingestTag());
        LitellmAgentEndpointRewrite.apply(data);

        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals(DEVICE_HOST, headers(data).getString("host"));
        BasicDBObject tag = tag(data);
        assertEquals("ENDPOINT", tag.getString("source"));
        assertEquals("claudecli-litellm", tag.getString("ai-agent"));
        assertEquals("LiteLLM", tag.getString("litellm"));
        assertEquals(DEVICE_ID, tag.getString("client_device_id"));
        assertEquals(CLAUDE_CLI_UA, headers(data).getString("user-agent"));
        assertNull(headers(data).get("x-akto-installer-user_email"));
    }

    @Test
    public void verdictAndIngestOfOneTurnLandOnTheSameHost() {
        Map<String, Object> verdict = envelope("litellm", claudeCodeHeaders(), verdictTag());
        Map<String, Object> ingest = envelope("litellm", claudeCodeHeaders(), ingestTag());
        LitellmAgentEndpointRewrite.apply(verdict);
        LitellmAgentEndpointRewrite.apply(ingest);
        assertEquals(DEVICE_HOST, headers(verdict).getString("host"));
        assertEquals(DEVICE_HOST, headers(ingest).getString("host"));
    }

    @Test
    public void shortDeviceIdIsKeptWhole() {
        Map<String, Object> data = envelope("litellm", claudeCodeHeaders(), baseTag().append("client_device_id", "e568ebd"));
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("e568ebd.ai-agent.claudecli-litellm", headers(data).getString("host"));
    }

    @Test
    public void withoutEmailOrDeviceIdTheProxyHostIsUsed() {
        Map<String, Object> data = envelope("litellm", claudeCodeHeaders(), baseTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("localhost-4000.ai-agent.claudecli-litellm", headers(data).getString("host"));
    }

    @Test
    public void installerEmailHeaderNamesTheHost() {
        BasicDBObject h = claudeCodeHeaders().append("x-akto-installer-user_email", "Test.User@example.com");
        Map<String, Object> data = envelope("litellm", h, verdictTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("test-user.ai-agent.claudecli-litellm", headers(data).getString("host"));
        assertEquals("Test.User@example.com", headers(data).getString("x-akto-installer-user_email"));
    }

    @Test
    public void emailFromTagIsUsedAndForwardedAsInstallerHeader() {
        Map<String, Object> data = envelope("litellm", claudeCodeHeaders(), ingestTag().append("user_email", "test.user@example.com"));
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("test-user.ai-agent.claudecli-litellm", headers(data).getString("host"));
        assertEquals("test.user@example.com", headers(data).getString("x-akto-installer-user_email"));
    }

    @Test
    public void emailFromSpendLogsMetadataHeaderIsUsed() {
        BasicDBObject h = claudeCodeHeaders().append("x-litellm-spend-logs-metadata", "{\"user_email\": \"test.user@example.com\"}");
        Map<String, Object> data = envelope("litellm", h, verdictTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("test-user.ai-agent.claudecli-litellm", headers(data).getString("host"));
        assertEquals("test.user@example.com", headers(data).getString("x-akto-installer-user_email"));
    }

    @Test
    public void openWebUiUserEmailComesFirst() {
        BasicDBObject h = claudeCodeHeaders().append("X-OpenWebUI-User-Email", "webui.user@example.com")
            .append("x-akto-installer-user_email", "installer@example.com");
        Map<String, Object> data = envelope("litellm", h, ingestTag().append("user_email", "tag@example.com"));
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("webui-user.ai-agent.claudecli-litellm", headers(data).getString("host"));
        // An installer email the client already sent is left as it is.
        assertEquals("installer@example.com", headers(data).getString("x-akto-installer-user_email"));
    }

    @Test
    public void openWebUiUserEmailIsForwardedAsInstallerHeader() {
        BasicDBObject h = genericClientHeaders("Python/3.11 aiohttp/3.9.5").append("x-akto-contextsource", "ENDPOINT")
            .append("X-OpenWebUI-User-Email", "webui.user@example.com");
        Map<String, Object> data = envelope("litellm", h, builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("webui-user.ai-agent.python-litellm", headers(data).getString("host"));
        assertEquals("webui.user@example.com", headers(data).getString("x-akto-installer-user_email"));
    }

    @Test
    public void applyReturnsTheEmailItUsed() {
        BasicDBObject h = claudeCodeHeaders().append("X-OpenWebUI-User-Email", "webui.user@example.com");
        assertEquals("webui.user@example.com", LitellmAgentEndpointRewrite.apply(envelope("litellm", h, verdictTag())));
        assertNull(LitellmAgentEndpointRewrite.apply(envelope("litellm", claudeCodeHeaders(), verdictTag())));
        BasicDBObject other = claudeCodeHeaders().append("x-akto-installer-user_email", "test.user@example.com");
        other.put("user-agent", "OpenAI/Python 1.40.0");
        assertNull(LitellmAgentEndpointRewrite.apply(envelope("litellm", other, verdictTag())));
    }

    @Test
    public void installerEmailHeaderWinsOverTag() {
        BasicDBObject h = claudeCodeHeaders().append("x-akto-installer-user_email", "first@example.com");
        Map<String, Object> data = envelope("litellm", h, ingestTag().append("user_email", "second@example.com"));
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("first.ai-agent.claudecli-litellm", headers(data).getString("host"));
    }

    @Test
    public void toolCallTrafficIsTaggedAsClaudeCliAgent() {
        BasicDBObject toolTag = new BasicDBObject("gen-ai", "Gen AI").append("ai-agent", "litellm")
            .append("tool_name", "Bash").append("call_type", "tool_call").append("client_device_id", DEVICE_ID);
        Map<String, Object> data = envelope("litellm", claudeCodeHeaders(), toolTag);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals(DEVICE_HOST, headers(data).getString("host"));
        assertEquals("claudecli-litellm", tag(data).getString("ai-agent"));
        assertEquals("ENDPOINT", tag(data).getString("source"));
    }

    @Test
    public void mcpToolCallsUseTheClaudeCliMcpHostShape() {
        BasicDBObject mcpTag = new BasicDBObject("mcp-server", "MCP Server").append("mcp-client", "litellm")
            .append("mcp_server_name", "claude_ai_Slack").append("tool_name", "slack_send_message")
            .append("client_device_id", DEVICE_ID);
        Map<String, Object> data = envelope("litellm", claudeCodeHeaders(), mcpTag);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("e5682ef8c5847e7f.claudecli-litellm.claude-ai-slack", headers(data).getString("host"));
        BasicDBObject tag = tag(data);
        assertEquals("claudecli-litellm", tag.getString("mcp-client"));
        assertEquals("ENDPOINT", tag.getString("source"));
        assertFalse(tag.containsField("ai-agent"));
        assertEquals("ENDPOINT", data.get("contextSource"));
    }

    @Test
    public void hostHeaderIsReplacedWhateverItsCasing() {
        BasicDBObject h = new BasicDBObject("User-Agent", CLAUDE_CLI_UA).append("Host", "LiteLLM.corp:4000");
        Map<String, Object> data = envelope("LiteLLM", h, baseTag());
        LitellmAgentEndpointRewrite.apply(data);
        BasicDBObject out = headers(data);
        assertFalse(out.containsField("Host"));
        assertEquals("litellm-corp-4000.ai-agent.claudecli-litellm", out.getString("host"));
    }

    @Test
    public void userAgentPrefixMatchIgnoresVersionAndCase() {
        BasicDBObject h = claudeCodeHeaders();
        h.put("user-agent", "Claude-CLI/3.0.0 (external, cli)");
        Map<String, Object> data = envelope("litellm", h, verdictTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("ENDPOINT", data.get("contextSource"));
    }

    private static final String OPENCODE_UA = "opencode/1.18.32 ai-sdk/provider-utils/4.0.23 runtime/bun/1.3.14";

    /** OpenCode through LiteLLM's built-in Akto guardrail: its tag carries only gen-ai and the key's user id. */
    private static BasicDBObject builtInGuardrailTag() {
        return new BasicDBObject("gen-ai", "Gen AI").append("user_id", "default_user_id");
    }

    @Test
    public void openCodeViaLitellmBecomesAtlasOpenCodeTraffic() {
        BasicDBObject h = new BasicDBObject("user-agent", OPENCODE_UA).append("host", "localhost:4001");
        Map<String, Object> data = envelope("litellm", h, builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("localhost-4001.ai-agent.opencode-litellm", headers(data).getString("host"));
        assertEquals("opencode-litellm", tag(data).getString("ai-agent"));
        assertEquals("ENDPOINT", tag(data).getString("source"));
    }

    @Test
    public void openCodeEmailHeaderNamesTheHost() {
        BasicDBObject h = new BasicDBObject("user-agent", OPENCODE_UA).append("host", "localhost:4001")
            .append("x-akto-installer-user_email", "test.user@example.com");
        Map<String, Object> data = envelope("litellm", h, builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("test-user.ai-agent.opencode-litellm", headers(data).getString("host"));
    }

    @Test
    public void agentIsPickedByUserAgentPrefix() {
        assertEquals("claudecli-litellm", LitellmAgentEndpointRewrite.agentFor(CLAUDE_CLI_UA));
        assertEquals("opencode-litellm", LitellmAgentEndpointRewrite.agentFor("OpenCode/2.0.0"));
        assertNull(LitellmAgentEndpointRewrite.agentFor("OpenAI/Python 1.40.0"));
        assertNull(LitellmAgentEndpointRewrite.agentFor("my-opencode/1.0"));
        assertNull(LitellmAgentEndpointRewrite.agentFor(null));
    }

    private static BasicDBObject genericClientHeaders(String userAgent) {
        BasicDBObject h = new BasicDBObject("host", "localhost:4001");
        if (userAgent != null) {
            h.append("user-agent", userAgent);
        }
        return h;
    }

    @Test
    public void contextSourceHeaderMovesAnyClientToAtlas() {
        BasicDBObject h = genericClientHeaders("OpenAI/Python 1.40.0").append("x-akto-contextsource", "ENDPOINT");
        Map<String, Object> data = envelope("litellm", h, builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("localhost-4001.ai-agent.openai-litellm", headers(data).getString("host"));
        assertEquals("openai-litellm", tag(data).getString("ai-agent"));
        assertEquals("ENDPOINT", tag(data).getString("source"));
    }

    @Test
    public void contextSourceHeaderMatchIgnoresCaseAndUsesTheEmail() {
        BasicDBObject h = genericClientHeaders("cursor/1.2.0 (darwin)").append("X-Akto-ContextSource", "endpoint")
            .append("x-akto-installer-user_email", "test.user@example.com");
        Map<String, Object> data = envelope("litellm", h, builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("test-user.ai-agent.cursor-litellm", headers(data).getString("host"));
    }

    @Test
    public void contextSourceHeaderWithoutUserAgentUsesUnknownAgent() {
        Map<String, Object> data = envelope("litellm", genericClientHeaders(null).append("x-akto-contextsource", "ENDPOINT"), builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("localhost-4001.ai-agent.unknown-litellm", headers(data).getString("host"));
    }

    @Test
    public void knownAgentsKeepTheirNameWhenTheHeaderIsSent() {
        BasicDBObject h = new BasicDBObject("user-agent", OPENCODE_UA).append("host", "localhost:4001").append("x-akto-contextsource", "ENDPOINT");
        Map<String, Object> data = envelope("litellm", h, builtInGuardrailTag());
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("opencode-litellm", tag(data).getString("ai-agent"));
    }

    @Test
    public void otherContextSourceValuesLeaveGenericClientsUntouched() {
        Map<String, Object> data = envelope("litellm", genericClientHeaders("OpenAI/Python 1.40.0").append("x-akto-contextsource", "AGENTIC"), builtInGuardrailTag());
        Map<String, Object> before = new HashMap<>(data);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals(before, data);
    }

    @Test
    public void contextSourceHeaderOnOtherConnectorsIsIgnored() {
        Map<String, Object> data = envelope("cursor", genericClientHeaders("cursor/1.2.0").append("x-akto-contextsource", "ENDPOINT"), builtInGuardrailTag());
        Map<String, Object> before = new HashMap<>(data);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals(before, data);
    }

    @Test
    public void agentNameIsDerivedFromTheUserAgentProduct() {
        assertEquals("openai-litellm", LitellmAgentEndpointRewrite.agentFromUserAgent("OpenAI/Python 1.40.0"));
        assertEquals("my-agent-litellm", LitellmAgentEndpointRewrite.agentFromUserAgent("My_Agent 2.0"));
        assertEquals("unknown-litellm", LitellmAgentEndpointRewrite.agentFromUserAgent(null));
        assertEquals("unknown-litellm", LitellmAgentEndpointRewrite.agentFromUserAgent("  "));
    }

    @Test
    public void otherLitellmClientsAreUntouched() {
        BasicDBObject h = claudeCodeHeaders();
        h.put("user-agent", "OpenAI/Python 1.40.0");
        Map<String, Object> data = envelope("litellm", h, verdictTag());
        Map<String, Object> before = new HashMap<>(data);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals(before, data);
    }

    @Test
    public void otherConnectorsAreUntouched() {
        Map<String, Object> data = envelope("claude_code_cli", claudeCodeHeaders(), verdictTag());
        Map<String, Object> before = new HashMap<>(data);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals(before, data);
    }

    @Test
    public void missingHeadersAreUntouched() {
        Map<String, Object> data = new HashMap<>();
        data.put("akto_connector", "litellm");
        LitellmAgentEndpointRewrite.apply(data);
        assertNull(data.get("contextSource"));
        assertTrue(data.size() == 1);
    }

    @Test
    public void agentHostFallsBackToUnknownIdentity() {
        assertEquals("unknown.ai-agent.claudecli", AgentHostUtils.agentHost(null, "claudecli"));
        assertEquals("unknown.ai-agent.claudecli", AgentHostUtils.agentHost("@@@", "claudecli"));
        assertEquals("a-b.ai-agent.claude-cli", AgentHostUtils.agentHost(AgentHostUtils.emailLocalPart("A.B@x.io"), "claude-cli"));
    }
}
