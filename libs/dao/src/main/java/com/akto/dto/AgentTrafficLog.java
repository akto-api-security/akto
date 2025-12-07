package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DTO for storing raw agent traffic logs from Cloudflare queue.
 * Used for future training on prompts and request payloads.
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentTrafficLog {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

    public static final String REQUEST_PAYLOAD = "requestPayload";
    private String requestPayload;

    public static final String RESPONSE_PAYLOAD = "responsePayload";
    private String responsePayload;

    public static final String METHOD = "method";
    private String method;

    public static final String URL = "url";
    private String url;

    public static final String STATUS_CODE = "statusCode";
    private int statusCode;

    public static final String REQUEST_HEADERS = "requestHeaders";
    private Map<String, List<String>> requestHeaders;

    public static final String RESPONSE_HEADERS = "responseHeaders";
    private Map<String, List<String>> responseHeaders;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public static final String SOURCE_IP = "sourceIP";
    private String sourceIP;

    public static final String DEST_IP = "destIP";
    private String destIP;

    public static final String SOURCE = "source";
    private String source;

    public static final String IS_BLOCKED = "isBlocked";
    private Boolean isBlocked;

    public static final String THREAT_INFO = "threatInfo";
    private String threatInfo;

    public static final String PARENT_MCP_TOOL_NAMES = "parentMcpToolNames";
    private List<String> parentMcpToolNames;

    public static final String EXPIRES_AT = "expiresAt";
    private Date expiresAt;

    /**
     * Factory method to create AgentTrafficLog from HttpResponseParams
     * @param httpResponseParams The HTTP response parameters
     * @return AgentTrafficLog instance
     */
    public static AgentTrafficLog fromHttpResponseParams(HttpResponseParams httpResponseParams) {
        AgentTrafficLog log = new AgentTrafficLog();

        // Collection context 
        if (httpResponseParams.requestParams != null) {
            log.setApiCollectionId(httpResponseParams.requestParams.getApiCollectionId());
        }

        // Core request/response data (raw/unprocessed)
        if (httpResponseParams.requestParams != null) {
            log.setRequestPayload(httpResponseParams.requestParams.getPayload());
            log.setMethod(httpResponseParams.requestParams.getMethod());
            log.setUrl(httpResponseParams.requestParams.getURL());
            log.setRequestHeaders(httpResponseParams.requestParams.getHeaders());
        }
        log.setResponsePayload(httpResponseParams.getPayload());
        log.setStatusCode(httpResponseParams.getStatusCode());
        log.setResponseHeaders(httpResponseParams.getHeaders());

        // Metadata
        log.setTimestamp(httpResponseParams.getTimeOrNow());
        log.setSourceIP(httpResponseParams.getSourceIP());
        log.setDestIP(httpResponseParams.getDestIP());
        if (httpResponseParams.getSource() != null) {
            log.setSource(httpResponseParams.getSource().name());
        }

        // Threat/Guardrail information (defaults to null, populated later if needed)
        log.setIsBlocked(null);
        log.setThreatInfo(null);

        // MCP context (defaults to null, populated later if needed)
        log.setParentMcpToolNames(null);

        // TTL for auto-cleanup (default 7 days)
        long expiryMillis = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000);
        log.setExpiresAt(new Date(expiryMillis));

        return log;
    }
}