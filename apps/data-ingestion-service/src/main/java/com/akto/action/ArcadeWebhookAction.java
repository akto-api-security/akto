package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;


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

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
        loggerMaker.info("ArcadeWebhookAction: Gateway configured with KafkaDataPublisher");
    }

    private String execution_id;
    private Map<String, Object> tool;       
    private Map<String, Object> inputs;     
    private Map<String, Object> context;   

    private Boolean success;
    private Map<String, Object> output;   
    private String execution_code;
    private String execution_error;

    private String user_id;
    private Map<String, Object> toolkits;   

    private String code;           
    private String error_message;
    private Map<String, Object> data;


    public String arcade() {
        try {
            loggerMaker.info("Arcade webhook received");

            String hookType = detectHookType();
            loggerMaker.info("Detected Arcade hook type: " + hookType);

            Map<String, Object> proxyData = buildProxyData(hookType);

            data = gateway.processHttpProxy(proxyData);

            code = "OK";
            loggerMaker.info("Arcade webhook processed successfully - hookType: " + hookType);
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error processing Arcade webhook: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            code = "CHECK_FAILED";
            error_message = "Internal processing error: " + e.getMessage();
            data = new HashMap<>();
            data.put("error", e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private String detectHookType() {
        if (toolkits != null && user_id != null) {
            return HOOK_TYPE_ACCESS;
        }
        if (output != null || success != null || execution_code != null || execution_error != null) {
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
