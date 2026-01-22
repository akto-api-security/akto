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

    // Request fields
    private Map<String, Object> requestData;

    // Response fields
    private Map<String, Object> response;
    private boolean success;
    private String message;

    /**
     * HTTP Proxy endpoint that processes requests through akto-gateway
     * @return Action result
     */
    public String httpProxy() {
        try {
            loggerMaker.infoAndAddToDb("HTTP Proxy called with request: " + requestData);

            // Validate request data
            if (requestData == null || requestData.isEmpty()) {
                loggerMaker.warnAndAddToDb("Empty or null request data received");
                requestData = new HashMap<>();
                requestData.put("message", "Empty request - using default");
            }

            // Process request through gateway
            response = gateway.processRequest(requestData);

            // Log activity through gateway
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("endpoint", "/api/http-proxy");
            metadata.put("service", "data-ingestion-service");
            metadata.put("requestSize", requestData.size());

            success = true;
            message = "Request processed successfully through gateway";

            loggerMaker.infoAndAddToDb("HTTP Proxy processed successfully");
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in HTTP Proxy: " + e.getMessage(), LoggerMaker.LogDb.DATA_INGESTION);
            e.printStackTrace();

            success = false;
            message = "Error processing request: " + e.getMessage();
            response = new HashMap<>();
            response.put("error", e.getMessage());

            return Action.ERROR.toUpperCase();
        }
    }

    // Getters and Setters (Lombok handles most, but explicit for clarity)
    public Map<String, Object> getRequestData() {
        return requestData;
    }

    public void setRequestData(Map<String, Object> requestData) {
        this.requestData = requestData;
    }

    public Map<String, Object> getResponse() {
        return response;
    }

    public void setResponse(Map<String, Object> response) {
        this.response = response;
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
