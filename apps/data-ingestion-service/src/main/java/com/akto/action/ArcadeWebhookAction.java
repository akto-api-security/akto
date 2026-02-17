package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.apache.struts2.ServletActionContext;


@lombok.Getter
@lombok.Setter
public class ArcadeWebhookAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArcadeWebhookAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ARCADE_HOST = "arcade.gateway";
    private static final String HOOK_TYPE_ACCESS = "tool_access";
    private static final String HOOK_TYPE_PRE = "pre_tool_execution";
    private static final String HOOK_TYPE_POST = "post_tool_execution";
    private static final String AKTO_CONNECTOR = "arcade";

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
        loggerMaker.info("ArcadeWebhookAction: Gateway configured with KafkaDataPublisher");
    }

    private String execution_id;
    private Map<String, Object> tool;       
    private Map<String, Object> inputs;     
    private Map<String, Object> context;   

    private Boolean success;
    private Object output;   // Arcade schema: "any JSON type — string, number, object, array, etc."

    private String execution_code;
    private String execution_error;

    private String user_id;
    private Object toolkits;   

    private String code;           
    private String error_message;
    private Map<String, Object> data;


    public String arcade() {
        try {
            loggerMaker.info("Arcade webhook received");

            // Detect hook type from request URL path
            String requestURI = ServletActionContext.getRequest().getRequestURI();
            String hookType = detectHookTypeFromPath(requestURI);
            loggerMaker.info("Detected Arcade hook type from path: " + hookType + " (URI: " + requestURI + ")");

            // Manually parse JSON body to bypass Struts JSON interceptor issues with polymorphic types
            String jsonBody = "";
            try {
                InputStream inputStream = ServletActionContext.getRequest().getInputStream();
                InputStreamReader reader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(reader);
                jsonBody = bufferedReader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error reading request body: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            }

            if (jsonBody != null && !jsonBody.isEmpty()) {
                try {
                    Map<String, Object> bodyMap = objectMapper.readValue(jsonBody, Map.class);
                    
                    // Manually populate fields
                    this.tool = (Map<String, Object>) bodyMap.get("tool");
                    this.inputs = (Map<String, Object>) bodyMap.get("inputs");
                    this.context = (Map<String, Object>) bodyMap.get("context");
                    this.execution_id = (String) bodyMap.get("execution_id");
                    this.toolkits = bodyMap.get("toolkits");
                    
                    this.user_id = (String) bodyMap.get("user_id");
                    
                    this.success = (Boolean) bodyMap.get("success");
                    this.output = bodyMap.get("output"); // Can be any type
                    this.execution_code = (String) bodyMap.get("execution_code");
                    this.execution_error = (String) bodyMap.get("execution_error");

                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error parsing JSON body: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
                    // Continue, as some fields might be missing or null is fine
                }
            }

            Map<String, Object> proxyData = buildProxyData(hookType);

            Map<String, Object> gatewayResponse = gateway.processHttpProxy(proxyData);

            // Check guardrails result if guardrails was applied
            if (gatewayResponse != null && gatewayResponse.containsKey("guardrailsResult")) {
                Map<String, Object> guardrailsResult = (Map<String, Object>) gatewayResponse.get("guardrailsResult");
                if (guardrailsResult != null) {
                    Object allowed = guardrailsResult.get("Allowed");
                    // Check if request was blocked by guardrails
                    if (allowed != null && !Boolean.TRUE.equals(allowed) && !"true".equalsIgnoreCase(String.valueOf(allowed))) {
                        // Request was blocked by guardrails
                        code = "CHECK_FAILED";
                        Object reasonObj = guardrailsResult.get("Reason");
                        String reason = reasonObj != null ? reasonObj.toString() : "Request blocked by guardrails policy";
                        error_message = reason;
                        loggerMaker.info("Arcade webhook blocked by guardrails - hookType: " + hookType + ", reason: " + reason);
                        // Still return HTTP 200 - Arcade expects 200 even for CHECK_FAILED
                        // The error is communicated via the `code` field, not the HTTP status
                        return Action.SUCCESS.toUpperCase();
                    }
                }
            }

            code = "OK";
            loggerMaker.info("Arcade webhook processed successfully - hookType: " + hookType);
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error processing Arcade webhook: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            code = "CHECK_FAILED";
            error_message = "Internal processing error: " + e.getMessage();
            // Return SUCCESS (HTTP 200) — Arcade expects 200 even for CHECK_FAILED.
            // The error is communicated via the `code` field, not the HTTP status.
            return Action.SUCCESS.toUpperCase();
        }
    }

    private String detectHookTypeFromPath(String requestURI) {
        if (requestURI == null) {
            return HOOK_TYPE_PRE; // Default fallback
        }
        
        if (requestURI.contains("/api/arcade/pre") || requestURI.endsWith("/pre") || requestURI.equals("/pre")) {
            return HOOK_TYPE_PRE;
        } else if (requestURI.contains("/api/arcade/post") || requestURI.endsWith("/post") || requestURI.equals("/post")) {
            return HOOK_TYPE_POST;
        }
        
        return HOOK_TYPE_PRE;
    }


    private Map<String, Object> buildProxyData(String hookType) throws Exception {
        Map<String, Object> proxyData = new HashMap<>();

        String toolName = extractToolName();
        String path;
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> responseMap = null;

        switch (hookType) {
            case HOOK_TYPE_ACCESS:
                path = "/access";
                requestMap.put("method", "POST");
                requestMap.put("headers", buildRequestHeaders(hookType, null));
                Map<String, Object> accessBody = new HashMap<>();
                accessBody.put("user_id", user_id);
                if (toolkits != null) {
                    accessBody.put("toolkits", toolkits);
                }
                requestMap.put("body", accessBody);
                break;

            case HOOK_TYPE_POST:
                path = "/tools/" + toolName;
                requestMap.put("method", "POST");
                requestMap.put("headers", buildRequestHeaders(hookType, toolName));
                requestMap.put("body", inputs != null ? inputs : new HashMap<>());
                responseMap = new HashMap<>();
                responseMap.put("statusCode", success != null && success ? 200 : 500);
                responseMap.put("status", success != null && success ? "SUCCESS" : "ERROR");
                Map<String, Object> responseBody = new HashMap<>();
                if (output != null) {
                    responseBody.put("output", output);
                }
                if (execution_error != null) {
                    responseBody.put("error", execution_error);
                }
                if (execution_code != null) {
                    responseBody.put("execution_code", execution_code);
                }
                responseMap.put("body", objectMapper.writeValueAsString(responseBody));
                responseMap.put("headers", new HashMap<>());
                break;

            case HOOK_TYPE_PRE:
            default:
                path = "/tools/" + toolName;
                requestMap.put("method", "POST");
                requestMap.put("headers", buildRequestHeaders(hookType, toolName));
                requestMap.put("body", inputs != null ? inputs : new HashMap<>());
                break;
        }

        String url = "https://" + ARCADE_HOST + path;
        proxyData.put("url", url);
        proxyData.put("path", path);
        proxyData.put("request", requestMap);
        if (responseMap != null) {
            proxyData.put("response", responseMap);
        }

        Map<String, Object> urlQueryParams = new HashMap<>();
        
        // Enable guardrails for pre and post execution hooks
        if (HOOK_TYPE_PRE.equals(hookType) || HOOK_TYPE_POST.equals(hookType)) {
            urlQueryParams.put("guardrails", "true");
            urlQueryParams.put("akto_connector", AKTO_CONNECTOR);
            loggerMaker.info("Guardrails enabled for hook type: " + hookType);
        }
        
        // Always enable data ingestion
        urlQueryParams.put("ingest_data", "true");
        proxyData.put("urlQueryParams", urlQueryParams);

        return proxyData;
    }

    private Map<String, Object> buildRequestHeaders(String hookType, String toolName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("host", ARCADE_HOST);
        headers.put("content-type", "application/json");
        headers.put("x-arcade-event", hookType);
        if (execution_id != null) {
            headers.put("x-arcade-execution-id", execution_id);
        }
        if (toolName != null) {
            headers.put("x-arcade-tool", toolName);
        }
        if (context != null) {
            Object userId = context.get("user_id");
            if (userId != null) {
                headers.put("x-arcade-user-id", userId.toString());
            }
        }
        if (tool != null) {
            Object toolkit = tool.get("toolkit");
            if (toolkit != null) {
                headers.put("x-arcade-toolkit", toolkit.toString());
            }
            Object version = tool.get("version");
            if (version != null) {
                headers.put("x-arcade-tool-version", version.toString());
            }
        }
        return headers;
    }

    private String extractToolName() {
        if (tool == null) {
            return "unknown";
        }
        Object name = tool.get("name");
        return name != null ? name.toString() : "unknown";
    }
}
