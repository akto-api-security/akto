package com.akto.dto.dependency_flow;

public class ReverseEdge {
    private String apiCollectionId;
    private String url;
    private String method;
    private String param;

    private int count;

    private boolean isUrlParam;
    private boolean isHeader;


    public ReverseEdge() {
    }

    public ReverseEdge(String apiCollectionId, String url, String method, String param, int count, boolean isUrlParam, boolean isHeader) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.param = param;
        this.count = count;
        this.isUrlParam = isUrlParam;
        this.isHeader = isHeader;
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

    public boolean isUrlParam() {
        return isUrlParam;
    }

    public void setIsUrlParam(boolean urlParam) {
        isUrlParam = urlParam;
    }

    public boolean getIsHeader() {
        return isHeader;
    }

    public void setIsHeader(boolean header) {
        isHeader = header;
    }
}
