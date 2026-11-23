package com.akto.hybrid_parsers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.runtime.parser.SampleParser;
import com.akto.util.Constants;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.mongodb.ConnectionString;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;

/**
 * Which identifiers a record ends up carrying, asserted on the record itself rather than on
 * the headers the parser injects.
 *
 * SampleParser's own tests prove the injected headers are present. They cannot prove those
 * headers are read back into the fields the UI groups on, and the two message-id headers are
 * one prefix apart:
 *
 *   x-akto-message-id                -> spanId
 *   x-akto-installer-akto_message_id -> traceId    (what the Bedrock bridge writes)
 *
 * Session grouping counts turns as cardinality(traceId), so a bridge landing in spanId would
 * leave every session reporting zero turns while looking correct at the header level.
 */
public class BedrockRecordIdentifiersTest {

    private static final int ACCOUNT_ID = 1000000;
    private static final String REQUEST_ID = "a299d3ed-b7d6-4d84-b8d4-35cca20d6e04";
    private static final String SESSION_ID = "d8f1f6f1-bcdc-4358-8a89-24cb96b108a4";
    /** Fixed so the synthesised id is reproducible across calls within a test. */
    private static final int FIXED_EVENT_TIME = 1795165470;
    private static final String EMPTY_MESSAGES = "{\"messages\":[]}";

    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;

    @BeforeAll
    static void startMongo() throws Exception {
        ImmutableMongodConfig cfg = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 27023, false))
                .build();
        mongodExe = MongodStarter.getDefaultInstance().prepare(cfg);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27023"));
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
    }

    @AfterAll
    static void stopMongo() {
        if (mongod != null) { mongod.stop(); mongodExe.stop(); }
    }

    /** Embeds a JSON document inside the JSON ingest envelope. */
    private static String esc(String json) {
        return json.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private AgentQueryRecord recordOf(String source, String sessionId, String extraHeader) throws Exception {
        return recordOf(source, sessionId, extraHeader, FIXED_EVENT_TIME, EMPTY_MESSAGES);
    }

    /**
     * @param source       the tag the pipeline gates on
     * @param sessionId    caller-supplied conversation id, or null for a record without one
     * @param extraHeader  an already-present request header as plain JSON ("name":"value"), or null
     * @param eventTime    what the source reported as the event's time
     * @param requestPayload the request body, as plain JSON
     */
    private AgentQueryRecord recordOf(String source, String sessionId, String extraHeader,
                                      int eventTime, String requestPayload) throws Exception {
        String tags = "{\"source\":\"" + source + "\",\"gen-ai\":\"Gen AI\",\"bot-name\":\"acorns-agent\""
            + (sessionId == null ? "" : ",\"session-id\":\"" + sessionId + "\"")
            + "}";
        String headers = "{\"host\":\"bedrock-runtime.us-east-1.amazonaws.com\""
            + ",\"x-request-id\":\"" + REQUEST_ID + "\""
            + (extraHeader == null ? "" : "," + extraHeader)
            + "}";
        String message = "{"
            + "\"path\":\"/model/anthropic.claude-sonnet-5/converse-stream\",\"method\":\"POST\","
            + "\"requestHeaders\":\"" + esc(headers) + "\","
            + "\"responseHeaders\":\"{}\","
            + "\"requestPayload\":\"" + esc(requestPayload) + "\",\"responsePayload\":\"{}\","
            + "\"ip\":\"sm-role-acorns-agents\",\"time\":\"" + eventTime + "\",\"statusCode\":\"200\","
            + "\"type\":\"HTTP/1.1\",\"status\":\"OK\",\"akto_account_id\":\"" + ACCOUNT_ID + "\","
            + "\"akto_vxlan_id\":\"0\",\"is_pending\":\"false\",\"source\":\"MIRRORING\","
            + "\"tag\":\"" + esc(tags) + "\"}";
        HttpResponseParams p = SampleParser.parseSampleMessage(message);
        Map<String, String> tags2 = HttpCallParser.parseTagsMap(p.getTags());
        return AgentQueryRecord.fromHttpResponseParams(p, tags2, new HashMap<>());
    }

    /** An Atlas record: source ENDPOINT, installer headers, and no span header of its own. */
    private AgentQueryRecord atlasRecord() throws Exception {
        String tags = "{\"source\":\"ENDPOINT\",\"gen-ai\":\"Gen AI\"}";
        String headers = "{\"host\":\"api.anthropic.com\""
            + ",\"x-akto-installer-akto_session_id\":\"atlas-sess-1\""
            + ",\"x-akto-installer-akto_message_id\":\"atlas-msg-1\"}";
        String message = "{"
            + "\"path\":\"/v1/messages\",\"method\":\"POST\","
            + "\"requestHeaders\":\"" + esc(headers) + "\","
            + "\"responseHeaders\":\"{}\","
            + "\"requestPayload\":\"" + esc(EMPTY_MESSAGES) + "\",\"responsePayload\":\"{}\","
            + "\"ip\":\"10.0.1.15\",\"time\":\"" + FIXED_EVENT_TIME + "\",\"statusCode\":\"200\","
            + "\"type\":\"HTTP/1.1\",\"status\":\"OK\",\"akto_account_id\":\"" + ACCOUNT_ID + "\","
            + "\"akto_vxlan_id\":\"0\",\"is_pending\":\"false\",\"source\":\"MIRRORING\","
            + "\"tag\":\"" + esc(tags) + "\"}";
        HttpResponseParams p = SampleParser.parseSampleMessage(message);
        Map<String, String> t = HttpCallParser.parseTagsMap(p.getTags());
        return AgentQueryRecord.fromHttpResponseParams(p, t, new HashMap<>());
    }

    // ── the bridged ids ───────────────────────────────────────────────────────

    /** The turn: Bedrock's request id reaches traceId, which is what session counts aggregate. */
    @Test
    public void testBedrockRequestIdBecomesTheTraceId() throws Exception {
        AgentQueryRecord r = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, SESSION_ID, null);
        assertNotNull(r);
        assertEquals(REQUEST_ID, r.getTraceId(), "the invocation's request id identifies the turn");
    }

    /**
     * spanId reads a different header, so the bridge must not have landed there - if it had,
     * traceId would be empty and every session would count zero turns.
     */
    @Test
    public void testTheBridgeDoesNotLandInSpanIdInstead() throws Exception {
        AgentQueryRecord r = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, SESSION_ID, null);
        assertNotEquals(REQUEST_ID, r.getSpanId(),
            "x-akto-message-id is a separate header from x-akto-installer-akto_message_id");
        assertTrue(r.getSpanId() != null && !r.getSpanId().isEmpty(),
            "with no span header of its own the record synthesises one");
    }

    /** The conversation: the caller's session tag reaches the field sessions group on. */
    @Test
    public void testSessionTagBecomesTheSessionIdentifier() throws Exception {
        assertEquals(SESSION_ID,
            recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, SESSION_ID, null).getSessionIdentifier());
    }

    /**
     * A caller that sends no session is the common case, and it must still produce a row the
     * UI can identify - spanId is always populated, so such a record is listable on its own.
     */
    @Test
    public void testRecordWithoutSessionStillHasAnIdentifier() throws Exception {
        AgentQueryRecord r = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null);
        assertNotNull(r);
        assertTrue(r.getSessionIdentifier() == null || r.getSessionIdentifier().isEmpty(),
            "no session was sent, so none is invented");
        assertTrue(r.getSpanId() != null && !r.getSpanId().isEmpty(),
            "every record can still be named in a list");
    }

    /** A source that sets its own message id keeps it; the bridge never overwrites. */
    @Test
    public void testExistingMessageIdHeaderWins() throws Exception {
        AgentQueryRecord r = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, SESSION_ID,
            "\"x-akto-installer-akto_message_id\":\"set-by-the-source\"");
        assertEquals("set-by-the-source", r.getTraceId());
    }

    /** The gate holds at the record level, not just at the header level. */
    @Test
    public void testNonBedrockSourceGetsNoBridgedTraceId() throws Exception {
        AgentQueryRecord r = recordOf("ENDPOINT", SESSION_ID, null);
        assertNotNull(r);
        assertNotEquals(REQUEST_ID, r.getTraceId(),
            "only Bedrock's request id is treated as a turn identifier");
    }

    // ── the synthesised id, for records whose source sends none ───────────────

    /**
     * Re-ingesting the same source line must not create a second, differently-named copy of
     * one invocation. The record is written with a null docId, so Elasticsearch assigns one
     * and nothing else about the write is idempotent - this id is what makes the row stable.
     */
    @Test
    public void sameEventYieldsTheSameSynthesisedId() throws Exception {
        String first  = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null).getSpanId();
        String second = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null).getSpanId();
        assertEquals(first, second, "the same event replayed is the same record");
        assertFalse(first.startsWith("span_"), "Argus ids are shown unprefixed");
        UUID.fromString(first); // a plain, parseable UUID
    }

    /** Two genuinely different invocations must not collapse onto one id. */
    @Test
    public void differentPayloadsYieldDifferentIds() throws Exception {
        String a = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null,
                FIXED_EVENT_TIME, "{\"messages\":[{\"q\":\"one\"}]}").getSpanId();
        String b = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null,
                FIXED_EVENT_TIME, "{\"messages\":[{\"q\":\"two\"}]}").getSpanId();
        assertNotEquals(a, b);
    }

    /** The same prompt asked again later is a new invocation, not the same one. */
    @Test
    public void samePayloadAtADifferentTimeYieldsADifferentId() throws Exception {
        String earlier = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null,
                FIXED_EVENT_TIME, EMPTY_MESSAGES).getSpanId();
        String later = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null, null,
                FIXED_EVENT_TIME + 60, EMPTY_MESSAGES).getSpanId();
        assertNotEquals(earlier, later);
    }

    /**
     * Atlas must keep a drawn id, not a derived one.
     *
     * One Atlas turn is several spans of the same trace, and LLMTraceDetail keys its rendered
     * rows on spanId. Two spans agreeing on time and both payloads would derive the same id and
     * render as one row, so the synthesis deliberately stops at the Atlas boundary.
     */
    @Test
    public void atlasKeepsADrawnSpanIdRatherThanADerivedOne() throws Exception {
        AgentQueryRecord first  = atlasRecord();
        AgentQueryRecord second = atlasRecord();

        assertTrue(first.getIsAtlasTraffic(), "guard only means something on Atlas traffic");
        assertEquals("atlas-msg-1", first.getTraceId(), "Atlas still gets its installer trace id");
        assertNotEquals(first.getSpanId(), second.getSpanId(),
            "two Atlas spans must never collide on one id");
        assertTrue(first.getSpanId().startsWith("span_"), "Atlas span ids keep their existing shape");
    }

    /** A source that sends its own span id still wins over anything synthesised. */
    @Test
    public void sourceSuppliedSpanIdStillWins() throws Exception {
        AgentQueryRecord r = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, null,
            "\"x-akto-message-id\":\"sent-by-the-source\"");
        assertEquals("sent-by-the-source", r.getSpanId());
    }

    // ── model, for the Traces UI ──────────────────────────────────────────────

    /** Bedrock's model id reaches responsePayload, where the UI and top-models read it. */
    @Test
    public void bedrockModelLandsInTheResponsePayload() throws Exception {
        AgentQueryRecord r = recordOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, SESSION_ID,
            "\"x-bedrock-model-id\":\"anthropic.claude-sonnet-5\"");
        assertEquals("anthropic.claude-sonnet-5",
            new org.json.JSONObject(r.getResponsePayload()).optString("model"));
    }

    /** Atlas responses are stored exactly as received - the model bridge is Bedrock-only. */
    @Test
    public void atlasResponsePayloadIsUntouched() throws Exception {
        assertEquals("{}", atlasRecord().getResponsePayload());
    }
}
