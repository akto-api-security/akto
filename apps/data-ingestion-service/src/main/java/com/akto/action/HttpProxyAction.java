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
    private String sync;  // TrueFoundry sync mode parameter

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
     * Implements sync/async execution based on sync parameter and presence of responseBody:
     * 
     * sync=true & response=null: Synchronous input guardrail check (block if failed)
     * sync=true & response!=null: Async ingestion only (return 200 immediately)
     * sync=false & response=null: No-op (return 200)
     * sync=false & response!=null: Async guardrails + ingestion (return 200 immediately)
     */
    public String truefoundryProxy() {
        try {
            boolean isSyncMode = "true".equalsIgnoreCase(sync);
            boolean hasResponse = responseBody != null && !responseBody.isEmpty();

            loggerMaker.info("TrueFoundry Proxy API called - sync: " + isSyncMode + ", hasResponse: " + hasResponse);

            // Validate that at least requestBody is present
            if (requestBody == null || requestBody.isEmpty()) {
                loggerMaker.warn("TrueFoundry: Missing requestBody");
                success = false;
                message = "Missing requestBody";
                data = new HashMap<>();
                data.put("error", "requestBody is required");
                return Action.ERROR.toUpperCase();
            }

            // Convert to Akto format ONCE at the beginning
            Map<String, Object> tfInput = buildTrueFoundryInput();
            Map<String, Object> aktoFormat = convertTrueFoundryToAktoFormat(tfInput);

            // Scenario 1: sync=true, response=null (Synchronous input guardrail check)
            if (isSyncMode && !hasResponse) {
                loggerMaker.info("TrueFoundry: Synchronous input guardrail check");
                return executeSyncGuardrailCheck(aktoFormat);
            }

            // Scenario 2: sync=true, response!=null (Async ingestion only)
            if (isSyncMode && hasResponse) {
                loggerMaker.info("TrueFoundry: Async ingestion only");
                executeAsync(aktoFormat, false, true);
                return Action.SUCCESS.toUpperCase();
            }

            // Scenario 3: sync=false, response=null (No-op)
            if (!isSyncMode && !hasResponse) {
                loggerMaker.info("TrueFoundry: No-op scenario");
                return Action.SUCCESS.toUpperCase();
            }

            // Scenario 4: sync=false, response!=null (Async guardrails + ingestion)
            loggerMaker.info("TrueFoundry: Async guardrails + ingestion");
            executeAsync(aktoFormat, true, true);
            return Action.SUCCESS.toUpperCase();

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
     * Scenario 1: Synchronous input guardrail check
     * Blocks and returns HTTP 400 if guardrails block the request
     */
    @SuppressWarnings("unchecked")
    private String executeSyncGuardrailCheck(Map<String, Object> aktoFormat) {
        try {
            // Set class properties from aktoFormat
            this.url = (String) aktoFormat.get("url");
            this.path = (String) aktoFormat.get("path");
            this.request = (Map<String, Object>) aktoFormat.get("request");
            this.response = null;  // No response in input guardrail check

            // Force guardrails validation
            this.guardrails = "true";
            this.ingest_data = null;  // No ingestion

            // Default connector name
            if (this.akto_connector == null || this.akto_connector.isEmpty()) {
                this.akto_connector = "truefoundry";
            }

            // Call httpProxy synchronously
            String result = httpProxy();

            // Check if guardrails blocked the request
            if (Action.SUCCESS.toUpperCase().equals(result) && data != null) {
                Map<String, Object> guardrailsResult = (Map<String, Object>) data.get("guardrailsResult");
                if (guardrailsResult != null) {
                    Boolean allowed = (Boolean) guardrailsResult.get("Allowed");
                    if (allowed != null && !allowed) {
                        // Request blocked - return HTTP 400
                        String reason = (String) guardrailsResult.get("Reason");
                        loggerMaker.warn("TrueFoundry sync request blocked by guardrails: " + reason);

                        // Ingest data on separate thread even though request is blocked
                        loggerMaker.info("TrueFoundry: Spawning async ingestion for blocked request");
                        executeAsync(aktoFormat, false, true);

                        success = false;
                        message = reason != null ? reason : "Request blocked by guardrails";
                        data = new HashMap<>();
                        data.put("error", message);

                        addActionError(message);
                        return "BLOCKED";  // HTTP 400
                    }
                }
            }

            // Request allowed - return HTTP 200
            data = null;
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error in sync guardrail check: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute guardrails validation and/or data ingestion asynchronously
     * @param aktoFormat The data in Akto format
     * @param enableGuardrails Whether to enable guardrails validation
     * @param enableIngestion Whether to enable data ingestion
     */
    private void executeAsync(Map<String, Object> aktoFormat, boolean enableGuardrails, boolean enableIngestion) {
        // Build thread name based on enabled features
        String threadName = "truefoundry-async-" + 
                           (enableGuardrails ? "guardrails-" : "") + 
                           (enableIngestion ? "ingestion" : "");
        
        // Spawn background thread
        new Thread(() -> {
            try {
                loggerMaker.info("TrueFoundry: Starting " + threadName);

                // Build proxy data
                Map<String, Object> proxyData = new HashMap<>();
                proxyData.put("url", aktoFormat.get("url"));
                proxyData.put("path", aktoFormat.get("path"));
                proxyData.put("request", aktoFormat.get("request"));
                proxyData.put("response", aktoFormat.get("response"));

                proxyData.put("akto_connector", "truefoundry"); 

                // Set URL query params based on flags
                Map<String, Object> urlQueryParams = new HashMap<>();
                if (enableGuardrails) {
                    urlQueryParams.put("guardrails", "true");
                }
                if (enableIngestion) {
                    urlQueryParams.put("ingest_data", "true");
                }
                urlQueryParams.put("akto_connector", "truefoundry");
                proxyData.put("urlQueryParams", urlQueryParams);

                // Process guardrails and/or ingestion
                gateway.processHttpProxy(proxyData);

                loggerMaker.info("TrueFoundry: " + threadName + " completed");

            } catch (Exception e) {
                loggerMaker.error("Error in " + threadName + ": " + e.getMessage(), e);
            }
        }, threadName).start();
    }

    /**
     * Helper method to build TrueFoundry input map from individual fields
     */
    private Map<String, Object> buildTrueFoundryInput() {
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
        return tfInput;
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

        // Set URL and path (using defaults for TrueFoundry)
        aktoFormat.put("url", "https://app.truefoundry.com");
        aktoFormat.put("path", "/api/llm/chat/completions");

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
