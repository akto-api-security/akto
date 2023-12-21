package com.akto.dto.dependency_flow;


import java.util.Map;
import java.util.Objects;

public class Node {
    private String apiCollectionId;
    private String url;
    private String method;
    private Map<String, Connection> connections;

    public Node() {
    }

    public Node(String apiCollectionId, String url, String method, Map<String, Connection> connections) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.connections = connections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return apiCollectionId.equals(node.apiCollectionId) && url.equals(node.url) && method.equals(node.method);
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

    public Map<String, Connection> getConnections() {
        return connections;
    }

    public void setConnections(Map<String, Connection> connections) {
        this.connections = connections;
    }
}
