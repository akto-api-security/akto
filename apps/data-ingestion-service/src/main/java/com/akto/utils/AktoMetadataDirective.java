package com.akto.utils;

import com.akto.gateway.Gateway;
import com.mongodb.BasicDBObject;

import java.util.Map;

/**
 * Applies the akto_metadata JSON object LiteLLM's built-in Akto guardrail sends per guardrail entry:
 * <pre>
 *   {"policy_name": "PII Strict, Secrets", "env": "prod"}
 * </pre>
 * policy_name goes to the guardrails service as Gateway.GUARDRAILS_POLICY_NAME and every other key is added
 * to the tag as a string without overriding an existing tag. The context source is not read from here, since
 * LiteLLM sends it as contextSource. Applied after VxlanPolicyDirective, so it wins over an akto_vxlan_id
 * policy directive.
 */
public final class AktoMetadataDirective {

    public static final String FIELD = "akto_metadata";
    static final String POLICY_NAME = "policy_name";

    private AktoMetadataDirective() {}

    public static void apply(Map<String, Object> requestData) {
        BasicDBObject metadata = parseObject(requestData.get(FIELD));
        String policyName = asString(metadata.remove(POLICY_NAME));
        if (!policyName.isEmpty()) {
            requestData.put(Gateway.GUARDRAILS_POLICY_NAME, policyName);
        }
        if (metadata.isEmpty()) {
            return;
        }
        BasicDBObject tag = parseObject(requestData.get("tag"));
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getValue() != null && !tag.containsField(entry.getKey())) {
                tag.put(entry.getKey(), entry.getValue().toString());
            }
        }
        requestData.put("tag", tag.toJson());
    }

    private static BasicDBObject parseObject(Object raw) {
        String value = raw != null ? raw.toString().trim() : "";
        if (value.isEmpty()) {
            return new BasicDBObject();
        }
        try {
            return BasicDBObject.parse(value);
        } catch (Exception e) {
            return new BasicDBObject();
        }
    }

    private static String asString(Object value) {
        return value != null ? value.toString().trim() : "";
    }
}
