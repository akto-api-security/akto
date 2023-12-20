package com.akto.util.dependency_flow;


import java.util.Map;
import java.util.Objects;

public class Node {
    String apiCollectionId;
    String url;
    String method;
    Map<String, Connection> connections;

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


}
