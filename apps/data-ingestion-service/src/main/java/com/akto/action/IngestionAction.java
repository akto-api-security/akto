package com.akto.action;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.data_actor.DbLayer;
import com.akto.dto.CustomDataType;
import com.akto.dto.CustomDataTypeMapper;
import com.akto.log.LoggerMaker;
import com.akto.utils.KafkaUtils;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

public class IngestionAction extends ActionSupport {
   
    String path;
    String requestHeaders;
    String responseHeaders;
    String method;
    String requestPayload;
    String responsePayload;
    String ip;
    String time;
    String statusCode;
    String type;
    String status;
    String akto_account_id;
    String akto_vxlan_id;
    String is_pending;
    String source;
    
    KafkaUtils kafkaUtils = new KafkaUtils();
    private static final Logger logger = LoggerFactory.getLogger(IngestionAction.class);

    public String ingestData() {
        try {
            logger.info("ingestData log path " + path);
            KafkaUtils.insertData(path, requestHeaders, responseHeaders, method, requestPayload, responsePayload, ip, time, statusCode, type, status, akto_account_id, akto_vxlan_id, is_pending, source);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }


    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public String getRequestHeaders() {
        return requestHeaders;
    }
    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }
    public String getResponseHeaders() {
        return responseHeaders;
    }
    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public String getRequestPayload() {
        return requestPayload;
    }
    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }
    public String getResponsePayload() {
        return responsePayload;
    }
    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }
    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public String getTime() {
        return time;
    }
    public void setTime(String time) {
        this.time = time;
    }
    public String getStatusCode() {
        return statusCode;
    }
    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getAkto_account_id() {
        return akto_account_id;
    }
    public void setAkto_account_id(String akto_account_id) {
        this.akto_account_id = akto_account_id;
    }
    public String getAkto_vxlan_id() {
        return akto_vxlan_id;
    }
    public void setAkto_vxlan_id(String akto_vxlan_id) {
        this.akto_vxlan_id = akto_vxlan_id;
    }
    public String getIs_pending() {
        return is_pending;
    }
    public void setIs_pending(String is_pending) {
        this.is_pending = is_pending;
    }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
    
}
