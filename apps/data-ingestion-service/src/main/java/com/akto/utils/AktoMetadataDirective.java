package com.akto.utils;

import com.akto.gateway.Gateway;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

import java.util.Map;

/**
 * Applies the akto_metadata JSON object LiteLLM's built-in Akto guardrail sends per guardrail entry:
 * <pre>
 *   {"policy_name": "PII Strict, Secrets", "context_source": "ENDPOINT", "env": "prod"}
 * </pre>
 * policy_name goes to the guardrails service as Gateway.GUARDRAILS_POLICY_NAME, context_source (AGENTIC or
 * ENDPOINT; anything else keeps the request's own) replaces contextSource, and every other key is added to
 * the tag as a string without overriding an existing tag. Applied after VxlanPolicyDirective, so it wins
 * over an akto_vxlan_id policy directive.
 */
public final class AktoMetadataDirective {

    public static final String FIELD = "akto_metadata";
    static final String POLICY_NAME = "policy_name";
    static final String CONTEXT_SOURCE = "context_source";

    private AktoMetadataDirective() {}

    public static void apply(Map<String, Object> requestData) {
        BasicDBObject metadata = parseObject(requestData.get(FIELD));
        String policyName = asString(metadata.remove(POLICY_NAME));
        if (!policyName.isEmpty()) {
            requestData.put(Gateway.GUARDRAILS_POLICY_NAME, policyName);
        }
        String contextSource = asString(metadata.remove(CONTEXT_SOURCE)).toUpperCase();
        if (VxlanPolicyDirective.AGENTIC.equals(contextSource) || Constants.AKTO_ENDPOINT_SOURCE_VALUE.equals(contextSource)) {
            requestData.put("contextSource", contextSource);
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
