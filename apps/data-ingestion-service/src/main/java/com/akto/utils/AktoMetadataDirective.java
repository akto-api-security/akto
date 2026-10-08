package com.akto.utils;

import com.akto.gateway.Gateway;
import com.mongodb.BasicDBObject;

import java.util.Map;

/**
 * Applies the akto_metadata JSON object LiteLLM's built-in Akto guardrail sends per guardrail entry,
 * e.g. {"policy_name": "PII Strict, Secrets"}: policy_name goes to the guardrails service as
 * Gateway.GUARDRAILS_POLICY_NAME. Applied after VxlanPolicyDirective, so it wins over an akto_vxlan_id
 * policy directive.
 */
public final class AktoMetadataDirective {

    public static final String FIELD = "akto_metadata";
    static final String POLICY_NAME = "policy_name";

    private AktoMetadataDirective() {}

    public static void apply(Map<String, Object> requestData) {
        Object raw = requestData.get(FIELD);
        String value = raw != null ? raw.toString().trim() : "";
        if (value.isEmpty()) {
            return;
        }
        String policyName;
        try {
            Object configured = BasicDBObject.parse(value).get(POLICY_NAME);
            policyName = configured != null ? configured.toString().trim() : "";
        } catch (Exception e) {
            return;
        }
        if (!policyName.isEmpty()) {
            requestData.put(Gateway.GUARDRAILS_POLICY_NAME, policyName);
        }
    }
}
