package com.akto.action.guardrail;

import java.util.Map;

/**
 * Provider-neutral view of one guardrail request, produced by a
 * {@link ProviderAdapter} from whatever wire format the vendor sent. Each vendor
 * parses its own body differently but produces this same shape, so the shared
 * action never depends on a vendor-specific format.
 */
public class ParsedRequest {
    /** The transcript flattened into a single scannable string. */
    public String prompt;
    /** Vendor's per-request id (Claude's request_id == webhook-id). */
    public String requestId;
    /** Vendor's conversation id, when present. */
    public String sessionId;
    /** Vendor's organization/tenant id, when present. */
    public String tenantId;
    /** Public model identifier, when present. */
    public String model;
    /** The principal the request is attributed to, when present. */
    public Map<String, Object> actor;
    /** Any extra vendor metadata to carry through. */
    public Map<String, Object> metadata;
    /**
     * True when the request needs a verdict but must be allowed without
     * inspection (e.g. an unrecognized future event type).
     */
    public boolean allowShortCircuit;
}
