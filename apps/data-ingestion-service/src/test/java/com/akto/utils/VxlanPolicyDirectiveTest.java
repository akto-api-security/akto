package com.akto.utils;

import com.akto.gateway.Gateway;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class VxlanPolicyDirectiveTest {

    private static Map<String, Object> envelope(String vxlanId) {
        Map<String, Object> data = new HashMap<>();
        data.put("akto_connector", "litellm");
        data.put("akto_vxlan_id", vxlanId);
        data.put("contextSource", "AGENTIC");
        return data;
    }

    private static Map<String, Object> applied(String vxlanId) {
        Map<String, Object> data = envelope(vxlanId);
        VxlanPolicyDirective.apply(data);
        return data;
    }

    @Test
    public void directiveSetsContextSourceAndPolicyAndResetsVxlanId() {
        Map<String, Object> data = applied("policy:ENDPOINT:PII Strict");
        assertEquals("0", data.get("akto_vxlan_id"));
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("PII Strict", data.get(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void prefixAndContextSourceIgnoreCaseAndSpaces() {
        Map<String, Object> data = applied("  Policy: endpoint : Secrets,Prompt Injection ");
        assertEquals("ENDPOINT", data.get("contextSource"));
        assertEquals("Secrets,Prompt Injection", data.get(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void policyNamesMayContainColons() {
        assertEquals("PII: Strict", applied("policy:AGENTIC:PII: Strict").get(Gateway.GUARDRAILS_POLICY_NAME));
    }

    @Test
    public void emptyOrUnknownContextSourceKeepsTheRequestOne() {
        for (String vxlanId : new String[]{"policy::Secrets", "policy:ENDPIONT:Secrets"}) {
            Map<String, Object> data = applied(vxlanId);
            assertEquals("AGENTIC", data.get("contextSource"));
            assertEquals("Secrets", data.get(Gateway.GUARDRAILS_POLICY_NAME));
            assertEquals("0", data.get("akto_vxlan_id"));
        }
    }

    @Test
    public void contextSourceWithoutPolicyNamesSetsNoPolicy() {
        for (String vxlanId : new String[]{"policy:ENDPOINT", "policy:ENDPOINT:", "policy:ENDPOINT:  "}) {
            Map<String, Object> data = applied(vxlanId);
            assertEquals("ENDPOINT", data.get("contextSource"));
            assertFalse(data.containsKey(Gateway.GUARDRAILS_POLICY_NAME));
        }
    }

    @Test
    public void vxlanIdWithoutThePrefixIsUntouched() {
        for (String vxlanId : new String[]{"0", "1313121", "", "PII Strict", "policies:ENDPOINT:x", null}) {
            Map<String, Object> data = envelope(vxlanId);
            Map<String, Object> before = new HashMap<>(data);
            VxlanPolicyDirective.apply(data);
            assertEquals(before, data);
        }
    }

    @Test
    public void endpointDirectiveMovesLitellmTrafficToAtlas() {
        Map<String, Object> data = envelope("policy:ENDPOINT:PII Strict");
        data.put("requestHeaders", new BasicDBObject("user-agent", "opencode/1.0.0").append("host", "localhost:4000").toJson());
        data.put("tag", new BasicDBObject("gen-ai", "Gen AI").toJson());
        VxlanPolicyDirective.apply(data);
        LitellmAgentEndpointRewrite.apply(data);
        assertEquals("localhost-4000.ai-agent.opencode-litellm", BasicDBObject.parse(data.get("requestHeaders").toString()).getString("host"));
        assertEquals("ENDPOINT", BasicDBObject.parse(data.get("tag").toString()).getString("source"));
    }
}
