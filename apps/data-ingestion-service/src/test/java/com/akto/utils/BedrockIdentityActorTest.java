package com.akto.utils;

import com.akto.dto.IngestDataBatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Actor cases must match lambda-function/test/identity-actor-cases.json in
 * akto_aws_bedrock_discovery (same file copied to test resources).
 */
public class BedrockIdentityActorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void actorFromIdentityArn_matchesSharedCases() throws Exception {
        InputStream in = getClass().getResourceAsStream("/identity-actor-cases.json");
        assertNotNull("identity-actor-cases.json missing from test resources", in);
        JsonNode cases = MAPPER.readTree(in);
        assertTrue(cases.isArray());
        assertTrue(cases.size() > 0);
        for (JsonNode c : cases) {
            String name = c.get("name").asText();
            String arn = c.get("arn").asText();
            String expected = c.get("actor").asText();
            assertEquals(name, expected, BedrockIdentityActor.actorFromIdentityArn(arn));
        }
    }

    @Test
    public void actorFromIdentityArn_rejectsNull() {
        assertEquals("", BedrockIdentityActor.actorFromIdentityArn(null));
    }

    @Test
    public void actorFromIdentityArn_trimsWhitespace() {
        assertEquals("alice", BedrockIdentityActor.actorFromIdentityArn("  arn:aws:iam::123456789012:user/alice  "));
    }

    @Test
    public void parseArn_usesAwsSdkEnvelope() {
        BedrockIdentityActor.ParsedArn parsed = BedrockIdentityActor.parseArn(
                "arn:aws:sts::123456789012:assumed-role/aria-usertask-role/edf60249dbcf44b19f0c10eedfe1790f");
        assertNotNull(parsed);
        assertEquals("sts", parsed.service);
        assertEquals("assumed-role/aria-usertask-role/edf60249dbcf44b19f0c10eedfe1790f", parsed.resource);
    }

    @Test
    public void apply_rewritesPlaceholderFromHeader() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("0.0.0.0");
        payload.setRequestHeaders("{\"bedrock-identity-arn\":\"arn:aws:sts::123456789012:assumed-role/AWSReservedSSO_Admin_abc/mabba@example.com\"}");
        BedrockIdentityActor.apply(payload);
        assertEquals("mabba@example.com", payload.getIp());
    }

    @Test
    public void apply_headerLookupIsCaseInsensitive() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("0.0.0.0");
        payload.setRequestHeaders("{\"Bedrock-Identity-Arn\":\"arn:aws:iam::123456789012:user/alice\"}");
        BedrockIdentityActor.apply(payload);
        assertEquals("alice", payload.getIp());
    }

    @Test
    public void apply_fallsBackToTagWhenHeaderMissing() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("0.0.0.0");
        payload.setRequestHeaders("{\"host\":\"bedrock-runtime.us-east-1.amazonaws.com\"}");
        payload.setTag("{\"bedrock-identity-arn\":\"arn:aws:sts::123456789012:assumed-role/aria-usertask-role/edf60249dbcf44b19f0c10eedfe1790f\"}");
        BedrockIdentityActor.apply(payload);
        assertEquals("ECS-edf60249dbcf44b19f0c10eedfe1790f-aria-usertask-role", payload.getIp());
    }

    @Test
    public void apply_doesNotOverwriteRealIp() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("203.0.113.9");
        payload.setRequestHeaders("{\"bedrock-identity-arn\":\"arn:aws:iam::123456789012:user/alice\"}");
        BedrockIdentityActor.apply(payload);
        assertEquals("203.0.113.9", payload.getIp());
    }

    @Test
    public void apply_leavesPlaceholderWhenArnIsAResource() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("0.0.0.0");
        payload.setRequestHeaders("{\"bedrock-identity-arn\":\"arn:aws:bedrock:us-east-1:123456789012:agent/ABCDEF\"}");
        BedrockIdentityActor.apply(payload);
        assertEquals("0.0.0.0", payload.getIp());
    }

    @Test
    public void apply_readsFirstArrayValue() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("0.0.0.0");
        payload.setRequestHeaders("{\"bedrock-identity-arn\":[\"arn:aws:iam::123456789012:user/alice\"]}");
        BedrockIdentityActor.apply(payload);
        assertEquals("alice", payload.getIp());
    }

    // ---------- machine sessions: kind, id and role ----------

    private static final String ROLE_ARN = "arn:aws:sts::123456789012:assumed-role/";

    @Test
    public void actor_ec2InstanceSession() {
        assertEquals("EC2-i-0abc1234def56789a-deploy-role",
                BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "deploy-role/i-0abc1234def56789a"));
    }

    @Test
    public void actor_ecsTaskSession() {
        assertEquals("ECS-0123456789abcdef0123456789abcdef-aria-usertask-role",
                BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "aria-usertask-role/0123456789abcdef0123456789abcdef"));
    }

    @Test
    public void actor_bedrockAgentsSession() {
        assertEquals("BedrockAgents-AGENT12345-11111111-2222-3333-4444-555555555555-AmazonBedrockExecutionRoleForAgents_ABC123",
                BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN
                        + "AmazonBedrockExecutionRoleForAgents_ABC123/BedrockAgents-AGENT12345-11111111-2222-3333-4444-555555555555"));
    }

    @Test
    public void actor_uuidSession() {
        assertEquals("SESSION-11111111-2222-3333-4444-555555555555-app-role",
                BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "app-role/11111111-2222-3333-4444-555555555555"));
    }

    @Test
    public void actor_readableSessionAndNoSessionAreUnchanged() {
        assertEquals("order-processor", BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "lambda-role/order-processor"));
        assertEquals("deadbeef", BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "app-role/deadbeef"));
        assertEquals("my-role", BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "my-role"));
    }

    @Test
    public void actor_neverContainsAColon() {
        String[] sessions = {"i-0abc1234def56789a", "0123456789abcdef0123456789abcdef",
                "BedrockAgents-AGENT12345-0f1e2d3c", "11111111-2222-3333-4444-555555555555"};
        for (String session : sessions) {
            assertFalse(session, BedrockIdentityActor.actorFromIdentityArn(ROLE_ARN + "r/" + session).contains(":"));
        }
    }

    // ---------- apply(): every Bedrock collector version ----------

    private static IngestDataBatch bedrockPayload(String ip, String identityArn) {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp(ip);
        payload.setRequestHeaders("{\"host\":\"bedrock-runtime.us-east-1.amazonaws.com\",\"bedrock-identity-arn\":\""
                + identityArn + "\"}");
        return payload;
    }

    @Test
    public void apply_replacesTheRoleNameSentByNewerCollectors() {
        IngestDataBatch payload = bedrockPayload("aria-usertask-role",
                ROLE_ARN + "aria-usertask-role/0123456789abcdef0123456789abcdef");
        BedrockIdentityActor.apply(payload);
        assertEquals("ECS-0123456789abcdef0123456789abcdef-aria-usertask-role", payload.getIp());
    }

    @Test
    public void apply_keepsTheSamePersonSentByNewerCollectors() {
        IngestDataBatch payload = bedrockPayload("jane.doe", ROLE_ARN + "my-app-role/jane.doe");
        BedrockIdentityActor.apply(payload);
        assertEquals("jane.doe", payload.getIp());
    }

    @Test
    public void apply_rewritesEmptyAndNullIp() {
        IngestDataBatch empty = bedrockPayload("", ROLE_ARN + "deploy-role/i-0abc1234");
        BedrockIdentityActor.apply(empty);
        assertEquals("EC2-i-0abc1234-deploy-role", empty.getIp());

        IngestDataBatch missing = bedrockPayload(null, ROLE_ARN + "deploy-role/i-0abc1234");
        BedrockIdentityActor.apply(missing);
        assertEquals("EC2-i-0abc1234-deploy-role", missing.getIp());
    }

    @Test
    public void apply_neverOverwritesARealIp() {
        String[] realIps = {"203.0.113.9", "203.0.113.9:443", "2001:db8::1", "[2001:db8::1]:443", "::1"};
        for (String ip : realIps) {
            IngestDataBatch payload = bedrockPayload(ip, ROLE_ARN + "deploy-role/i-0abc1234");
            BedrockIdentityActor.apply(payload);
            assertEquals(ip, ip, payload.getIp());
        }
    }

    @Test
    public void apply_keepsTheCollectorValueWhenTheArnIsAResource() {
        IngestDataBatch payload = bedrockPayload("discovery-actor", "arn:aws:bedrock:us-east-1:123456789012:agent/ABCDEF");
        BedrockIdentityActor.apply(payload);
        assertEquals("discovery-actor", payload.getIp());
    }

    @Test
    public void apply_leavesPayloadsWithoutTheIdentityArnUntouched() {
        IngestDataBatch other = new IngestDataBatch();
        other.setIp("some-actor");
        other.setRequestHeaders("{\"host\":\"api.example.com\",\"x-user\":\"arn:aws:iam::123456789012:user/alice\"}");
        other.setTag("{\"source\":\"IBM_WATSONX\"}");
        BedrockIdentityActor.apply(other);
        assertEquals("some-actor", other.getIp());

        IngestDataBatch placeholder = new IngestDataBatch();
        placeholder.setIp("0.0.0.0");
        placeholder.setRequestHeaders("not json");
        BedrockIdentityActor.apply(placeholder);
        assertEquals("0.0.0.0", placeholder.getIp());
    }

    @Test
    public void apply_ignoresTheKeyNameWhenItIsOnlyAValue() {
        IngestDataBatch payload = new IngestDataBatch();
        payload.setIp("some-actor");
        payload.setRequestHeaders("{\"x-note\":\"bedrock-identity-arn\"}");
        BedrockIdentityActor.apply(payload);
        assertEquals("some-actor", payload.getIp());
    }

    @Test
    public void apply_ignoresNullPayload() {
        BedrockIdentityActor.apply(null);
    }

    @Test
    public void isRealIp_classifiesAddressesAndPlaceholders() {
        assertTrue(BedrockIdentityActor.isRealIp("10.0.1.15"));
        assertTrue(BedrockIdentityActor.isRealIp(" 10.0.1.15 "));
        assertTrue(BedrockIdentityActor.isRealIp("fe80::1%eth0"));
        assertFalse(BedrockIdentityActor.isRealIp("0.0.0.0"));
        assertFalse(BedrockIdentityActor.isRealIp("0.0.0.0:0"));
        assertFalse(BedrockIdentityActor.isRealIp("::"));
        assertFalse(BedrockIdentityActor.isRealIp("null"));
        assertFalse(BedrockIdentityActor.isRealIp(null));
        assertFalse(BedrockIdentityActor.isRealIp("aria-usertask-role"));
        assertFalse(BedrockIdentityActor.isRealIp("ECS-0123456789abcdef0123456789abcdef-aria-usertask-role"));
        assertFalse(BedrockIdentityActor.isRealIp("EC2-i-0abc1234-deploy-role"));
    }
}
