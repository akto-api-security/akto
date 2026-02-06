package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;


@lombok.Getter
@lombok.Setter
public class HttpProxyAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpProxyAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();

    // Initialize Gateway with KafkaDataPublisher
    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
        loggerMaker.info("Gateway configured with KafkaDataPublisher");
    }

    private String url;
    private String path;
    private Map<String, Object> request;
    private Map<String, Object> response;

    // TrueFoundry-specific fields (captures the root-level structure)
    private Map<String, Object> requestBody;
    private Map<String, Object> responseBody;
    private Map<String, Object> config;
    private Map<String, Object> context;

    // Query parameters (from URL query string)
    private String guardrails;
    private String akto_connector;
    private String ingest_data;

    private Map<String, Object> data;
    private boolean success;
    private String message;

    
    public String httpProxy() {
        try {
            loggerMaker.info("HTTP Proxy API called");

            // Validate input
            if (url == null || url.isEmpty()) {
                loggerMaker.warn("Missing required field: url");
                success = false;
                message = "Missing required field: url";
                data = new HashMap<>();
                data.put("error", "URL is required");
                return Action.ERROR.toUpperCase();
            }

            if (path == null || path.isEmpty()) {
                loggerMaker.warn("Missing required field: path");
                success = false;
                message = "Missing required field: path";
                data = new HashMap<>();
                data.put("error", "Path is required");
                return Action.ERROR.toUpperCase();
            }

            if (request == null || request.isEmpty()) {
                loggerMaker.warn("Missing required field: request");
                success = false;
                message = "Missing required field: request";
                data = new HashMap<>();
                data.put("error", "Request object is required");
                return Action.ERROR.toUpperCase();
            }

            Map<String, Object> urlQueryParams = new HashMap<>();
            if (guardrails != null && !guardrails.isEmpty()) {
                urlQueryParams.put("guardrails", guardrails);
            }
            if (akto_connector != null && !akto_connector.isEmpty()) {
                urlQueryParams.put("akto_connector", akto_connector);
            }
            if (ingest_data != null && !ingest_data.isEmpty()) {
                urlQueryParams.put("ingest_data", ingest_data);
            }

            loggerMaker.info("URL Query Params - guardrails: " + guardrails +
                ", akto_connector: " + akto_connector + ", ingest_data: " + ingest_data);

            Map<String, Object> proxyData = new HashMap<>();
            proxyData.put("url", url);
            proxyData.put("path", path);
            proxyData.put("request", request);
            if (response != null) {
                proxyData.put("response", response);
            }
            proxyData.put("urlQueryParams", urlQueryParams);

            data = gateway.processHttpProxy(proxyData);

            success = data != null;
            if (success) {
                message = "Request processed successfully";
            } else {
                message = "Request processing failed";
            }

            loggerMaker.info("HTTP Proxy processed - success: " + success);

            return success ? Action.SUCCESS.toUpperCase() : Action.ERROR.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in HTTP Proxy action: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);

            success = false;
            message = "Unexpected error: " + e.getMessage();
            data = new HashMap<>();
            data.put("error", e.getMessage());

            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * TrueFoundry-specific endpoint wrapper
     * Converts TrueFoundry format to standard http-proxy format,
     * calls httpProxy(), and transforms response for TrueFoundry
     */
    public String truefoundryProxy() {
        try {
            loggerMaker.info("TrueFoundry Proxy API called");

            // Build TrueFoundry input from individual fields
            Map<String, Object> tfInput = new HashMap<>();
            if (requestBody != null) {
                tfInput.put("requestBody", requestBody);
            }
            if (responseBody != null) {
                tfInput.put("responseBody", responseBody);
            }
            if (config != null) {
                tfInput.put("config", config);
            }
            if (context != null) {
                tfInput.put("context", context);
            }

            // Validate that at least requestBody is present
            if (requestBody == null || requestBody.isEmpty()) {
                loggerMaker.warn("TrueFoundry: Missing requestBody");
                success = false;
                message = "Missing requestBody";
                data = new HashMap<>();
                data.put("error", "requestBody is required");
                return Action.ERROR.toUpperCase();
            }

            // Convert TrueFoundry format to standard http-proxy format
            Map<String, Object> aktoFormat = convertTrueFoundryToAktoFormat(tfInput);

            // Set class properties from converted format
            this.url = (String) aktoFormat.get("url");
            this.path = (String) aktoFormat.get("path");
            this.request = (Map<String, Object>) aktoFormat.get("request");
            this.response = (Map<String, Object>) aktoFormat.get("response");

            // Default connector name for TrueFoundry if not provided
            if (this.akto_connector == null || this.akto_connector.isEmpty()) {
                this.akto_connector = "truefoundry";
            }

            loggerMaker.info("Calling httpProxy() with converted TrueFoundry data");

            // Call the existing httpProxy method
            String result = httpProxy();

            // Transform response for TrueFoundry format
            return transformResponseForTrueFoundry(result);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in TrueFoundry Proxy action: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);

            success = false;
            message = "Unexpected error: " + e.getMessage();
            data = new HashMap<>();
            data.put("error", e.getMessage());

            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Transform http-proxy response to TrueFoundry format
     * - SUCCESS → SUCCESS (empty response)
     * - ERROR with guardrails blocked → BLOCKED (HTTP 400)
     * - ERROR → ERROR (HTTP 422)
     */
    @SuppressWarnings("unchecked")
    private String transformResponseForTrueFoundry(String httpProxyResult) {
        if (Action.SUCCESS.toUpperCase().equals(httpProxyResult)) {
            // Check if request was blocked by guardrails
            if (data != null && guardrails != null && !guardrails.isEmpty()) {
                Map<String, Object> guardrailsResult = (Map<String, Object>) data.get("guardrailsResult");
                if (guardrailsResult != null) {
                    Boolean allowed = (Boolean) guardrailsResult.get("Allowed");
                    if (allowed != null && !allowed) {
                        // Request blocked by guardrails - return HTTP 400
                        String reason = (String) guardrailsResult.get("Reason");
                        loggerMaker.warn("TrueFoundry request blocked by guardrails: " + reason);
                        
                        success = false;
                        message = reason != null ? reason : "Request blocked by guardrails";
                        data = new HashMap<>();
                        data.put("error", message);
                        
                        addActionError(message);
                        return "BLOCKED"; // Returns HTTP 400
                    }
                }
            }

            // Request allowed - clear data for empty response
            data = null;
            return Action.SUCCESS.toUpperCase();
        }

        // Pass through ERROR result as-is
        return httpProxyResult;
    }

    /**
     * Convert TrueFoundry format to Akto format
     * 
     * TrueFoundry format:
     * {
     *   "requestBody": { "messages": [...], "model": "...", ... },
     *   "responseBody": { "choices": [...], ... },  // optional
     *   "config": { "check_content": true },
     *   "context": { "user": {...}, "metadata": {...} }
     * }
     * 
     * Akto format:
     * {
     *   "url": "...",
     *   "path": "...",
     *   "request": {
     *     "method": "POST",
     *     "headers": { "content-type": "application/json" },
     *     "body": { "messages": [...] },
     *     "queryParams": {},
     *     "metadata": { "tag": { "gen-ai": "Gen AI" } }
     *   },
     *   "response": { ... } // optional
     * }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertTrueFoundryToAktoFormat(Map<String, Object> tfInput) {
        Map<String, Object> aktoFormat = new HashMap<>();

        // Set URL and path (use defaults or extract from input if available)
        aktoFormat.put("url", url != null && !url.isEmpty() ? url : "https://app.truefoundry.com");
        aktoFormat.put("path", path != null && !path.isEmpty() ? path : "/api/llm/chat/completions");

        // Build request object
        Map<String, Object> aktoRequest = new HashMap<>();
        aktoRequest.put("method", "POST");

        // Set headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        aktoRequest.put("headers", headers);

        // Extract requestBody from TrueFoundry format
        Map<String, Object> requestBody = (Map<String, Object>) tfInput.get("requestBody");
        if (requestBody != null) {
            aktoRequest.put("body", new HashMap<>(requestBody));
        } else {
            aktoRequest.put("body", new HashMap<>());
        }

        // Set queryParams
        aktoRequest.put("queryParams", new HashMap<>());

        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> tags = new HashMap<>();
        tags.put("gen-ai", "Gen AI");
        metadata.put("tag", tags);

        // Add TrueFoundry context to metadata if available
        if (tfInput.containsKey("context")) {
            Map<String, Object> context = (Map<String, Object>) tfInput.get("context");
            if (context != null) {
                metadata.put("truefoundry_context", context);
            }
        }

        // Add TrueFoundry config to metadata if available
        if (tfInput.containsKey("config")) {
            Map<String, Object> config = (Map<String, Object>) tfInput.get("config");
            if (config != null) {
                metadata.put("truefoundry_config", config);
            }
        }

        aktoRequest.put("metadata", metadata);
        aktoFormat.put("request", aktoRequest);

        // Handle response if present
        if (tfInput.containsKey("responseBody")) {
            Map<String, Object> responseBody = (Map<String, Object>) tfInput.get("responseBody");
            if (responseBody != null && !responseBody.isEmpty()) {
                Map<String, Object> aktoResponse = new HashMap<>();
                
                // Set response body
                aktoResponse.put("body", new HashMap<>(responseBody));
                
                // Set response headers
                Map<String, Object> responseHeaders = new HashMap<>();
                responseHeaders.put("content-type", "application/json");
                aktoResponse.put("headers", responseHeaders);
                
                // Set status code and status
                aktoResponse.put("statusCode", 200);
                aktoResponse.put("status", "OK");
                
                aktoFormat.put("response", aktoResponse);
            } else {
                aktoFormat.put("response", null);
            }
        } else {
            aktoFormat.put("response", null);
        }

        loggerMaker.info("Converted TrueFoundry format to Akto format");
        return aktoFormat;
    }
}
