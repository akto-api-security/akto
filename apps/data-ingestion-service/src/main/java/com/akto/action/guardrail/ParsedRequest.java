package com.akto.action.guardrail;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral view of one guardrail request, produced by a
 * {@link ProviderAdapter} from whatever wire format the vendor sent. Each vendor
 * parses its own body differently but produces this same shape, so the shared
 * action never depends on a vendor-specific format.
 */
public class ParsedRequest {

    public static class ToolCall {
        public String id;
        public String name;
        public Object input;
        public Map<String, Object> info;
        public String text;
    }

    /** The transcript flattened into a single scannable string. */
    public String prompt;
    /**
     * The part of the transcript that still needs a verdict, when narrower than
     * {@link #prompt} (e.g. only the turn after the last assistant message).
     * Null means judge the whole prompt. The full prompt is still ingested.
     */
    public String verdictPrompt;
    /**
     * The model's last response in the transcript (the latest assistant turn),
     * when present. It already reached the user, so it is judged against
     * response policies and recorded, never used to deny the request.
     */
    public String lastResponse;
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
    /**
     * Advisory source application, when present (e.g. Claude's
     * source.application: "claude-code" = CLI, "claude-ai" = app,
     * "config-test" = connection test). Open string — unknown values are
     * carried through, never rejected. Advisory routing metadata only; must
     * not drive a security-critical policy decision.
     */
    public String application;
    /** Any extra vendor metadata to carry through. */
    public Map<String, Object> metadata;
    /**
     * True when the request needs a verdict but must be allowed without
     * inspection (e.g. an unrecognized future event type).
     */
    public boolean allowShortCircuit;
    /** Hook event: "prompt" or "tool_call". */
    public String eventType;
    public List<ToolCall> toolCalls;
}
