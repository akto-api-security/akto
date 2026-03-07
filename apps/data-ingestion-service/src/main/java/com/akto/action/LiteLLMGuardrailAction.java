package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.publisher.KafkaDataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import org.apache.struts2.ServletActionContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@lombok.Getter
@lombok.Setter
public class LiteLLMGuardrailAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LiteLLMGuardrailAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String AKTO_CONNECTOR = "litellm";
    private static final String DEFAULT_PATH = "/v1/chat/completions";
    private static final String DEFAULT_AKTO_ACCOUNT_ID = "1000000";
    private static final String DEFAULT_AKTO_VXLAN_ID = "0";
    private static final String DEFAULT_SOURCE = "MIRRORING";
    private static final String DEFAULT_IP = "0.0.0.0";

    static {
        gateway.setDataPublisher(new KafkaDataPublisher());
    }

    private List<String> guardrailTexts;
    private List<String> images;
    private List<Map<String, Object>> tools;
    private List<Map<String, Object>> tool_calls;
    private List<Map<String, Object>> structured_messages;
    private Map<String, Object> request_data;
    private Map<String, Object> request_headers;
    private String input_type; 
    private String litellm_call_id;
    private String litellm_trace_id;
    private String litellm_version;
    private String model;
    private Map<String, Object> additional_provider_specific_params;

    private String action;
    private String blocked_reason;

    @SuppressWarnings("unchecked")
    private void parseJsonBody() {
        try {
            InputStream inputStream = ServletActionContext.getRequest().getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);
            String jsonBody = bufferedReader.lines().collect(Collectors.joining("\n"));

            if (jsonBody == null || jsonBody.isEmpty()) return;

            Map<String, Object> bodyMap = objectMapper.readValue(jsonBody, Map.class);

            this.guardrailTexts = (List<String>) bodyMap.get("texts");
            this.images = (List<String>) bodyMap.get("images");
            this.tools = (List<Map<String, Object>>) bodyMap.get("tools");
            this.tool_calls = (List<Map<String, Object>>) bodyMap.get("tool_calls");
            this.structured_messages = (List<Map<String, Object>>) bodyMap.get("structured_messages");
            this.request_data = (Map<String, Object>) bodyMap.get("request_data");
            this.request_headers = (Map<String, Object>) bodyMap.get("request_headers");
            this.input_type = (String) bodyMap.get("input_type");
            this.litellm_call_id = (String) bodyMap.get("litellm_call_id");
            this.litellm_trace_id = (String) bodyMap.get("litellm_trace_id");
            this.litellm_version = (String) bodyMap.get("litellm_version");
            this.model = (String) bodyMap.get("model");
            this.additional_provider_specific_params = (Map<String, Object>) bodyMap.get("additional_provider_specific_params");

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error parsing LiteLLM JSON body: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
        }
    }

    @SuppressWarnings("unchecked")
    public String guardrail() {
        try {
            parseJsonBody();

            boolean isPreCall = !"response".equalsIgnoreCase(input_type);

            loggerMaker.info("LiteLLM Guardrail API called - input_type: " + input_type
                    + ", call_id: " + litellm_call_id
                    + ", isPreCall: " + isPreCall
                    + ", texts_count: " + (guardrailTexts != null ? guardrailTexts.size() : 0));

            if (guardrailTexts == null || guardrailTexts.isEmpty()) {
                loggerMaker.info("LiteLLM Guardrail: No texts provided, passing through");
                action = "NONE";
                return Action.SUCCESS.toUpperCase();
            }

            if (isPreCall) {
                return handlePreCall();
            } else {
                return handlePostCall();
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in LiteLLM Guardrail action: " + e.getMessage(),
                    LoggerMaker.LogDb.DATA_INGESTION);

            action = "NONE";
            return Action.SUCCESS.toUpperCase();
        }
    }

    @SuppressWarnings("unchecked")
    private String handlePreCall() {
        Map<String, Object> requestData = buildRequestData(true, false);
        Map<String, Object> gatewayResponse = gateway.processHttpProxy(requestData);

        if (gatewayResponse != null && gatewayResponse.containsKey("guardrailsResult")) {
            Map<String, Object> guardrailsResult = (Map<String, Object>) gatewayResponse.get("guardrailsResult");
            if (guardrailsResult != null) {
                Object allowed = guardrailsResult.get("allowed");
                if (allowed == null) {
                    allowed = guardrailsResult.get("Allowed");
                }
                if (allowed != null && !Boolean.TRUE.equals(allowed)
                        && !"true".equalsIgnoreCase(String.valueOf(allowed))) {
                    Object reasonObj = guardrailsResult.get("reason");
                    if (reasonObj == null) {
                        reasonObj = guardrailsResult.get("Reason");
                    }
                    String reason = reasonObj != null ? reasonObj.toString() : "Blocked by Akto Guardrails";

                    loggerMaker.warn("LiteLLM Guardrail: Request BLOCKED - reason: " + reason);

                    ingestBlockedRequest(requestData, reason);

                    action = "BLOCKED";
                    blocked_reason = reason;
                    return Action.SUCCESS.toUpperCase();
                }
            }
        }

        loggerMaker.info("LiteLLM Guardrail: Request ALLOWED");
        action = "NONE";
        return Action.SUCCESS.toUpperCase();
    }

    private String handlePostCall() {
        Map<String, Object> requestData = buildRequestData(false, true);
        Map<String, Object> gatewayResponse = gateway.processHttpProxy(requestData);

        if (gatewayResponse != null && Boolean.TRUE.equals(gatewayResponse.get("success"))) {
            loggerMaker.info("LiteLLM Guardrail: Response data ingested to Kafka");
        } else {
            loggerMaker.warn("LiteLLM Guardrail: Response ingestion may have failed");
        }

        action = "NONE";
        return Action.SUCCESS.toUpperCase();
    }

    private Map<String, Object> buildRequestData(boolean guardrails, boolean ingestData) {
        Map<String, Object> requestData = new HashMap<>();

        boolean isPreCall = guardrails && !ingestData;

        Map<String, Object> requestBody = buildRequestBody();
        String requestPayloadStr = toJsonString(requestBody);

        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("content-type", "application/json");
        reqHeaders.put("host", "litellm.local");
        String requestHeadersStr = toJsonString(reqHeaders);

        Map<String, Object> tagMap = new HashMap<>();
        tagMap.put("gen-ai", "Gen AI");
        tagMap.put("source", "litellm");
        String tagStr = toJsonString(tagMap);

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("gen-ai", "Gen AI");
        metadataMap.put("source", "litellm");
        metadataMap.put("input_type", input_type);
        if (litellm_call_id != null) {
            metadataMap.put("litellm_call_id", litellm_call_id);
        }
        if (litellm_trace_id != null) {
            metadataMap.put("litellm_trace_id", litellm_trace_id);
        }
        if (request_data != null) {
            metadataMap.put("litellm_request_data", request_data);
        }
        String metadataStr = toJsonString(metadataMap);

        String responsePayloadStr;
        String responseHeadersStr;
        if (!isPreCall) {
            Map<String, Object> responseBody = buildResponseBody();
            responsePayloadStr = toJsonString(responseBody);
            Map<String, String> respHeaders = new HashMap<>();
            respHeaders.put("content-type", "application/json");
            responseHeadersStr = toJsonString(respHeaders);
        } else {
            responsePayloadStr = requestPayloadStr;
            responseHeadersStr = requestHeadersStr;
        }

        requestData.put("path", DEFAULT_PATH);
        requestData.put("method", "POST");
        requestData.put("requestHeaders", requestHeadersStr);
        requestData.put("responseHeaders", responseHeadersStr);
        requestData.put("requestPayload", requestPayloadStr);
        requestData.put("responsePayload", responsePayloadStr);
        requestData.put("ip", DEFAULT_IP);
        requestData.put("destIp", "127.0.0.1");
        requestData.put("time", String.valueOf(System.currentTimeMillis()));
        requestData.put("statusCode", "200");
        requestData.put("type", "HTTP/1.1");
        requestData.put("status", "200");
        requestData.put("akto_account_id", DEFAULT_AKTO_ACCOUNT_ID);
        requestData.put("akto_vxlan_id", DEFAULT_AKTO_VXLAN_ID);
        requestData.put("is_pending", "false");
        requestData.put("source", DEFAULT_SOURCE);
        requestData.put("tag", tagStr);
        requestData.put("metadata", metadataStr);
        requestData.put("contextSource", "AGENTIC");
        requestData.put("akto_connector", AKTO_CONNECTOR);

        if (guardrails) {
            requestData.put("guardrails", "true");
        }
        if (ingestData) {
            requestData.put("ingest_data", "true");
        }

        return requestData;
    }

    private Map<String, Object> buildRequestBody() {
        Map<String, Object> body = new HashMap<>();

        if (structured_messages != null && !structured_messages.isEmpty()) {
            body.put("messages", structured_messages);
        } else if (guardrailTexts != null && !guardrailTexts.isEmpty()) {
            List<Map<String, String>> messages = new ArrayList<>();
            for (String text : guardrailTexts) {
                Map<String, String> msg = new HashMap<>();
                msg.put("role", "user");
                msg.put("content", text);
                messages.add(msg);
            }
            body.put("messages", messages);
        }

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        if (model != null) {
            body.put("model", model);
        }

        return body;
    }

    private Map<String, Object> buildResponseBody() {
        Map<String, Object> body = new HashMap<>();

        if (guardrailTexts != null && !guardrailTexts.isEmpty()) {
            List<Map<String, Object>> choices = new ArrayList<>();
            Map<String, Object> choice = new HashMap<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "assistant");
            message.put("content", String.join("\n", guardrailTexts));

            if (tool_calls != null && !tool_calls.isEmpty()) {
                message.put("tool_calls", tool_calls);
            }

            choice.put("message", message);
            choices.add(choice);
            body.put("choices", choices);
        }

        return body;
    }

    private void ingestBlockedRequest(Map<String, Object> requestData, String reason) {
        try {
            Map<String, Object> blockedData = new HashMap<>(requestData);
            blockedData.remove("guardrails");
            blockedData.put("ingest_data", "true");
            blockedData.put("statusCode", "403");
            blockedData.put("status", "BLOCKED");

            Map<String, Object> blockedBody = new HashMap<>();
            blockedBody.put("x-blocked-by", "Akto Proxy");
            blockedBody.put("blocked_reason", reason);
            blockedData.put("responsePayload", toJsonString(blockedBody));

            Map<String, String> respHeaders = new HashMap<>();
            respHeaders.put("content-type", "application/json");
            blockedData.put("responseHeaders", toJsonString(respHeaders));

            gateway.processHttpProxy(blockedData);
            loggerMaker.info("LiteLLM Guardrail: Blocked request ingested to Kafka");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error ingesting blocked request: " + e.getMessage(),
                    LoggerMaker.LogDb.DATA_INGESTION);
        }
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
