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

    private Map<String, Object> proxyData;

    private Map<String, Object> result;
    private boolean success;
    private String message;

    
    public String httpProxy() {
        try {
            loggerMaker.info("HTTP Proxy API called");

            // Validate input
            if (proxyData == null || proxyData.isEmpty()) {
                loggerMaker.warnAndAddToDb("Empty proxy data received");
                success = false;
                message = "Empty or null proxy data";
                result = new HashMap<>();
                result.put("error", "Proxy data is required");
                return Action.ERROR.toUpperCase();
            }

            result = gateway.processHttpProxy(proxyData);

            Object successObj = result.get("success");
            success = (successObj instanceof Boolean) ? (Boolean) successObj : false;

            if (success) {
                message = "Request processed successfully";
            } else {
                Object errorObj = result.get("error");
                message = (errorObj != null) ? errorObj.toString() : "Request processing failed";
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

    public Map<String, Object> getProxyData() {
        return proxyData;
    }

    public void setProxyData(Map<String, Object> proxyData) {
        this.proxyData = proxyData;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
