package com.akto.dto;

public class CodeAnalysisApi {

    private String method;
    private String endpoint;
    private CodeAnalysisApiLocation location;

    public CodeAnalysisApi() {
    }

    public CodeAnalysisApi(String method, String endpoint, CodeAnalysisApiLocation location) {
        this.method = method;
        this.endpoint = endpoint;
        this.location = location;
    }

    public String generateCodeAnalysisApisMapKey() {
        return method + " " + endpoint;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public CodeAnalysisApiLocation getLocation() {
        return location;
    }

    public void setLocation(CodeAnalysisApiLocation location) {
        this.location = location;
    }
}
