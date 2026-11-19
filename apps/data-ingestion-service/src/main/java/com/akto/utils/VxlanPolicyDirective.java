package com.akto.utils;

import com.akto.gateway.Gateway;
import com.akto.util.Constants;

import java.util.Map;

/**
 * Lets a connector's own config pick the guardrail context source and policies through akto_vxlan_id,
 * the one free-form field LiteLLM's built-in Akto guardrail sends (per guardrail entry):
 * <pre>
 *   akto_vxlan_id: "policy:&lt;contextSource&gt;:&lt;policy name&gt;[,&lt;policy name&gt;...]"
 *   e.g. policy:ENDPOINT:PII Strict   policy:AGENTIC:Secrets,Prompt Injection   policy::Secrets
 * </pre>
 * The directive is consumed here: akto_vxlan_id is reset to "0" (what LiteLLM sends by default) so
 * nothing downstream mistakes it for a collection id, the context source (AGENTIC or ENDPOINT; empty
 * or anything else keeps the request's own) replaces contextSource, and the policy names go to the
 * guardrails service as Gateway.GUARDRAILS_POLICY_NAME. Only the first two ':' split, so policy
 * names may contain ':'. A vxlan id without the prefix is left untouched.
 */
public final class VxlanPolicyDirective {

    static final String VXLAN_ID = "akto_vxlan_id";
    static final String PREFIX = "policy:";
    static final String DEFAULT_VXLAN_ID = "0";
    static final String AGENTIC = "AGENTIC";

    private VxlanPolicyDirective() {}

    /** Consumes a policy directive in requestData's akto_vxlan_id, if there is one. */
    public static void apply(Map<String, Object> requestData) {
        Object raw = requestData.get(VXLAN_ID);
        String value = raw != null ? raw.toString().trim() : "";
        if (!value.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return;
        }
        requestData.put(VXLAN_ID, DEFAULT_VXLAN_ID);

        String[] parts = value.substring(PREFIX.length()).split(":", 2);
        String contextSource = parts[0].trim().toUpperCase();
        if (AGENTIC.equals(contextSource) || Constants.AKTO_ENDPOINT_SOURCE_VALUE.equals(contextSource)) {
            requestData.put("contextSource", contextSource);
        }
        String policyName = parts.length > 1 ? parts[1].trim() : "";
        if (!policyName.isEmpty()) {
            requestData.put(Gateway.GUARDRAILS_POLICY_NAME, policyName);
        }
    }
}
