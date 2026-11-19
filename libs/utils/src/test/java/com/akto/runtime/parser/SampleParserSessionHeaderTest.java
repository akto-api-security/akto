package com.akto.runtime.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.akto.dto.HttpResponseParams;
import com.akto.util.Constants;
import com.google.gson.Gson;

/**
 * A pipeline that builds records from logs has no request to hang a header on, so it delivers
 * the conversation id as a tag. AgentQueryRecord only ever reads the header, so without this
 * bridge every such record arrives with a null session and Traces cannot group it.
 */
public class SampleParserSessionHeaderTest {

    private static final Gson gson = new Gson();
    private static final String SESSION = "8ef2a7f2-4e53-42ec-b368-6c051c763a5d";

    /** One ingest message, shaped the way the Bedrock log processor sends them. */
    private String message(String tagsJson, String extraHeader) {
        String headers = "{\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\""
            + (extraHeader == null ? "" : "," + extraHeader) + "}";
        return "{"
            + "\"path\":\"/model/anthropic.claude-sonnet-5/converse-stream\",\"method\":\"POST\","
            + "\"requestHeaders\":\"" + headers + "\",\"responseHeaders\":\"{}\","
            + "\"requestPayload\":\"{\\\"messages\\\":[]}\",\"responsePayload\":\"{}\","
            + "\"ip\":\"0.0.0.0\",\"time\":\"1764000000\",\"statusCode\":\"200\","
            + "\"type\":\"HTTP/1.1\",\"status\":\"OK\",\"akto_account_id\":\"1000000\","
            + "\"akto_vxlan_id\":\"0\",\"is_pending\":\"false\",\"source\":\"MIRRORING\","
            + "\"tag\":" + gson.toJson(tagsJson)
            + "}";
    }

    private String sessionHeaderOf(HttpResponseParams p) {
        List<String> values = p.getRequestParams().getHeaders()
            .get(Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Test
    public void testSessionTagBecomesTheHeaderTracesReads() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\",\"bot-name\":\"sm-role-acorns-agents\","
            + "\"session-id\":\"" + SESSION + "\"}", null));

        assertEquals(SESSION, sessionHeaderOf(p),
            "AgentQueryRecord reads only this header, so the tag has to reach it");
    }

    /** Every request in one conversation carries the same id, which is what groups them. */
    @Test
    public void testTwoRequestsInOneConversationAgree() throws Exception {
        String tags = "{\"source\":\"AWS_BEDROCK\",\"bot-name\":\"a\",\"session-id\":\"" + SESSION + "\"}";
        HttpResponseParams first  = SampleParser.parseSampleMessage(message(tags, null));
        HttpResponseParams second = SampleParser.parseSampleMessage(message(tags, null));

        assertEquals(sessionHeaderOf(first), sessionHeaderOf(second));
    }

    /**
     * The bridge is scoped to Bedrock. No other source writes a session-id tag today, so this
     * guards the intent rather than any current behaviour: one that starts writing one has to
     * opt in here first.
     */
    @Test
    public void testOtherSourcesAreNotBridged() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"ENDPOINT\",\"bot-name\":\"some-bot\","
            + "\"session-id\":\"" + SESSION + "\"}", null));

        assertNull(sessionHeaderOf(p), "only AWS_BEDROCK opts into the bridge");
    }

    @Test
    public void testNoSessionTagInventsNoHeader() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\",\"bot-name\":\"sm-role-acorns-agents\"}", null));

        assertNull(sessionHeaderOf(p));
    }

    @Test
    public void testBlankSessionIsIgnored() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\",\"session-id\":\"   \"}", null));

        assertNull(sessionHeaderOf(p));
    }

    /** A source that sets the header itself keeps its own value. */
    @Test
    public void testExistingHeaderIsNeverOverwritten() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\",\"session-id\":\"from-tag\"}",
            "\\\"x-akto-installer-akto_session_id\\\":\\\"from-header\\\""));

        assertEquals("from-header", sessionHeaderOf(p));
    }

    @Test
    public void testRecordsWithoutTagsAreUnaffected() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message("", null));

        assertNull(sessionHeaderOf(p));
        assertNotNull(p.getRequestParams().getHeaders().get("host"));
    }

    /**
     * Pre-existing behaviour, pinned so the session bridge is not blamed for it: a tag string
     * that will not parse already fails the whole record inside injectTagsInHeaders, which has
     * no try/catch and runs first. The bridge's own parse is defensive, but it never gets the
     * chance to run. Widening that is a separate change, not one to make on a hotfix.
     */
    @Test
    public void testMalformedTagsAlreadyFailTheRecordBeforeTheBridge() {
        assertThrows(Exception.class,
            () -> SampleParser.parseSampleMessage(message("{not json", null)));
    }
}
