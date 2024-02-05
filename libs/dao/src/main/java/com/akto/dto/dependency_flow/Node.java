package com.akto.dto.dependency_flow;


import java.util.Map;
import java.util.Objects;

public class Node {
    private String apiCollectionId;
    public static final String _API_COLLECTION_ID = "apiCollectionId";
    private String url;
    public static final String _URL = "url";
    private String method;
    public static final String _METHOD = "method";
    private Map<String, Connection> connections;
    public static final String _CONNECTIONS = "connections";
    private int maxDepth;
    public static final String _MAX_DEPTH = "maxDepth";

    public Node() {
    }

    public Node(String apiCollectionId, String url, String method, Map<String, Connection> connections) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.connections = connections;
        fillMaxDepth();
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

    public void fillMaxDepth() {
        int maxDepth = 0; // todo: change var name
        for (Connection connection: this.connections.values()) {
            int minDepth = Integer.MAX_VALUE; // todo
            for (Edge edge : connection.getEdges()) {
                minDepth= Math.min(edge.getDepth(),minDepth); // find the minimum value for each edge.. because we want to take the shortest path
            }
            if (minDepth == Integer.MAX_VALUE) continue;
            maxDepth = Math.max(minDepth, maxDepth); // find the max value for all the connections
        }
        this.maxDepth = maxDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
}
