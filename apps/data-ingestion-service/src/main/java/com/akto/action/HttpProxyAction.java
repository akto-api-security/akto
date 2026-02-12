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
}
