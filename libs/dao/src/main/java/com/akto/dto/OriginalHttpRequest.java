package com.akto.dto;

import com.google.gson.Gson;

import java.util.*;

public class OriginalHttpRequest {

    private static final Gson gson = new Gson();
    private String url;
    private String queryParams;
    private String method;
    private String body;
    private Map<String, List<String>> headers;

    public OriginalHttpRequest() { }

    public OriginalHttpRequest(String url, String queryParams, String method, String body, Map<String, List<String>> headers) {
        this.url = url;
        this.queryParams = queryParams;
        this.method = method;
        this.body = body;
        this.headers = headers;
    }

    public void buildFromSampleMessage(String message) {
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String rawUrl = (String) json.get("path");
        String[] rawUrlArr = rawUrl.split("\\?");
        this.url = rawUrlArr[0];
        if (rawUrlArr.length > 1) {
            this.queryParams = rawUrlArr[1];
        }

        this.method = (String) json.get("method");

        String requestPayload = (String) json.get("requestPayload");
        this.body = requestPayload.trim();

        this.headers = buildHeadersMap(json, "requestHeaders");
    }

    public String findHeaderValue(String headerName) {
        if (this.headers == null ) return null;
        List<String> values = this.headers.get(headerName.trim().toLowerCase());
        if (values == null || values.size() == 0) return null;
        return values.get(0);
    }

    public String findContentType() {
        return findHeaderValue("content-type");
    }

    public String findHostFromHeader() {
        return findHeaderValue("host");
    }

    public String findProtocolFromHeader() {
        return findHeaderValue("x-forwarded-proto");
    }

    public String getFullUrlWithParams() {
        if (this.queryParams == null || this.queryParams.isEmpty()) return this.url;
        if (url.contains("?")) return this.url + "&" + this.queryParams;
        return this.url + "?" + this.queryParams;
    }

    public static Map<String,List<String>> buildHeadersMap(Map json, String key) {
        return buildHeadersMap((String) json.get(key));
    }

    public static Map<String,List<String>> buildHeadersMap(String headersString) {
        Map headersFromRequest = gson.fromJson(headersString, Map.class);
        Map<String,List<String>> headers = new HashMap<>();
        if (headersFromRequest == null) return headers;
        for (Object k: headersFromRequest.keySet()) {
            List<String> values = headers.getOrDefault(k,new ArrayList<>());
            values.add(headersFromRequest.get(k).toString());
            headers.put(k.toString().toLowerCase(),values);
        }
        return headers;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
