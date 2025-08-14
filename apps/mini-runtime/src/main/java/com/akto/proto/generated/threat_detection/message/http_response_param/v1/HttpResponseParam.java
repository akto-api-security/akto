package com.akto.proto.generated.threat_detection.message.http_response_param.v1;

import java.util.Map;
import java.util.HashMap;

public final class HttpResponseParam {
    private String method = "";
    private String path = "";
    private String type = "";
    private Map<String, StringList> requestHeaders = new HashMap<>();
    private String requestPayload = "";
    private int apiCollectionId = 0;
    private int statusCode = 0;
    private String status = "";
    private Map<String, StringList> responseHeaders = new HashMap<>();
    private String responsePayload = "";
    private int time = 0;
    private String aktoAccountId = "";
    private String ip = "";
    private String destIp = "";
    private String direction = "";
    private boolean isPending = false;
    private String source = "";
    private String aktoVxlanId = "";

    private HttpResponseParam() {}

    public static HttpResponseParam getDefaultInstance() {
        return new HttpResponseParam();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private HttpResponseParam result = new HttpResponseParam();

        public Builder setMethod(String method) {
            result.method = method;
            return this;
        }

        public Builder setPath(String path) {
            result.path = path;
            return this;
        }

        public Builder setType(String type) {
            result.type = type;
            return this;
        }

        public Builder putRequestHeaders(String key, StringList value) {
            result.requestHeaders.put(key, value);
            return this;
        }

        public Builder setRequestPayload(String requestPayload) {
            result.requestPayload = requestPayload;
            return this;
        }

        public Builder setApiCollectionId(int apiCollectionId) {
            result.apiCollectionId = apiCollectionId;
            return this;
        }

        public Builder setStatusCode(int statusCode) {
            result.statusCode = statusCode;
            return this;
        }

        public Builder setStatus(String status) {
            result.status = status;
            return this;
        }

        public Builder putResponseHeaders(String key, StringList value) {
            result.responseHeaders.put(key, value);
            return this;
        }

        public Builder setResponsePayload(String responsePayload) {
            result.responsePayload = responsePayload;
            return this;
        }

        public Builder setTime(int time) {
            result.time = time;
            return this;
        }

        public Builder setAktoAccountId(String aktoAccountId) {
            result.aktoAccountId = aktoAccountId;
            return this;
        }

        public Builder setIp(String ip) {
            result.ip = ip;
            return this;
        }

        public Builder setDestIp(String destIp) {
            result.destIp = destIp;
            return this;
        }

        public Builder setDirection(String direction) {
            result.direction = direction;
            return this;
        }

        public Builder setIsPending(boolean isPending) {
            result.isPending = isPending;
            return this;
        }

        public Builder setSource(String source) {
            result.source = source;
            return this;
        }

        public Builder setAktoVxlanId(String aktoVxlanId) {
            result.aktoVxlanId = aktoVxlanId;
            return this;
        }

        public HttpResponseParam build() {
            return result;
        }
    }

    // Getters
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getType() { return type; }
    public Map<String, StringList> getRequestHeaders() { return requestHeaders; }
    public String getRequestPayload() { return requestPayload; }
    public int getApiCollectionId() { return apiCollectionId; }
    public int getStatusCode() { return statusCode; }
    public String getStatus() { return status; }
    public Map<String, StringList> getResponseHeaders() { return responseHeaders; }
    public String getResponsePayload() { return responsePayload; }
    public int getTime() { return time; }
    public String getAktoAccountId() { return aktoAccountId; }
    public String getIp() { return ip; }
    public String getDestIp() { return destIp; }
    public String getDirection() { return direction; }
    public boolean getIsPending() { return isPending; }
    public String getSource() { return source; }
    public String getAktoVxlanId() { return aktoVxlanId; }

    public byte[] toByteArray() {
        // Simple serialization for now
        return toString().getBytes();
    }
}
