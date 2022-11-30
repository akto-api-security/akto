package com.akto.dto.testing;

public class RequestData {

    private String body;

    private String headers;

    private String queryParams;

    private String url;

    private String method;

    public RequestData(String body, String headers, String queryParams, String url, String method) {
        this.body = body;
        this.headers = headers;
        this.queryParams = queryParams;
        this.url = url;
        this.method = method;
    }

    public String getBody() {
        return this.body;
    }

    public String getHeaders() {
        return this.headers;
    }

    public String getQueryParams() {
        return this.queryParams;
    }

    public String getUrl() {
        return this.url;
    }

    public String getMethod() {
        return this.method;
    }

}
