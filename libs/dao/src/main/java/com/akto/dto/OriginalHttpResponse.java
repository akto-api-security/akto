package com.akto.dto;

import com.akto.util.HttpRequestResponseUtils;
import com.google.gson.Gson;

import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OriginalHttpResponse {

    private static final Gson gson = new Gson();
    private String body;
    private Map<String, List<String>> headers;
    private int statusCode;

    public OriginalHttpResponse() { }

    // before adding any fields make sure to add them to copy function as wel
    public OriginalHttpResponse(String body, Map<String, List<String>> headers, int statusCode) {
        this.body = body;
        this.headers = headers;
        this.statusCode = statusCode;
    }

    public OriginalHttpResponse copy() {
        return new OriginalHttpResponse(this.body, new HashMap<>(this.headers), this.statusCode);
    }

    public void buildFromSampleMessage(String message) {
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String responsePayload = (String) json.get("responsePayload");
        this.body = responsePayload != null ? responsePayload.trim() : null;
        this.headers = OriginalHttpRequest.buildHeadersMap(json, "responseHeaders");
        Object obj = json.get("statusCode");
        if(obj instanceof Double){
            obj = ((Double) obj).intValue();
        }
        this.statusCode = Integer.parseInt(obj.toString());
    }

    public void buildFromSampleMessageNew(HttpResponseParams responseParam) {
        String responsePayload = responseParam.getPayload();
        this.body = responsePayload != null ? responsePayload.trim() : null;
        this.headers = responseParam.getHeaders();
        this.statusCode = responseParam.getStatusCode();
    }

    public void addHeaderFromLine(String line) {
        if (this.headers == null || this.headers.isEmpty()) {
            this.headers = new HashMap<>();
        } 

        int separator = line.indexOf(":");
        if (separator < 0 || separator > line.length()-2) {
            return;
        }
        String headerKey = line.substring(0, separator);
        List<String> headerValues = this.headers.get(headerKey);
        if (headerValues == null) {
            headerValues = new ArrayList<>();
            this.headers.put(headerKey, headerValues);
        }
                
        String headerValue = line.substring(separator+2);

        headerValues.add(headerValue);
    }

    public void appendToPayload(String line) {
        if (this.body == null) {
            this.body = "";
        }

        this.body += line;
    }

    public String findHeaderValue(String headerName) {
        if (this.headers == null ) return null;
        List<String> values = this.headers.get(headerName.trim().toLowerCase());
        if (values == null || values.size() == 0) return null;
        return values.get(0);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

//    public boolean setStatusFromLine(String line) {
//        String[] tokens = line.split(" ");
//        if (tokens.length < 3) {
//            return false;
//        }
//
//        String statusStr = tokens[1];
//        if (NumberUtils.isDigits(statusStr)) {
//            this.statusCode = Integer.parseInt(statusStr);
//            return true;
//        } else {
//            return false;
//        }
//    }
//
    public String getJsonResponseBody() {
        return HttpRequestResponseUtils.rawToJsonString(body, headers);
    }
}
