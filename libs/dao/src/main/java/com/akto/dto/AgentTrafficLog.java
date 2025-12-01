package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DTO for storing raw agent traffic logs for future training and analysis.
 * This stores unprocessed request/response data with collection context.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentTrafficLog {
    
    // Collection and account context
    private int apiCollectionId;
    private String accountId;
    
    // Core request/response data (raw/unprocessed)
    private String requestPayload;
    private String responsePayload;
    private String method;
    private String url;
    private int statusCode;
    
    // Headers (for context)
    private Map<String, List<String>> requestHeaders;
    private Map<String, List<String>> responseHeaders;
    
    // Metadata
    private int timestamp;
    private String sourceIP;
    private String destIP;
    private String source;  // HAR, PCAP, MIRRORING, SDK, OTHER, POSTMAN
    
    // Threat/Guardrail information
    private Boolean isBlocked;
    private String threatInfo;  // JSON string with threat details
    
    // MCP context
    private List<String> parentMcpToolNames;
    
    // TTL for auto-cleanup (MongoDB will auto-delete based on this)
    private Date expiresAt;
    
    /**
     * Create AgentTrafficLog from HttpResponseParams
     */
    public static AgentTrafficLog fromHttpResponseParams(HttpResponseParams params, Boolean isBlocked, String threatInfo) {
        AgentTrafficLog log = new AgentTrafficLog();
        
        // Collection and account
        if (params.getRequestParams() != null) {
            log.setApiCollectionId(params.getRequestParams().getApiCollectionId());
        }
        log.setAccountId(params.getAccountId());
        
        // Request data
        if (params.getRequestParams() != null) {
            log.setRequestPayload(params.getRequestParams().getPayload());
            log.setMethod(params.getRequestParams().getMethod());
            log.setUrl(params.getRequestParams().getURL());
            log.setRequestHeaders(params.getRequestParams().getHeaders());
        }
        
        // Response data
        log.setResponsePayload(params.getPayload());
        log.setStatusCode(params.getStatusCode());
        log.setResponseHeaders(params.getHeaders());
        
        // Metadata
        log.setTimestamp(params.getTime());
        log.setSourceIP(params.getSourceIP());
        log.setDestIP(params.getDestIP());
        log.setSource(params.getSource() != null ? params.getSource().name() : null);
        
        // Threat info
        log.setIsBlocked(isBlocked);
        log.setThreatInfo(threatInfo);
        
        // MCP context
        log.setParentMcpToolNames(params.getParentMcpToolNames());
        
        // Set TTL - 90 days from now
        long expiryMillis = System.currentTimeMillis() + (2L * 24 * 60 * 60 * 1000);
        log.setExpiresAt(new Date(expiryMillis));
        
        return log;
    }
    
    /**
     * Create AgentTrafficLog from HttpResponseParams with default values
     */
    public static AgentTrafficLog fromHttpResponseParams(HttpResponseParams params) {
        return fromHttpResponseParams(params, null, null);
    }
}

