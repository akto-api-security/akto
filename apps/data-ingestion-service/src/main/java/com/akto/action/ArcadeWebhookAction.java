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

    private static final String ARCADE_HOST = "arcade.dev";
    private static final String HOOK_TYPE_ACCESS = "tool_access";
    private static final String HOOK_TYPE_PRE = "pre_tool_execution";
    private static final String HOOK_TYPE_POST = "post_tool_execution";
    private static final String AKTO_CONNECTOR = "arcade";

    private static final String DEFAULT_AKTO_ACCOUNT_ID = "1000000";
    private static final String DEFAULT_AKTO_VXLAN_ID = "0";
    private static final String DEFAULT_SOURCE = "MIRRORING";
    private static final String DEFAULT_IP = "0.0.0.0";

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    private String execution_id;
    private Map<String, Object> tool;
    private Map<String, Object> inputs;
    private Map<String, Object> context;

    private Boolean success;
    private Object output;

    private String execution_code;
    private String execution_error;

    private String user_id;
    private Object toolkits;

    private String code;
    private String error_message;
    private Map<String, Object> data;

    @SuppressWarnings("unchecked")
    public String arcade() {
        try {
            loggerMaker.info("Arcade webhook received");

            String requestURI = ServletActionContext.getRequest().getRequestURI();
            String hookType = detectHookTypeFromPath(requestURI);
            loggerMaker.info("Detected Arcade hook type from path: " + hookType + " (URI: " + requestURI + ")");

            parseJsonBody();

            Map<String, Object> requestData = buildRequestData(hookType);
            Map<String, Object> gatewayResponse = gateway.processHttpProxy(requestData);

            if (gatewayResponse != null && gatewayResponse.containsKey("guardrailsResult")) {
                Map<String, Object> guardrailsResult = (Map<String, Object>) gatewayResponse.get("guardrailsResult");
                if (guardrailsResult != null) {
                    Object allowed = guardrailsResult.get("Allowed");
                    if (allowed != null && !Boolean.TRUE.equals(allowed) && !"true".equalsIgnoreCase(String.valueOf(allowed))) {
                        code = "CHECK_FAILED";
                        Object reasonObj = guardrailsResult.get("Reason");
                        String reason = reasonObj != null ? reasonObj.toString() : "Request blocked by guardrails policy";
                        error_message = "Blocked by Akto Guardrails " + reason;
                        loggerMaker.info("Arcade webhook blocked by guardrails - hookType: " + hookType + ", reason: " + reason);

                        ingestBlockedRequest(requestData);

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
            return Action.SUCCESS.toUpperCase();
        }
    }

    @SuppressWarnings("unchecked")
    private void parseJsonBody() {
        try {
            InputStream inputStream = ServletActionContext.getRequest().getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);
            String jsonBody = bufferedReader.lines().collect(Collectors.joining("\n"));

            if (jsonBody == null || jsonBody.isEmpty()) return;

            Map<String, Object> bodyMap = objectMapper.readValue(jsonBody, Map.class);

            this.tool = (Map<String, Object>) bodyMap.get("tool");
            this.inputs = (Map<String, Object>) bodyMap.get("inputs");
            this.context = (Map<String, Object>) bodyMap.get("context");
            this.execution_id = (String) bodyMap.get("execution_id");
            this.toolkits = bodyMap.get("toolkits");
            this.user_id = (String) bodyMap.get("user_id");
            this.success = (Boolean) bodyMap.get("success");
            this.output = bodyMap.get("output");
            this.execution_code = (String) bodyMap.get("execution_code");
            this.execution_error = (String) bodyMap.get("execution_error");

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error parsing JSON body: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private void ingestBlockedRequest(Map<String, Object> requestData) {
        try {
            Map<String, Object> blockedData = new HashMap<>(requestData);
            blockedData.put("statusCode", "403");
            blockedData.put("status", "BLOCKED");

            Map<String, Object> blockedBody = new HashMap<>();
            blockedBody.put("code", "CHECK_FAILED");
            blockedBody.put("error_message", error_message);
            blockedData.put("responsePayload", objectMapper.writeValueAsString(blockedBody));
            blockedData.put("responseHeaders", "{}");

            blockedData.put("ingest_data", "true");
            blockedData.put("guardrails", null);

            gateway.processHttpProxy(blockedData);
            loggerMaker.info("Blocked request ingested into Kafka");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error ingesting blocked request: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    private Map<String, Object> buildRequestData(String hookType) throws Exception {
        Map<String, Object> requestData = new HashMap<>();

        String toolName = extractToolName();
        String path;

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> headers;

        switch (hookType) {
            case HOOK_TYPE_ACCESS:
                path = "/access";
                headers = buildRequestHeaders(hookType, null);
                requestBody.put("user_id", user_id);
                if (toolkits != null) requestBody.put("toolkits", toolkits);
                break;

            case HOOK_TYPE_POST:
                path = "/tools/" + toolName;
                headers = buildRequestHeaders(hookType, toolName);
                if (tool != null) requestBody.put("tool", tool);
                if (inputs != null) requestBody.put("inputs", inputs);

                requestData.put("statusCode", String.valueOf(success != null && success ? 200 : 500));
                requestData.put("status", success != null && success ? "SUCCESS" : "ERROR");
                requestData.put("responsePayload", output != null ? objectMapper.writeValueAsString(output) : "{}");
                requestData.put("responseHeaders", "{}");
                break;

            case HOOK_TYPE_PRE:
            default:
                path = "/tools/" + toolName;
                headers = buildRequestHeaders(hookType, toolName);
                if (tool != null) requestBody.put("tool", tool);
                if (inputs != null) requestBody.put("inputs", inputs);
                break;
        }

        requestData.put("path", path);
        requestData.put("method", "POST");
        requestData.put("requestHeaders", objectMapper.writeValueAsString(headers));
        requestData.put("requestPayload", objectMapper.writeValueAsString(requestBody));

        Map<String, String> tag = new HashMap<>();
        tag.put("gen-ai", "Gen AI");
        requestData.put("tag", objectMapper.writeValueAsString(tag));

        requestData.put("contextSource", "AGENTIC");
        requestData.put("ip", DEFAULT_IP);
        requestData.put("time", String.valueOf(System.currentTimeMillis() / 1000));
        requestData.put("type", "HTTP/1.1");
        requestData.put("akto_account_id", DEFAULT_AKTO_ACCOUNT_ID);
        requestData.put("akto_vxlan_id", DEFAULT_AKTO_VXLAN_ID);
        requestData.put("source", DEFAULT_SOURCE);
        requestData.put("is_pending", "false");
        requestData.put("akto_connector", AKTO_CONNECTOR);

        if (HOOK_TYPE_PRE.equals(hookType)) {
            requestData.put("guardrails", "true");
        } else if (HOOK_TYPE_POST.equals(hookType)) {
            requestData.put("ingest_data", "true");
        }

        return requestData;
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
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) continue;
                String headerKey = "x-arcade-context-" + key;
                if (value instanceof String) {
                    headers.put(headerKey, value);
                } else {
                    try {
                        headers.put(headerKey, objectMapper.writeValueAsString(value));
                    } catch (Exception e) {
                        headers.put(headerKey, value.toString());
                    }
                }
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

    private String detectHookTypeFromPath(String requestURI) {
        if (requestURI == null) {
            return HOOK_TYPE_PRE;
        }

        if (requestURI.contains("/api/arcade/pre") || requestURI.endsWith("/pre") || requestURI.equals("/pre")) {
            return HOOK_TYPE_PRE;
        } else if (requestURI.contains("/api/arcade/post") || requestURI.endsWith("/post") || requestURI.equals("/post")) {
            return HOOK_TYPE_POST;
        }

        return HOOK_TYPE_PRE;
    }

    private String extractToolName() {
        if (tool == null) {
            return "unknown";
        }
        Object name = tool.get("name");
        return name != null ? name.toString() : "unknown";
    }
}
