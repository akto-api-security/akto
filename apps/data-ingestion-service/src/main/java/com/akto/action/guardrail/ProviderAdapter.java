package com.akto.action.guardrail;

import java.util.Map;

/**
 * Adapts one AI provider's guardrail-webhook wire format to the shared core.
 * Each provider differs only in two places — how its request is parsed and how
 * its verdict is shaped — so those are the only two methods here. The guardrail
 * + ingestion in between (processHttpProxy) is provider-independent.
 *
 * Adding a provider = one new ProviderAdapter registered in
 * {@link ProviderRegistry}; no new action and no struts change.
 */
public interface ProviderAdapter {

    /** Route key, matching the {provider} path segment (e.g. "claude"). */
    String name();

    /** Parse the raw request body into the neutral ParsedRequest. */
    ParsedRequest parse(Map<String, Object> body);

    /**
     * Render the verdict in this provider's response shape.
     *
     * @param allow       whether inference may proceed
     * @param reason      user-facing block reason (deny only; may be null)
     * @param referenceId opaque correlation id for this evaluation (may be null)
     */
    Map<String, Object> verdict(boolean allow, String reason, String referenceId);
}
