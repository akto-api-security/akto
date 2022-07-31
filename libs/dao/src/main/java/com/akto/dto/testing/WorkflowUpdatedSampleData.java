package com.akto.dto.testing;

public class WorkflowUpdatedSampleData {
    String orig;
    String queryParams;
    String requestHeaders;
    String requestPayload;

    public WorkflowUpdatedSampleData() {}

    public WorkflowUpdatedSampleData(String orig, String queryParams, String requestHeaders, String requestPayload) {
        this.orig = orig;
        this.queryParams = queryParams;
        this.requestHeaders = requestHeaders;
        this.requestPayload = requestPayload;
    }

    public String getOrig() {
        return this.orig;
    }

    public void setOrig(String orig) {
        this.orig = orig;
    }

    public String getQueryParams() {
        return this.queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getRequestHeaders() {
        return this.requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestPayload() {
        return this.requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    @Override
    public String toString() {
        return "{" +
            " orig='" + getOrig() + "'" +
            ", queryParams='" + getQueryParams() + "'" +
            ", requestHeaders='" + getRequestHeaders() + "'" +
            ", requestPayload='" + getRequestPayload() + "'" +
            "}";
    }
}