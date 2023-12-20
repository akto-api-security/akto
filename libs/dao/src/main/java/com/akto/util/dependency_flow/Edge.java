package com.akto.util.dependency_flow;


public class Edge {
    String apiCollectionId;
    String url;
    String method;
    String param;
    int count;
    int depth;

    public Edge(String apiCollectionId, String url, String method, String param, int count, int depth) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.param = param;
        this.count = count;
        this.depth = depth;
    }
}