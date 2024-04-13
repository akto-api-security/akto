package com.akto.dto.testing;

public class RequestData {

    private String body;

    private String headers;

    private String queryParams;

    private String url;

    private String method;

    private String type;

    private String regex;

    private String otpRefUuid;

    private String tokenFetchCommand;

    private boolean allowAllStatusCodes;

    public RequestData() { }
    public RequestData(String body, String headers, String queryParams, String url, String method, 
        String type, String regex, String otpRefUuid, String tokenFetchCommand, boolean allowAllStatusCodes) {
        this.body = body;
        this.headers = headers;
        this.queryParams = queryParams;
        this.url = url;
        this.method = method;
        this.type = type;
        this.regex = regex;
        this.otpRefUuid = otpRefUuid;
        this.tokenFetchCommand = tokenFetchCommand;
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

    public String getRegex() {
        return this.regex;
    }

    public String getOtpRefUuid() {
        return this.otpRefUuid;
    }

    public String getTokenFetchCommand() {
        return this.tokenFetchCommand;
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

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public void setOtpRefUuid(String otpRefUuid) {
        this.otpRefUuid = otpRefUuid;
    }

    public void setTokenFetchCommand(String tokenFetchCommand) {
        this.tokenFetchCommand = tokenFetchCommand;
    }

    public boolean getAllowAllStatusCodes() {
        return allowAllStatusCodes;
    }
    public void setAllowAllStatusCodes(boolean allowAllStatusCodes) {
        this.allowAllStatusCodes = allowAllStatusCodes;
    }
}
