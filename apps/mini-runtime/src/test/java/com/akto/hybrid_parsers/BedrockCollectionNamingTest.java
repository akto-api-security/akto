package com.akto.hybrid_parsers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.akto.dto.HttpResponseParams;
import com.akto.runtime.parser.SampleParser;
import com.google.gson.Gson;

/**
 * End to end over the two halves of the change: SampleParser rewrites bot-name to the calling
 * agent, and getHostnameForCollection - which already keys Bedrock traffic by bot-name - then
 * puts each agent in its own collection instead of pooling them under a shared IAM role.
 */
public class BedrockCollectionNamingTest {

    private static final Gson gson = new Gson();

    private HttpResponseParams parse(String tagsJson) throws Exception {
        String message = "{"
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
        return SampleParser.parseSampleMessage(message);
    }

    private static final String BASE =
        "\"source\":\"AWS_BEDROCK\",\"bot-name\":\"sm-role-acorns-agents\"";

    @Test
    public void testEachAgentGetsItsOwnCollection() throws Exception {
        String deeplink = HttpCallParser.getHostnameForCollection(
            parse("{" + BASE + ",\"request-metadata-agent_name\":\"deeplink_agent\"}"));
        String invest = HttpCallParser.getHostnameForCollection(
            parse("{" + BASE + ",\"request-metadata-agent_name\":\"invest_agent\"}"));

        assertEquals("deeplink_agent", deeplink);
        assertEquals("invest_agent", invest);
        assertNotEquals(deeplink, invest,
            "agents sharing one IAM role must no longer share one collection");
    }

    @Test
    public void testFallsBackToBotNameWithoutAgentName() throws Exception {
        assertEquals("sm-role-acorns-agents",
            HttpCallParser.getHostnameForCollection(parse("{" + BASE + "}")),
            "Bedrock traffic that names no agent keeps the collection it has today");
    }

    /**
     * ENDPOINT names its collections by its own rule (bot-name joined to the host); what
     * matters here is only that the agent_name rewrite never reached it.
     */
    @Test
    public void testNonBedrockSourceIsUnaffected() throws Exception {
        String name = HttpCallParser.getHostnameForCollection(parse(
            "{\"source\":\"ENDPOINT\",\"bot-name\":\"some-bot\","
            + "\"request-metadata-agent_name\":\"deeplink_agent\"}"));

        assertTrue(name.startsWith("some-bot"), "kept its own bot-name, got: " + name);
        assertFalse(name.contains("deeplink_agent"), "the rewrite is Bedrock-only");
    }

    /** No tags at all: the raw Host header still wins, as for any non-agentic traffic. */
    @Test
    public void testPlainTrafficKeepsItsHost() throws Exception {
        assertEquals("bedrock-runtime.us-east-1.amazonaws.com",
            HttpCallParser.getHostnameForCollection(parse("")));
    }
}
