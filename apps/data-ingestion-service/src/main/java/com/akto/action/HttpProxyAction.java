package com.akto.action;

import com.akto.gateway.Gateway;
import com.akto.log.LoggerMaker;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;


@lombok.Getter
@lombok.Setter
public class HttpProxyAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpProxyAction.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final Gateway gateway = Gateway.getInstance();

    private String url;
    private String path;
    private Map<String, Object> request;
    private Map<String, Object> response;

    private String guardrails;
    private String akto_connector;

    private Map<String, Object> result;
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
                result = new HashMap<>();
                result.put("error", "URL is required");
                return Action.ERROR.toUpperCase();
            }

            if (path == null || path.isEmpty()) {
                loggerMaker.warn("Missing required field: path");
                success = false;
                message = "Missing required field: path";
                result = new HashMap<>();
                result.put("error", "Path is required");
                return Action.ERROR.toUpperCase();
            }

            if (request == null || request.isEmpty()) {
                loggerMaker.warn("Missing required field: request");
                success = false;
                message = "Missing required field: request";
                result = new HashMap<>();
                result.put("error", "Request object is required");
                return Action.ERROR.toUpperCase();
            }

            Map<String, Object> urlQueryParams = new HashMap<>();
            if (guardrails != null && !guardrails.isEmpty()) {
                urlQueryParams.put("guardrails", guardrails);
            }
            if (akto_connector != null && !akto_connector.isEmpty()) {
                urlQueryParams.put("akto_connector", akto_connector);
            }

            loggerMaker.info("URL Query Params - guardrails: " + guardrails + ", akto_connector: " + akto_connector);

            Map<String, Object> proxyData = new HashMap<>();
            proxyData.put("url", url);
            proxyData.put("path", path);
            proxyData.put("request", request);
            if (response != null) {
                proxyData.put("response", response);
            }
            proxyData.put("urlQueryParams", urlQueryParams);

            result = gateway.processHttpProxy(proxyData);
            
            success = result != null;
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
            result = new HashMap<>();
            result.put("error", e.getMessage());

            return Action.ERROR.toUpperCase();
        }
    }
}
