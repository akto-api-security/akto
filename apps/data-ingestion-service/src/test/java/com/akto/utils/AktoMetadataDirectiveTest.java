package com.akto.utils;

import com.akto.gateway.Gateway;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AktoMetadataDirectiveTest {

    private static Map<String, Object> applied(String litellmMetadata, String tag) {
        Map<String, Object> data = new HashMap<>();
        data.put("contextSource", "AGENTIC");
        data.put("tag", tag);
        data.put(AktoMetadataDirective.FIELD, litellmMetadata);
        AktoMetadataDirective.apply(data);
        return data;
    }

    @Test
    public void setsPolicyNameAndAddsOtherKeysAsStringTags() {
        Map<String, Object> data = applied(
            "{\"policy_name\": \"PII Strict, Secrets\", \"env\": \"prod\", \"tier\": 2}", "{\"gen-ai\": \"Gen AI\"}");
        assertEquals("PII Strict, Secrets", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        BasicDBObject tag = BasicDBObject.parse((String) data.get("tag"));
        assertEquals(new BasicDBObject("gen-ai", "Gen AI").append("env", "prod").append("tier", "2"), tag);
    }

    @Test
    public void theContextSourceStaysWhatLitellmSentAndIsNotReadFromMetadata() {
        Map<String, Object> data = applied("{\"context_source\": \"ENDPOINT\"}", "{}");
        assertEquals("AGENTIC", data.get("contextSource"));
        assertEquals("ENDPOINT", BasicDBObject.parse((String) data.get("tag")).getString("context_source"));
    }

    @Test
    public void winsOverAVxlanDirectiveButKeepsExistingTags() {
        Map<String, Object> data = new HashMap<>();
        data.put("akto_vxlan_id", "policy:AGENTIC:Old Policy");
        data.put("tag", "{\"mcp-server\": \"MCP Server\"}");
        data.put(AktoMetadataDirective.FIELD, "{\"policy_name\": \"New Policy\", \"mcp-server\": \"other\"}");
        VxlanPolicyDirective.apply(data);
        AktoMetadataDirective.apply(data);
        assertEquals("New Policy", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        assertEquals("MCP Server", BasicDBObject.parse((String) data.get("tag")).getString("mcp-server"));
    }

    // As LiteLLM sends a guardrail that only sets akto_vxlan_id: its own context source and empty akto_metadata
    private static Map<String, Object> litellmWithVxlanId(String vxlanId, String contextSource) {
        Map<String, Object> data = new HashMap<>();
        data.put("akto_vxlan_id", vxlanId);
        data.put("contextSource", contextSource);
        data.put("tag", "{\"gen-ai\": \"Gen AI\"}");
        data.put(AktoMetadataDirective.FIELD, "{}");
        VxlanPolicyDirective.apply(data);
        AktoMetadataDirective.apply(data);
        return data;
    }

    @Test
    public void aVxlanDirectiveStillPicksTheContextSourceAndPolicies() {
        Map<String, Object> data = litellmWithVxlanId("policy:ENDPOINT:block employee pii", "AGENTIC");
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("block employee pii", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        assertEquals("0", data.get("akto_vxlan_id"));
        assertEquals("{\"gen-ai\": \"Gen AI\"}", data.get("tag"));
    }

    @Test
    public void aVxlanDirectiveWithoutAContextSourceKeepsTheOneLitellmSent() {
        Map<String, Object> data = litellmWithVxlanId("policy::Secrets", "ENDPOINT");
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("Secrets", data.get(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void aPlainVxlanIdIsLeftAlone() {
        Map<String, Object> data = litellmWithVxlanId("42", "AGENTIC");
        assertEquals("42", data.get("akto_vxlan_id"));
        assertEquals("AGENTIC", data.get("contextSource"));
        assertFalse(data.containsKey(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void emptyOrInvalidMetadataChangeNothing() {
        for (String metadata : new String[] { null, "", "not json", "{\"policy_name\": \" \"}" }) {
            Map<String, Object> data = applied(metadata, "{\"gen-ai\": \"Gen AI\"}");
            assertEquals("AGENTIC", data.get("contextSource"));
            assertFalse(data.containsKey(Gateway.GUARDRAILS_POLICY_NAME));
            assertEquals("{\"gen-ai\": \"Gen AI\"}", data.get("tag"));
        }
    }
}
