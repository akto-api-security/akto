package com.akto.hybrid_runtime.redaction;

/**
 * One request/response pair of a single endpoint call, as sent to guardrails-service. A null or
 * empty payload means "not sent": that side is not evaluated and has no result.
 */
public class RedactionItem {

    private final String id;
    private final String method;
    private final String host;
    private final String path;
    private final String requestPayload;
    private final String responsePayload;

    public RedactionItem(String id, String method, String host, String path,
                         String requestPayload, String responsePayload) {
        this.id = id;
        this.method = method;
        this.host = host;
        this.path = path;
        this.requestPayload = isBlank(requestPayload) ? null : requestPayload;
        this.responsePayload = isBlank(responsePayload) ? null : responsePayload;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    public String getId() { return id; }
    public String getMethod() { return method; }
    public String getHost() { return host; }
    public String getPath() { return path; }
    public String getRequestPayload() { return requestPayload; }
    public String getResponsePayload() { return responsePayload; }
}
