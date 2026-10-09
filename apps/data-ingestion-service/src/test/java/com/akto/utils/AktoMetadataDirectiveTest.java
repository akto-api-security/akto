package com.akto.utils;

import com.akto.gateway.Gateway;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AktoMetadataDirectiveTest {

    // As LiteLLM sends it: its own context source, a vxlan id and the akto_metadata JSON ("{}" when unset)
    private static Map<String, Object> fromLitellm(String vxlanId, String contextSource, String metadata) {
        Map<String, Object> data = new HashMap<>();
        data.put("akto_vxlan_id", vxlanId);
        data.put("contextSource", contextSource);
        data.put("tag", "{\"gen-ai\": \"Gen AI\"}");
        data.put(AktoMetadataDirective.FIELD, metadata);
        VxlanPolicyDirective.apply(data);
        AktoMetadataDirective.apply(data);
        return data;
    }

    @Test
    public void policyNameFromMetadataPicksThePolicies() {
        Map<String, Object> data = fromLitellm("0", "AGENTIC", "{\"policy_name\": \"PII Strict, Secrets\"}");
        assertEquals("PII Strict, Secrets", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        assertEquals("AGENTIC", data.get("contextSource"));
        assertEquals("{\"gen-ai\": \"Gen AI\"}", data.get("tag"));
    }

    @Test
    public void metadataPolicyNameWinsOverAVxlanDirective() {
        Map<String, Object> data = fromLitellm("policy:ENDPOINT:Old Policy", "AGENTIC", "{\"policy_name\": \"New Policy\"}");
        assertEquals("New Policy", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        assertEquals("ENDPOINT", data.get("contextSource"));
    }

    @Test
    public void aVxlanDirectiveStillPicksTheContextSourceAndPolicies() {
        Map<String, Object> data = fromLitellm("policy:ENDPOINT:block employee pii", "AGENTIC", "{}");
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("block employee pii", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        assertEquals("0", data.get("akto_vxlan_id"));
    }

    @Test
    public void aVxlanDirectiveWithoutAContextSourceKeepsTheOneLitellmSent() {
        Map<String, Object> data = fromLitellm("policy::Secrets", "ENDPOINT", "{}");
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("Secrets", data.get(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void aPlainVxlanIdIsLeftAlone() {
        Map<String, Object> data = fromLitellm("42", "AGENTIC", "{}");
        assertEquals("42", data.get("akto_vxlan_id"));
        assertEquals("AGENTIC", data.get("contextSource"));
        assertFalse(data.containsKey(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void metadataWithoutAUsablePolicyNameChangesNothing() {
        String[] noPolicy = { null, "", "{}", "not json", "{\"policy_name\": \" \"}", "{\"context_source\": \"ENDPOINT\"}",
            "{\"policy_name\": [\"A\", \"B\"]}", "{\"policy_name\": 5}" };
        for (String metadata : noPolicy) {
            Map<String, Object> data = fromLitellm("0", "AGENTIC", metadata);
            assertEquals("AGENTIC", data.get("contextSource"));
            assertFalse(data.containsKey(Gateway.GUARDRAILS_POLICY_NAME));
            assertEquals("{\"gen-ai\": \"Gen AI\"}", data.get("tag"));
        }
    }
}
