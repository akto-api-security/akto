package com.akto.runtime.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.akto.dto.HttpResponseParams;
import com.akto.util.Constants;
import com.google.gson.Gson;

/**
 * A pipeline that builds records from logs has no request to hang headers on, so it delivers
 * the conversation as a tag and the turn as a response id. AgentQueryRecord only ever reads the
 * x-akto-installer-* headers, so without these bridges every such record arrives with a null
 * session and an empty traceId - ungroupable, uncountable and not expandable.
 */
public class SampleParserAgenticIdentityTest {

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

    private String headerOf(HttpResponseParams p, String name) {
        List<String> values = p.getRequestParams().getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String sessionHeaderOf(HttpResponseParams p) {
        return headerOf(p, Constants.AI_AGENT_HEADER_INSTALLER_SESSION_ID);
    }

    private String messageIdHeaderOf(HttpResponseParams p) {
        return headerOf(p, Constants.AI_AGENT_HEADER_INSTALLER_MESSAGE_ID);
    }

    private static final String REQUEST_ID_HEADER =
        "\\\"x-request-id\\\":\\\"a299d3ed-b7d6-4d84-b8d4-35cca20d6e04\\\"";

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

    // ---------------- turn identifier (traceId) ----------------

    /**
     * Without this, every Bedrock record lands with an empty traceId: per-session message counts
     * are a cardinality over it, and every per-trace lookup is keyed on it, so the turns of a
     * conversation can neither be counted nor expanded.
     */
    @Test
    public void testRequestIdBecomesTheTurnIdentifier() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\",\"bot-name\":\"a\"}", REQUEST_ID_HEADER));

        assertEquals("a299d3ed-b7d6-4d84-b8d4-35cca20d6e04", messageIdHeaderOf(p));
    }

    /** Two turns of one conversation share a session but must stay distinct traces. */
    @Test
    public void testTurnsInOneConversationGetDistinctIds() throws Exception {
        String tags = "{\"source\":\"AWS_BEDROCK\",\"session-id\":\"" + SESSION + "\"}";
        HttpResponseParams first  = SampleParser.parseSampleMessage(
            message(tags, "\\\"x-request-id\\\":\\\"req-one\\\""));
        HttpResponseParams second = SampleParser.parseSampleMessage(
            message(tags, "\\\"x-request-id\\\":\\\"req-two\\\""));

        assertEquals(sessionHeaderOf(first), sessionHeaderOf(second), "same conversation");
        assertNotEquals(messageIdHeaderOf(first), messageIdHeaderOf(second), "different turns");
    }

    @Test
    public void testNoRequestIdInventsNoTurnIdentifier() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\",\"session-id\":\"" + SESSION + "\"}", null));

        assertNull(messageIdHeaderOf(p));
        assertEquals(SESSION, sessionHeaderOf(p), "the session still bridges on its own");
    }

    @Test
    public void testExistingTurnIdentifierIsNeverOverwritten() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"AWS_BEDROCK\"}",
            "\\\"x-request-id\\\":\\\"from-bedrock\\\","
            + "\\\"x-akto-installer-akto_message_id\\\":\\\"from-header\\\""));

        assertEquals("from-header", messageIdHeaderOf(p));
    }

    @Test
    public void testOtherSourcesGetNoTurnIdentifier() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message(
            "{\"source\":\"ENDPOINT\",\"bot-name\":\"some-bot\"}", REQUEST_ID_HEADER));

        assertNull(messageIdHeaderOf(p), "only AWS_BEDROCK opts into the bridge");
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
