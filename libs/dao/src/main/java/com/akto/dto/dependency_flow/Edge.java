package com.akto.dto.dependency_flow;


public class Edge {
    private String apiCollectionId;
    private String url;
    private String method;
    private String param;
    private boolean isHeader;
    private int count;
    private int depth;

    public Edge() {
    }

    public Edge(String apiCollectionId, String url, String method, String param, boolean isHeader, int count, int depth) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.param = param;
        this.isHeader = isHeader;
        this.count = count;
        this.depth = depth;
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

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean getIsHeader() {
        return isHeader;
    }

    public void setIsHeader(boolean header) {
        isHeader = header;
    }
}