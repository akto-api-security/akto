package com.akto.dto.dependency_flow;

import java.util.Map;
import java.util.Objects;

public class ReverseNode {
    private String apiCollectionId;
    public static final String _API_COLLECTION_ID = "apiCollectionId";
    private String url;
    public static final String _URL = "url";
    private String method;
    public static final String _METHOD = "method";
    private Map<String, ReverseConnection> reverseConnections;
    public static final String _REVERSE_CONNECTIONS = "reverseConnections";

    public ReverseNode() {
    }

    public ReverseNode(String apiCollectionId, String url, String method, Map<String, ReverseConnection> reverseConnection) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.reverseConnections = reverseConnection;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReverseNode that = (ReverseNode) o;
        return apiCollectionId.equals(that.apiCollectionId) && url.equals(that.url) && method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionId, url, method);
    }

    public String getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(String apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, ReverseConnection> getReverseConnections() {
        return reverseConnections;
    }

    public void setReverseConnections(Map<String, ReverseConnection> reverseConnections) {
        this.reverseConnections = reverseConnections;
    }
}
