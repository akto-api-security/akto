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
    public void setsPolicyNameAndContextSourceAndAddsOtherKeysAsStringTags() {
        Map<String, Object> data = applied(
            "{\"policy_name\": \"PII Strict, Secrets\", \"context_source\": \"endpoint\", \"env\": \"prod\", \"tier\": 2}",
            "{\"gen-ai\": \"Gen AI\"}");
        assertEquals("PII Strict, Secrets", data.get(Gateway.GUARDRAILS_POLICY_NAME));
        assertEquals("ENDPOINT", data.get("contextSource"));
        BasicDBObject tag = BasicDBObject.parse((String) data.get("tag"));
        assertEquals(new BasicDBObject("gen-ai", "Gen AI").append("env", "prod").append("tier", "2"), tag);
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

    @Test
    public void unknownContextSourceAndEmptyOrInvalidMetadataChangeNothing() {
        for (String metadata : new String[] { null, "", "not json", "{\"context_source\": \"ATLAS\", \"policy_name\": \" \"}" }) {
            Map<String, Object> data = applied(metadata, "{\"gen-ai\": \"Gen AI\"}");
            assertEquals("AGENTIC", data.get("contextSource"));
            assertFalse(data.containsKey(Gateway.GUARDRAILS_POLICY_NAME));
            assertEquals("{\"gen-ai\": \"Gen AI\"}", data.get("tag"));
        }
    }
}
