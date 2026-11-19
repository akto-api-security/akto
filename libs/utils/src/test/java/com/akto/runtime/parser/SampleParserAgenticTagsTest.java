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
 * When the caller tells us which agent actually made the call, that becomes the identity, and
 * the conversation id it sends as a tag is carried into the header Traces reads.
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

    // ---------------- session header ----------------

    @Test
    public void testSessionTagIsCarriedIntoTheHeaderTracesReads() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(
            message(bedrockTags("\"session-id\":\"8ef2a7f2-4e53-42ec-b368-6c051c763a5d\"")));

        List<String> header = p.getRequestParams().getHeaders()
            .get(Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID);
        assertNotNull(header, "AgentQueryRecord reads only this header, so it has to be set");
        assertEquals("8ef2a7f2-4e53-42ec-b368-6c051c763a5d", header.get(0));
    }

    @Test
    public void testNoSessionTagLeavesNoHeader() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(bedrockTags(null)));

        assertFalse(p.getRequestParams().getHeaders()
            .containsKey(Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID),
            "nothing to carry means no header invented");
    }

    /** A source that sets the header itself must keep its own value. */
    @Test
    public void testExistingSessionHeaderWins() throws Exception {
        String msg = message(bedrockTags("\"session-id\":\"from-tag\""))
            .replace("\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\"",
                     "\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\","
                   + "\\\"x-akto-installer-akto_session_id\\\":\\\"from-header\\\"");
        HttpResponseParams p = SampleParser.parseSampleMessage(msg);

        assertEquals("from-header", p.getRequestParams().getHeaders()
            .get(Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID).get(0));
    }

    /** The session tag is normalised upstream, so a renamed caller field still lands here. */
    @Test
    public void testSessionComesFromTheNormalisedTagNotARawSpelling() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(bedrockTags(
            "\"session-id\":\"abc-123\",\"request-metadata-conversationId\":\"abc-123\"")));

        assertEquals("abc-123", p.getRequestParams().getHeaders()
            .get(Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID).get(0));
    }
}
