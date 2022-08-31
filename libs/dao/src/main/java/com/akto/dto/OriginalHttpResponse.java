package com.akto.dto;

import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

public class OriginalHttpResponse {

    private static final Gson gson = new Gson();
    private String body;
    private Map<String, List<String>> headers;
    private int statusCode;

    public OriginalHttpResponse() { }

    public OriginalHttpResponse(String body, Map<String, List<String>> headers, int statusCode) {
        this.body = body;
        this.headers = headers;
        this.statusCode = statusCode;
    }

    public void buildFromSampleMessage(String message) {
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String requestPayload = (String) json.get("responsePayload");
        this.body = requestPayload.trim();
        this.headers = OriginalHttpRequest.buildHeadersMap(json, "responseHeaders");
        this.statusCode = Integer.parseInt(json.get("statusCode").toString());
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
}
