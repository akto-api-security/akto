package com.akto.hybrid_parsers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.runtime.parser.SampleParser;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.ImmutableMongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;

/**
 * Drives a real ingest message through the real mini-runtime path against a real (embedded)
 * Mongo, and asserts what actually lands in the database: the collection an Acorns-shaped
 * Bedrock record is filed under, and the service graph drawn for it.
 *
 * The unit tests cover each half in isolation; this is the one that would catch the halves
 * failing to meet - a tag rewritten but never read, or a graph built against the wrong id.
 */
public class BedrockPipelineIntegrationTest {

    private static final int ACCOUNT_ID = 1000000;
    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;

    @BeforeAll
    static void startMongo() throws Exception {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        ImmutableMongodConfig cfg = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 27021, false))
                .build();
        mongodExe = starter.prepare(cfg);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27021"));
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
    }

    @AfterAll
    static void stopMongo() {
        if (mongod != null) { mongod.stop(); mongodExe.stop(); }
    }

    private static final String ROLE  = "sm-role-acorns-agents";
    private static final String ORCH  = "chatbot_orchestrator_agent";
    private static final String MODEL = "anthropic.claude-sonnet-5";
    private static final String SESSION = "8ef2a7f2-4e53-42ec-b368-6c051c763a5d";

    /** An ingest record shaped exactly as the Bedrock log processor sends them. */
    private String message(String agentName) {
        String tag = "{\\\"source\\\":\\\"AWS_BEDROCK\\\",\\\"gen-ai\\\":\\\"Gen AI\\\","
            + "\\\"bot-name\\\":\\\"" + ROLE + "\\\",\\\"agent-id\\\":\\\"" + ROLE + "\\\","
            + "\\\"agentType\\\":\\\"BEDROCK_AGENT\\\",\\\"model\\\":\\\"" + MODEL + "\\\","
            + "\\\"session-id\\\":\\\"" + SESSION + "\\\","
            + "\\\"request-metadata-agent_name\\\":\\\"" + agentName + "\\\","
            + "\\\"request-metadata-orchestrator_name\\\":\\\"" + ORCH + "\\\"}";
        String awsMetadata = "{\\\"model\\\":\\\"" + MODEL + "\\\","
            + "\\\"bedrock-execution-role\\\":\\\"" + ROLE + "\\\",\\\"traceData\\\":{}}";
        return "{"
            + "\"path\":\"/model/" + MODEL + "/converse-stream\",\"method\":\"POST\","
            + "\"requestHeaders\":\"{\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\","
            + "\\\"x-request-id\\\":\\\"req-" + agentName + "\\\"}\","
            + "\"responseHeaders\":\"{}\","
            + "\"requestPayload\":\"{\\\"messages\\\":[]}\","
            + "\"responsePayload\":\"{\\\"awsMetadata\\\":" + awsMetadata + "}\","
            + "\"ip\":\"0.0.0.0\",\"time\":\"" + Context.now() + "\",\"statusCode\":\"200\","
            + "\"type\":\"HTTP/1.1\",\"status\":\"OK\",\"akto_account_id\":\"" + ACCOUNT_ID + "\","
            + "\"akto_vxlan_id\":\"0\",\"is_pending\":\"false\",\"source\":\"MIRRORING\","
            + "\"tag\":\"" + tag + "\"}";
    }

    /** Runs one record the whole way through and returns the collection it was filed under. */
    private ApiCollection ingest(String agentName) throws Exception {
        HttpCallParser parser = new HttpCallParser("test-user", 10, 10, 120, false);
        HttpResponseParams record = SampleParser.parseSampleMessage(message(agentName));

        int collectionId = parser.createApiCollectionId(record);
        record.getRequestParams().setApiCollectionId(collectionId);
        parser.filterHttpResponseParams(Collections.singletonList(record), new AccountSettings());

        return ApiCollectionsDao.instance.findOne(Filters.eq("_id", collectionId));
    }

    @Test
    public void testRecordIsFiledUnderTheCallingAgent() throws Exception {
        ApiCollection collection = ingest("deeplink_agent");

        assertNotNull(collection, "the record should have created a collection");
        assertEquals("deeplink_agent", collection.getHostName(),
            "filed under the agent that made the call, not the shared IAM role");
    }

    @Test
    public void testServiceGraphCarriesTheOrchestratorHop() throws Exception {
        ApiCollection collection = ingest("invest_agent");
        Map<String, ServiceGraphEdgeInfo> edges = collection.getServiceGraphEdges();

        assertNotNull(edges, "a Bedrock record should have produced graph edges");
        assertEquals("User", edges.get(ORCH).getSourceService(),
            "the orchestrator hangs off User");
        assertEquals(ORCH, edges.get("invest_agent").getSourceService(),
            "the calling agent hangs off its orchestrator");
        assertEquals("invest_agent", edges.get(MODEL).getSourceService(),
            "the model is reached by the agent that called it");
    }

    /** Two agents sharing one IAM role must no longer share one collection. */
    @Test
    public void testSiblingAgentsDoNotShareACollection() throws Exception {
        ApiCollection a = ingest("agent_alpha");
        ApiCollection b = ingest("agent_beta");

        assertNotEquals(a.getId(), b.getId());
        assertEquals("agent_alpha", a.getHostName());
        assertEquals("agent_beta", b.getHostName());
    }

    /**
     * The agent split and the Traces identifiers come from the same tag string; a record filed
     * under its agent must still group into its conversation and turn in Traces.
     */
    @Test
    public void testAgentRecordKeepsItsConversationAndTurn() throws Exception {
        HttpResponseParams p = SampleParser.parseSampleMessage(message("deeplink_agent"));
        AgentQueryRecord r = AgentQueryRecord.fromHttpResponseParams(
            p, HttpCallParser.parseTagsMap(p.getTags()), new HashMap<>());

        assertNotNull(r);
        assertEquals(SESSION, r.getSessionIdentifier(), "the conversation survives the rewrite");
        assertEquals("req-deeplink_agent", r.getTraceId(), "and so does the turn");
    }

    /** Two agents in one conversation: separate collections, one session in Traces. */
    @Test
    public void testSiblingAgentsStayInOneConversation() throws Exception {
        HttpResponseParams a = SampleParser.parseSampleMessage(message("agent_gamma"));
        HttpResponseParams b = SampleParser.parseSampleMessage(message("agent_delta"));
        AgentQueryRecord ra = AgentQueryRecord.fromHttpResponseParams(
            a, HttpCallParser.parseTagsMap(a.getTags()), new HashMap<>());
        AgentQueryRecord rb = AgentQueryRecord.fromHttpResponseParams(
            b, HttpCallParser.parseTagsMap(b.getTags()), new HashMap<>());

        assertEquals(ra.getSessionIdentifier(), rb.getSessionIdentifier());
        assertNotEquals(ra.getTraceId(), rb.getTraceId(), "each call is its own turn");
        assertNotEquals(ingest("agent_gamma").getId(), ingest("agent_delta").getId());
    }

    /** Bedrock bot collections are dedicated, so they never get a "-agentic" sibling. */
    @Test
    public void testAgentCollectionIsNotForked() throws Exception {
        ApiCollection collection = ingest("fork_check_agent");
        assertFalse(collection.getHostName().endsWith("-agentic"));
        assertNull(ApiCollectionsDao.instance.findOne(Filters.eq("hostName", "fork_check_agent-agentic")));
    }
}
