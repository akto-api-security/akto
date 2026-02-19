package com.akto.action;

import com.akto.dto.IngestDataBatch;
import com.akto.gateway.GuardrailsClient;
import com.akto.log.LoggerMaker;
import com.akto.utils.KafkaUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;


@lombok.Getter
@lombok.Setter
public class HttpProxyAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpProxyAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final GuardrailsClient guardrailsClient = new GuardrailsClient();

    // Query parameters (from URL query string)
    private String guardrails;
    private String akto_connector;
    private String ingest_data;

    // IngestDataBatch fields (flat format from traffic sources)
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

    // Response fields
    private Map<String, Object> data;
    private boolean success;
    private String message;

    public String httpProxy() {
        try {
            loggerMaker.info("HTTP Proxy API called - path: " + path + ", method: " + method +
                ", ip: " + ip + ", contextSource: " + contextSource +
                ", guardrails: " + guardrails + ", ingest_data: " + ingest_data +
                ", akto_connector: " + akto_connector);

            if (requestPayload == null || requestPayload.isEmpty()) {
                loggerMaker.warn("Missing required field: requestPayload");
                success = false;
                message = "Missing required field: requestPayload";
                data = new HashMap<>();
                data.put("error", "requestPayload is required");
                return Action.ERROR.toUpperCase();
            }

            data = new HashMap<>();

            // Step 1: Call guardrails if guardrails=true
            if ("true".equalsIgnoreCase(guardrails)) {
                Map<String, Object> guardrailsResponse = callGuardrails();
                data.put("guardrailsResult", guardrailsResponse);
            }

            // Step 2: Ingest data to Kafka if ingest_data=true
            if ("true".equalsIgnoreCase(ingest_data)) {
                ingestToKafka();
            }

            success = true;
            message = "Request processed successfully";

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in HTTP Proxy action: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            success = false;
            message = "Unexpected error: " + e.getMessage();
            data = new HashMap<>();
            data.put("error", e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private Map<String, Object> callGuardrails() {
        Map<String, Object> validateRequest = new HashMap<>();
        validateRequest.put("requestPayload", requestPayload);
        validateRequest.put("contextSource", contextSource);

        if (path != null) validateRequest.put("path", path);
        if (requestHeaders != null) validateRequest.put("requestHeaders", requestHeaders);
        if (responseHeaders != null) validateRequest.put("responseHeaders", responseHeaders);
        if (method != null) validateRequest.put("method", method);
        if (responsePayload != null) validateRequest.put("responsePayload", responsePayload);
        if (ip != null) validateRequest.put("ip", ip);
        if (destIp != null) validateRequest.put("destIp", destIp);
        if (time != null) validateRequest.put("time", time);
        if (statusCode != null) validateRequest.put("statusCode", statusCode);
        if (type != null) validateRequest.put("type", type);
        if (status != null) validateRequest.put("status", status);
        if (akto_account_id != null) validateRequest.put("akto_account_id", akto_account_id);
        if (akto_vxlan_id != null) validateRequest.put("akto_vxlan_id", akto_vxlan_id);
        if (is_pending != null) validateRequest.put("is_pending", is_pending);
        if (source != null) validateRequest.put("source", source);
        if (direction != null) validateRequest.put("direction", direction);
        if (tag != null) validateRequest.put("tag", tag);
        if (metadata != null) validateRequest.put("metadata", metadata);

        loggerMaker.info("Calling guardrails /validate/request, contextSource: " + contextSource);

        Map<String, Object> guardrailsResponse = guardrailsClient.callValidateRequest(validateRequest);

        loggerMaker.info("Guardrails response - allowed: "
            + (guardrailsResponse != null ? guardrailsResponse.get("allowed") : "null"));

        return guardrailsResponse;
    }

    private void ingestToKafka() {
        IngestDataBatch batch = new IngestDataBatch();
        batch.setPath(path);
        batch.setRequestHeaders(requestHeaders);
        batch.setResponseHeaders(responseHeaders);
        batch.setMethod(method);
        batch.setRequestPayload(requestPayload);
        batch.setResponsePayload(responsePayload);
        batch.setIp(ip);
        batch.setDestIp(destIp);
        batch.setTime(time);
        batch.setStatusCode(statusCode);
        batch.setType(type);
        batch.setStatus(status);
        batch.setAkto_account_id(akto_account_id);
        batch.setAkto_vxlan_id(akto_vxlan_id);
        batch.setIs_pending(is_pending);
        batch.setSource(source);
        batch.setDirection(direction);
        batch.setProcess_id(process_id);
        batch.setSocket_id(socket_id);
        batch.setDaemonset_id(daemonset_id);
        batch.setEnabled_graph(enabled_graph);
        batch.setTag(tag);

        KafkaUtils.insertData(batch);
        loggerMaker.info("Data ingested to Kafka - path: " + path + ", method: " + method);
    }
}
