package com.akto.dto.testing;

public class RequestData {

    private String body;

    private String headers;

    private String queryParams;

    private String url;

    private String method;

    private String type;

    public RequestData() { }
    public RequestData(String body, String headers, String queryParams, String url, String method, String type) {
        this.body = body;
        this.headers = headers;
        this.queryParams = queryParams;
        this.url = url;
        this.method = method;
        this.type = type;
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

    public String getType() {
        return this.type;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public void setMethod(String method) {
        this.method = method;
    }

    public void setType(String type) {
        this.type = type;
    }
}
