package com.akto.utils;

import com.akto.dto.IngestDataBatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
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
        assertEquals("aria-usertask-role", payload.getIp());
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
}
