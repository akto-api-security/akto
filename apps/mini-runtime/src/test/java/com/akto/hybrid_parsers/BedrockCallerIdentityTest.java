package com.akto.hybrid_parsers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

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
 * Who a gen-AI record names as its user.
 *
 * The shared fallback is the organisation's admin email, which is not a user at all - it is
 * whoever administers the account, shown as though they made the call. Bedrock can do better:
 * the log processor already resolves a caller from the invocation's identity ARN
 * (identityActor.js) and sends it as the record's ip.
 *
 * Needs a database only for the fallback path, which reads the organisation.
 */
public class BedrockCallerIdentityTest {

    private static final int ACCOUNT_ID = 1000000;
    private static final String ROLE = "sm-role-acorns-agents";
    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;

    @BeforeAll
    static void startMongo() throws Exception {
        ImmutableMongodConfig cfg = ImmutableMongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 27022, false))
                .build();
        mongodExe = MongodStarter.getDefaultInstance().prepare(cfg);
        mongod = mongodExe.start();
        DaoInit.init(new ConnectionString("mongodb://localhost:27022"));
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
    }

    @AfterAll
    static void stopMongo() {
        if (mongod != null) { mongod.stop(); mongodExe.stop(); }
    }

    /** One gen-AI record whose ip carries whatever the processor resolved from the ARN. */
    private HttpResponseParams record(String source, String actorIp) throws Exception {
        String tag = "{\\\"source\\\":\\\"" + source + "\\\",\\\"gen-ai\\\":\\\"Gen AI\\\","
            + "\\\"bot-name\\\":\\\"" + ROLE + "\\\"}";
        String message = "{"
            + "\"path\":\"/model/anthropic.claude-sonnet-5/converse-stream\",\"method\":\"POST\","
            + "\"requestHeaders\":\"{\\\"host\\\":\\\"bedrock-runtime.us-east-1.amazonaws.com\\\"}\","
            + "\"responseHeaders\":\"{}\","
            + "\"requestPayload\":\"{\\\"messages\\\":[]}\",\"responsePayload\":\"{}\","
            + "\"ip\":\"" + actorIp + "\",\"time\":\"" + Context.now() + "\",\"statusCode\":\"200\","
            + "\"type\":\"HTTP/1.1\",\"status\":\"OK\",\"akto_account_id\":\"" + ACCOUNT_ID + "\","
            + "\"akto_vxlan_id\":\"0\",\"is_pending\":\"false\",\"source\":\"MIRRORING\","
            + "\"tag\":\"" + tag + "\"}";
        return SampleParser.parseSampleMessage(message);
    }

    private String userOf(String source, String actorIp) throws Exception {
        HttpResponseParams p = record(source, actorIp);
        Map<String, String> tags = HttpCallParser.parseTagsMap(p.getTags());
        AgentQueryRecord r = AgentQueryRecord.fromHttpResponseParams(p, tags, new HashMap<>());
        return r == null ? null : r.getUserName();
    }

    /** An assumed role with a machine session resolves to the role's own name. */
    @Test
    public void testBedrockUserIsTheResolvedCaller() throws Exception {
        assertEquals(ROLE, userOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, ROLE),
            "the role the caller assumed answers 'who called this'");
    }

    /** An SSO ARN resolves to the person's email, delivered in the same field. */
    @Test
    public void testBedrockUserCanBeAnEmail() throws Exception {
        assertEquals("mabba@example.com",
            userOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, "mabba@example.com"));
    }

    /** Nothing resolvable in the ARN: the processor sends a placeholder, which is not a user. */
    @Test
    public void testUnresolvedActorDoesNotBecomeTheUser() throws Exception {
        assertNotEquals(Constants.AI_AGENT_UNRESOLVED_ACTOR_IP,
            userOf(Constants.AI_AGENT_SOURCE_AWS_BEDROCK, Constants.AI_AGENT_UNRESOLVED_ACTOR_IP));
    }

    /**
     * Every other gen-AI source puts a real address in that field, so using it would show
     * 10.0.1.15 as the user - worse than the admin email it would have replaced.
     */
    @Test
    public void testOtherGenAiSourcesNeverShowAnIpAsTheUser() throws Exception {
        assertNotEquals("10.0.1.15", userOf("ENDPOINT", "10.0.1.15"));
    }
}
