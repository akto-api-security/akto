package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.akto.utils.SlackUtils;
import com.akto.utils.KafkaUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;


@lombok.Getter
@lombok.Setter
public class HttpProxyAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpProxyAction.class, LoggerMaker.LogDb.DATA_INGESTION);

    private String guardrails;
    private String akto_connector;
    private String ingest_data;

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
            loggerMaker.info("HTTP Proxy API called - path: " + path + ", requestHeaders: " + requestHeaders + ", requestPayload: " + requestPayload);

            Gateway gateway = Gateway.getInstance();
            ensureDataPublisher(gateway);

            Map<String, Object> requestData = buildRequestData();
            Map<String, Object> result = gateway.processHttpProxy(requestData);

            success = Boolean.TRUE.equals(result.get("success"));
            message = (String) result.get("message");
            data = result;

            if (!success) {
                String errorMsg = "[http-proxy] API failed - path: " + path + ", method: " + method
                    + ", account: " + akto_account_id + ", error: " + message;
                loggerMaker.errorAndAddToDb(errorMsg, LoggerMaker.LogDb.DATA_INGESTION);
                sendSlackAlert(errorMsg);
            }

            return success ? Action.SUCCESS.toUpperCase() : Action.ERROR.toUpperCase();

        } catch (Exception e) {
            String errorMsg = "[http-proxy] Unexpected error - path: " + path + ", method: " + method
                + ", account: " + akto_account_id + ", error: " + e.getMessage();
            loggerMaker.errorAndAddToDb(errorMsg, LoggerMaker.LogDb.DATA_INGESTION);
            sendSlackAlert(errorMsg);
            success = false;
            message = "Unexpected error: " + e.getMessage();
            data = new HashMap<>();
            data.put("error", e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    private void sendSlackAlert(String errorMsg) {
        String alertText = errorMsg + ", requestData: " + buildRequestData().toString();
        SlackUtils.sendAlert(alertText);
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
}
