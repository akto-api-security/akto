package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.utils.KafkaUtils;
import com.akto.publisher.KafkaDataPublisher;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@lombok.Getter
@lombok.Setter
public class HttpProxyAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpProxyAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Initialize Gateway with KafkaDataPublisher
    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
        loggerMaker.info("Gateway configured with KafkaDataPublisher");
    }

    private String url;
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
    private String streaming;

    private String path;
    private String requestHeaders;
    private String responseHeaders;
    private String method;
    private String requestPayload;
    private String responsePayload;
    private String ip;
    private String destIp;
    private String time;
    private String statusCode;
    private String type;
    private String status;
    private String akto_account_id;
    private String akto_vxlan_id;
    private String is_pending;
    private String source;
    private String direction;
    private String tag;
    private String metadata;
    private String process_id;
    private String socket_id;
    private String daemonset_id;
    private String enabled_graph;
    private String contextSource;

    private Map<String, Object> data;
    private boolean success;
    private String message;

    public String httpProxy() {
        try {
            loggerMaker.info("HTTP Proxy API called - path: " + path + ", method: " + method);

            Gateway gateway = Gateway.getInstance();
            ensureDataPublisher(gateway);

            Map<String, Object> requestData = buildRequestData();
            Map<String, Object> result = gateway.processHttpProxy(requestData);

            success = Boolean.TRUE.equals(result.get("success"));
            message = (String) result.get("message");
            data = result;

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

    private Map<String, Object> buildRequestData() {
        Map<String, Object> requestData = new HashMap<>();

        requestData.put("guardrails", guardrails);
        requestData.put("akto_connector", akto_connector);
        requestData.put("ingest_data", ingest_data);

        requestData.put("path", path);
        requestData.put("requestHeaders", requestHeaders);
        requestData.put("responseHeaders", responseHeaders);
        requestData.put("method", method);
        requestData.put("requestPayload", requestPayload);
        requestData.put("responsePayload", responsePayload);
        requestData.put("ip", ip);
        requestData.put("destIp", destIp);
        requestData.put("time", time);
        requestData.put("statusCode", statusCode);
        requestData.put("type", type);
        requestData.put("status", status);
        requestData.put("akto_account_id", akto_account_id);
        requestData.put("akto_vxlan_id", akto_vxlan_id);
        requestData.put("is_pending", is_pending);
        requestData.put("source", source);
        requestData.put("direction", direction);
        requestData.put("tag", tag);
        requestData.put("metadata", metadata);
        requestData.put("process_id", process_id);
        requestData.put("socket_id", socket_id);
        requestData.put("daemonset_id", daemonset_id);
        requestData.put("enabled_graph", enabled_graph);
        requestData.put("contextSource", contextSource);

        return requestData;
    }

    private void ensureDataPublisher(Gateway gateway) {
        if (gateway.getDataPublisher() == null) {
            gateway.setDataPublisher(batch -> KafkaUtils.insertData(batch));
        }
    }

    /**
     * TrueFoundry-specific endpoint wrapper
     * Always operates in sync mode with two scenarios:
     * 
     * response=null: Synchronous input guardrail check (block if failed)
     * response!=null: Async ingestion only (return 200 immediately for monitoring)
     */
    public String truefoundryProxy() {
        try {
            boolean isStreaming = "true".equalsIgnoreCase(streaming);
            boolean hasResponse = responseBody != null && !responseBody.isEmpty();

            loggerMaker.info("TrueFoundry Proxy API called - hasResponse: " + hasResponse + ", streaming: " + isStreaming);

            // Validate that at least requestBody is present
            if (requestBody == null || requestBody.isEmpty()) {
                loggerMaker.error("TrueFoundry: Missing requestBody");
                success = false;
                message = "Missing requestBody";
                data = new HashMap<>();
                data.put("error", "requestBody is required");
                return Action.ERROR.toUpperCase();
            }

            // Streaming mode: extract last prompt-response pair from messages and ingest
            if (isStreaming) {
                loggerMaker.info("TrueFoundry: Streaming mode - extracting last prompt-response pair for ingestion");
                handleStreamingIngestion();
            }

            // Convert to Akto format ONCE at the beginning
            Map<String, Object> tfInput = buildTrueFoundryInput();
            Map<String, Object> aktoFormat = convertTrueFoundryToAktoFormat(tfInput);

            // Scenario 1: response=null (Synchronous input guardrail check)
            if (!hasResponse) {
                loggerMaker.info("TrueFoundry: Synchronous input guardrail check");
                return executeSyncGuardrailCheck(aktoFormat);
            }

            // Scenario 2: response!=null (Async ingestion only)
            loggerMaker.info("TrueFoundry: Async ingestion only");
            executeAsync(aktoFormat, false, true);
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
            // Build flat request map with guardrails enabled, no ingestion
            Map<String, Object> requestData = new HashMap<>(aktoFormat);
            requestData.put("guardrails", "true");
            requestData.putIfAbsent("akto_connector", "truefoundry");

            // Call gateway directly with the flat format
            Map<String, Object> result = gateway.processHttpProxy(requestData);

            // Check if guardrails blocked the request
            // GuardrailsClient returns lowercase "allowed" and "reason" keys
            if (Boolean.TRUE.equals(result.get("success"))) {
                Map<String, Object> guardrailsResult = (Map<String, Object>) result.get("guardrailsResult");
                if (guardrailsResult != null) {
                    Boolean allowed = (Boolean) guardrailsResult.get("allowed");
                    if (allowed != null && !allowed) {
                        String reason = (String) guardrailsResult.get("reason");
                        loggerMaker.warn("TrueFoundry sync request blocked by guardrails: " + reason);

                        // Build flat blocked-response fields for async ingestion
                        Map<String, Object> blockedBody = new HashMap<>();
                        blockedBody.put("x-blocked-by", "Akto Proxy");
                        Map<String, String> respHeaders = new HashMap<>();
                        respHeaders.put("content-type", "application/json");

                        Map<String, Object> aktoFormatWithResponse = new HashMap<>(aktoFormat);
                        aktoFormatWithResponse.put("responsePayload", toJsonString(blockedBody));
                        aktoFormatWithResponse.put("responseHeaders", toJsonString(respHeaders));
                        aktoFormatWithResponse.put("statusCode", "400");
                        aktoFormatWithResponse.put("status", "forbidden");

                        loggerMaker.info("TrueFoundry: Spawning async ingestion for blocked request");
                        executeAsync(aktoFormatWithResponse, false, true);

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
     * Execute guardrails validation and/or data ingestion asynchronously.
     * @param aktoFormat The data in flat Akto format (as returned by convertTrueFoundryToAktoFormat)
     * @param enableGuardrails Whether to enable guardrails validation
     * @param enableIngestion Whether to enable data ingestion
     */
    private void executeAsync(Map<String, Object> aktoFormat, boolean enableGuardrails, boolean enableIngestion) {
        String threadName = "truefoundry-async-" +
                           (enableGuardrails ? "guardrails-" : "") +
                           (enableIngestion ? "ingestion" : "");

        new Thread(() -> {
            try {
                loggerMaker.info("TrueFoundry: Starting " + threadName);

                // Copy all flat fields and add control flags directly on the map
                Map<String, Object> requestData = new HashMap<>(aktoFormat);
                requestData.put("akto_connector", "truefoundry");
                if (enableGuardrails) {
                    requestData.put("guardrails", "true");
                }
                if (enableIngestion) {
                    requestData.put("ingest_data", "true");
                }

                gateway.processHttpProxy(requestData);

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
            Map<String, Object> body = new HashMap<>(requestBody);
            extractLastUserMessage(body);
            tfInput.put("requestBody", body);
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
     * Handle streaming mode: extract the last complete prompt-response pair
     * from the messages array and ingest it as response data.
     * The normal flow continues after this to process the latest prompt.
     */
    @SuppressWarnings("unchecked")
    private void handleStreamingIngestion() {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
        if (messages == null || messages.size() < 2) {
            loggerMaker.info("TrueFoundry streaming: Not enough messages to extract prompt-response pair, skipping");
            return;
        }

        // Find the last assistant message
        Map<String, Object> lastAssistantMessage = null;
        int lastAssistantIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).get("role"))) {
                lastAssistantMessage = messages.get(i);
                lastAssistantIndex = i;
                break;
            }
        }

        if (lastAssistantMessage == null || lastAssistantIndex == 0) {
            loggerMaker.info("TrueFoundry streaming: No complete prompt-response pair found, skipping");
            return;
        }

        // Find the user message preceding the last assistant message
        Map<String, Object> lastUserMessage = null;
        for (int i = lastAssistantIndex - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) {
                lastUserMessage = messages.get(i);
                break;
            }
        }

        if (lastUserMessage == null) {
            loggerMaker.info("TrueFoundry streaming: No user message found before assistant message, skipping");
            return;
        }

        // Build synthetic requestBody with just the user message
        Map<String, Object> syntheticRequestBody = new HashMap<>(requestBody);
        syntheticRequestBody.put("messages", Collections.singletonList(lastUserMessage));

        // Build synthetic responseBody from the assistant message
        Map<String, Object> syntheticResponseBody = new HashMap<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> choiceMessage = new HashMap<>();
        choiceMessage.put("role", "assistant");
        choiceMessage.put("content", lastAssistantMessage.get("content"));
        choice.put("message", choiceMessage);
        syntheticResponseBody.put("choices", Collections.singletonList(choice));

        // Build tfInput with synthetic data
        Map<String, Object> tfInput = new HashMap<>();
        tfInput.put("requestBody", syntheticRequestBody);
        tfInput.put("responseBody", syntheticResponseBody);
        if (config != null) {
            tfInput.put("config", config);
        }
        if (context != null) {
            tfInput.put("context", context);
        }

        // Convert to Akto format and ingest
        Map<String, Object> aktoFormat = convertTrueFoundryToAktoFormat(tfInput);
        executeAsync(aktoFormat, false, true);

        loggerMaker.info("TrueFoundry streaming: Last prompt-response pair ingested successfully");
    }

    @SuppressWarnings("unchecked")
    private void extractLastUserMessage(Map<String, Object> body) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).get("role"))) {
                body.put("messages", Collections.singletonList(messages.get(i)));
                return;
            }
        }
    }

    /**
     * Convert TrueFoundry format to the flat Akto format expected by gateway.processHttpProxy().
     *
     * TrueFoundry input format:
     * {
     *   "requestBody": { "messages": [...], "model": "...", ... },
     *   "responseBody": { "choices": [...], ... },  // optional
     *   "config": { "check_content": true },
     *   "context": { "user": {...}, "metadata": { "ip_address": "..." } }
     * }
     *
     * Flat Akto format (what processHttpProxy expects):
     * {
     *   "path": "/api/llm/chat/completions",
     *   "method": "POST",
     *   "requestHeaders": "{\"content-type\":\"application/json\"}",
     *   "requestPayload": "<JSON string of requestBody>",
     *   "responseHeaders": "{\"content-type\":\"application/json\"}",  // if response present
     *   "responsePayload": "<JSON string of responseBody>",            // if response present
     *   "statusCode": "200",                                           // if response present
     *   "status": "OK",                                                // if response present
     *   "ip": "<extracted from context.metadata.ip_address>",
     *   "tag": "truefoundry",
     *   "source": "TRUEFOUNDRY",
     *   "contextSource": "truefoundry",
     *   "metadata": "<JSON string with truefoundry_context, truefoundry_config, tag>"
     * }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertTrueFoundryToAktoFormat(Map<String, Object> tfInput) {
        Map<String, Object> flatMap = new HashMap<>();

        flatMap.put("path", "/api/llm/chat/completions");
        flatMap.put("method", "POST");

        // Request headers
        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("content-type", "application/json");
        flatMap.put("requestHeaders", toJsonString(reqHeaders));

        // Request payload — serialise requestBody map to JSON string
        Map<String, Object> requestBody = (Map<String, Object>) tfInput.get("requestBody");
        flatMap.put("requestPayload", requestBody != null ? toJsonString(requestBody) : "{}");

        // Extract IP from context.metadata.ip_address
        String extractedIp = null;
        if (tfInput.containsKey("context")) {
            Map<String, Object> context = (Map<String, Object>) tfInput.get("context");
            if (context != null && context.containsKey("metadata")) {
                Map<String, Object> contextMetadata = (Map<String, Object>) context.get("metadata");
                if (contextMetadata != null && contextMetadata.containsKey("ip_address")) {
                    Object ip = contextMetadata.get("ip_address");
                    if (ip != null && !ip.toString().isEmpty()) {
                        extractedIp = ip.toString();
                        loggerMaker.info("Extracted IP address from TrueFoundry context: " + extractedIp);
                    }
                }
            }
        }
        if (extractedIp != null) {
            flatMap.put("ip", extractedIp);
        }

        // Response fields — only populate when a responseBody is present
        Map<String, Object> responseBody = (Map<String, Object>) tfInput.get("responseBody");
        if (responseBody != null && !responseBody.isEmpty()) {
            Map<String, String> respHeaders = new HashMap<>();
            respHeaders.put("content-type", "application/json");
            flatMap.put("responseHeaders", toJsonString(respHeaders));
            flatMap.put("responsePayload", toJsonString(responseBody));
            flatMap.put("statusCode", "200");
            flatMap.put("status", "OK");
        }

        // Metadata — serialise as JSON string
        Map<String, Object> metadataMap = new HashMap<>();
        Map<String, Object> tags = new HashMap<>();
        tags.put("gen-ai", "Gen AI");
        metadataMap.put("tag", tags);
        if (extractedIp != null) {
            metadataMap.put("ip", extractedIp);
        }
        if (tfInput.containsKey("context") && tfInput.get("context") != null) {
            metadataMap.put("truefoundry_context", tfInput.get("context"));
        }
        if (tfInput.containsKey("config") && tfInput.get("config") != null) {
            metadataMap.put("truefoundry_config", tfInput.get("config"));
        }
        flatMap.put("metadata", toJsonString(metadataMap));

        flatMap.put("tag", "truefoundry");
        flatMap.put("source", "TRUEFOUNDRY");
        flatMap.put("contextSource", "truefoundry");

        loggerMaker.info("Converted TrueFoundry format to flat Akto format");
        return flatMap;
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            loggerMaker.error("Failed to serialize to JSON: " + e.getMessage(), e);
            return "{}";
        }
    }
}
