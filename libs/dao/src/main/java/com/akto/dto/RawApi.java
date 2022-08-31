package com.akto.dto;

public class RawApi {

    private OriginalHttpRequest request;
    private OriginalHttpResponse response;

    public RawApi(OriginalHttpRequest request, OriginalHttpResponse response) {
        this.request = request;
        this.response = response;
    }

    public RawApi() { }

    public OriginalHttpRequest getRequest() {
        return request;
    }

    public void setRequest(OriginalHttpRequest request) {
        this.request = request;
    }

    public OriginalHttpResponse getResponse() {
        return response;
    }

    public void setResponse(OriginalHttpResponse response) {
        this.response = response;
    }
}
