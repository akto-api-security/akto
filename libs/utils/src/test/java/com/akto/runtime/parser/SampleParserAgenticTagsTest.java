package com.akto.runtime.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.HttpResponseParams;
import com.akto.util.Constants;
import com.google.gson.Gson;

/**
 * Bedrock records name their caller by IAM role, which every agent in an application shares.
 * When the caller tells us which agent actually made the call, that becomes the identity.
 */
public class SampleParserAgenticTagsTest {

    private static final Gson gson = new Gson();

    /** One ingest message, shaped the way the Bedrock log processor sends them. */
    private String message(String tagsJson) {
        return "{"
            + "\"path\":\"/model/anthropic.claude-sonnet-5/converse-stream\","
            + "\"method\":\"POST\","
            + "\"requestHeaders\":\"{\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\"}\","
            + "\"responseHeaders\":\"{}\","
            + "\"requestPayload\":\"{\\\"messages\\\":[]}\","
            + "\"responsePayload\":\"{}\","
            + "\"ip\":\"0.0.0.0\",\"time\":\"1764000000\",\"statusCode\":\"200\","
            + "\"type\":\"HTTP/1.1\",\"status\":\"OK\",\"akto_account_id\":\"1000000\","
            + "\"akto_vxlan_id\":\"0\",\"is_pending\":\"false\",\"source\":\"MIRRORING\","
            + "\"tag\":" + gson.toJson(tagsJson)
            + "}";
    }

    private String bedrockTags(String extra) {
        return "{\"source\":\"AWS_BEDROCK\",\"bot-name\":\"sm-role-acorns-agents\""
            + (extra == null ? "" : "," + extra) + "}";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> tagsOf(HttpResponseParams p) {
        return gson.fromJson(p.getTags(), Map.class);
    }

    // ---------------- bot-name ----------------

    @Test
    public void testAgentNameBecomesBotName() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(
            message(bedrockTags("\"request-metadata-agent_name\":\"deeplink_agent\"")));

        assertEquals("deeplink_agent", tagsOf(p).get(Constants.AI_AGENT_TAG_BOT_NAME),
            "the agent that made the call should own the identity, not the shared IAM role");
    }

    @Test
    public void testBotNameUntouchedWithoutAgentName() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(bedrockTags(null)));

        assertEquals("sm-role-acorns-agents", tagsOf(p).get(Constants.AI_AGENT_TAG_BOT_NAME),
            "a caller that names no agent keeps today's behaviour exactly");
    }

    @Test
    public void testOtherSourcesAreNeverRewritten() throws Exception {
        String tags = "{\"source\":\"ENDPOINT\",\"bot-name\":\"some-bot\","
            + "\"request-metadata-agent_name\":\"deeplink_agent\"}";
        HttpResponseParams p = SampleParser.parseSampleMessage(message(tags));

        assertEquals("some-bot", tagsOf(p).get(Constants.AI_AGENT_TAG_BOT_NAME),
            "the rewrite is scoped to Bedrock; no other source changes shape");
    }

    @Test
    public void testAwsQuickIsNotRewritten() throws Exception {
        String tags = "{\"source\":\"AWS_QUICK\",\"bot-name\":\"quick-bot\","
            + "\"request-metadata-agent_name\":\"deeplink_agent\"}";
        HttpResponseParams p = SampleParser.parseSampleMessage(message(tags));

        assertEquals("quick-bot", tagsOf(p).get(Constants.AI_AGENT_TAG_BOT_NAME),
            "AWS Quick keeps its existing collection naming");
    }

    @Test
    public void testBlankAgentNameIsIgnored() {
        String tags = bedrockTags("\"request-metadata-agent_name\":\"   \"");
        assertEquals(tags, SampleParser.normalizeAgenticTags(tags),
            "a blank name is not an identity");
    }

    @Test
    public void testMalformedTagsPassThrough() {
        assertEquals("{not json", SampleParser.normalizeAgenticTags("{not json"));
        assertNull(SampleParser.normalizeAgenticTags(null));
        assertEquals("", SampleParser.normalizeAgenticTags(""));
    }

    // ---------------- alongside the identity bridge ----------------
    // The session and turn bridging itself is covered by SampleParserAgenticIdentityTest.
    // These pin that the rewrite and the bridge, which both read the same tag string, do not
    // step on each other on one record.

    private String messageWithRequestId(String tagsJson, String requestId) {
        return message(tagsJson).replace(
            "\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\"",
            "\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\","
          + "\\\"x-request-id\\\":\\\"" + requestId + "\\\"");
    }

    private String header(HttpResponseParams p, String name) {
        List<String> values = p.getRequestParams().getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Test
    public void testRewriteKeepsTheSessionAndTurn() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(messageWithRequestId(bedrockTags(
            "\"request-metadata-agent_name\":\"deeplink_agent\","
          + "\"session-id\":\"8ef2a7f2-4e53-42ec-b368-6c051c763a5d\""), "req-1"));

        assertEquals("deeplink_agent", tagsOf(p).get(Constants.AI_AGENT_TAG_BOT_NAME));
        assertEquals("8ef2a7f2-4e53-42ec-b368-6c051c763a5d",
            header(p, Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID),
            "rewriting bot-name must not drop the session tag the bridge reads");
        assertEquals("req-1", header(p, Constants.AI_AGENT_HEADER_INSTALLER_MESSAGE_ID),
            "nor the turn identifier");
    }

    /** Tags become headers too; both must carry the rewritten name so readers cannot disagree. */
    @Test
    public void testInjectedBotNameHeaderMatchesTheRewrittenTag() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(
            message(bedrockTags("\"request-metadata-agent_name\":\"deeplink_agent\"")));

        assertEquals("deeplink_agent", header(p, "x-akto-k8s-" + Constants.AI_AGENT_TAG_BOT_NAME));
    }

    /** Two agents in one conversation keep their own identity and share the session. */
    @Test
    public void testSiblingAgentsShareTheConversation() throws Exception {
        String session = "\"session-id\":\"conv-1\"";
        HttpResponseParams a = SampleParser.parseSampleMessage(messageWithRequestId(bedrockTags(
            "\"request-metadata-agent_name\":\"invest_agent\"," + session), "req-a"));
        HttpResponseParams b = SampleParser.parseSampleMessage(messageWithRequestId(bedrockTags(
            "\"request-metadata-agent_name\":\"deeplink_agent\"," + session), "req-b"));

        assertNotEquals(tagsOf(a).get(Constants.AI_AGENT_TAG_BOT_NAME),
                        tagsOf(b).get(Constants.AI_AGENT_TAG_BOT_NAME));
        assertEquals("conv-1", header(a, Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID));
        assertEquals("conv-1", header(b, Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID));
        assertNotEquals(header(a, Constants.AI_AGENT_HEADER_INSTALLER_MESSAGE_ID),
                        header(b, Constants.AI_AGENT_HEADER_INSTALLER_MESSAGE_ID));
    }
}
