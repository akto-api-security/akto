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

    // Input fields (direct mapping from JSON)
    private String url;
    private String path;
    private Map<String, Object> request;
    private Map<String, Object> response;

    // Output
    private Map<String, Object> result;
    private boolean success;
    private String message;

    // Additional response fields (extracted from result for convenience)
    private Boolean guardrailsApplied;
    private String adapterUsed;
    private Boolean blocked;

    
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

            // Build proxyData object for Gateway
            Map<String, Object> proxyData = new HashMap<>();
            proxyData.put("url", url);
            proxyData.put("path", path);
            proxyData.put("request", request);
            if (response != null) {
                proxyData.put("response", response);
            }

            // Process through Gateway
            result = gateway.processHttpProxy(proxyData);

            // Extract success flag
            Object successObj = result.get("success");
            success = (successObj instanceof Boolean) ? (Boolean) successObj : false;

            // Extract additional fields from result
            Object guardrailsAppliedObj = result.get("guardrailsApplied");
            guardrailsApplied = (guardrailsAppliedObj instanceof Boolean) ? (Boolean) guardrailsAppliedObj : null;

            Object adapterUsedObj = result.get("adapterUsed");
            adapterUsed = (adapterUsedObj instanceof String) ? (String) adapterUsedObj : null;

            Object blockedObj = result.get("blocked");
            blocked = (blockedObj instanceof Boolean) ? (Boolean) blockedObj : null;

            // Build message
            if (success) {
                if (Boolean.TRUE.equals(guardrailsApplied)) {
                    message = "Request processed successfully with guardrails validation (adapter: " + adapterUsed + ")";
                } else {
                    message = "Request processed successfully";
                }
            } else {
                if (Boolean.TRUE.equals(blocked)) {
                    message = "Request blocked by guardrails";
                } else {
                    Object errorObj = result.get("error");
                    message = (errorObj != null) ? errorObj.toString() : "Request processing failed";
                }
            }

            loggerMaker.info("HTTP Proxy processed - success: " + success +
                           ", guardrailsApplied: " + guardrailsApplied +
                           ", adapterUsed: " + adapterUsed +
                           ", blocked: " + blocked);

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
