package com.akto.dto;

public class CodeAnalysisApi {

    private String method;
    private String endpoint;
    private CodeAnalysisApiLocation location;
    private String requestBody;
    private String responseBody;

    public CodeAnalysisApi() {
    }

    public CodeAnalysisApi(String method, String endpoint, CodeAnalysisApiLocation location, String requestBody, String responseBody) {
        this.method = method;
        this.endpoint = endpoint;
        this.location = location;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
    }

    @Override
    public String toString() {
        return "CodeAnalysisApi{" +
                "method='" + method + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", location=" + location +
                '}';
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

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
}
