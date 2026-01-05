package com.akto.fast_discovery.dto;

/**
 * ApiId - DTO representing API identifier (collection ID, URL, method).
 * Used for Bloom filter initialization and batch existence checks.
 */
public class ApiId {
    private int apiCollectionId;
    private String url;
    private String method;

    public ApiId() {
    }

    public ApiId(int apiCollectionId, String url, String method) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
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

    @Override
    public String toString() {
        return "ApiId{" +
                "apiCollectionId=" + apiCollectionId +
                ", url='" + url + '\'' +
                ", method='" + method + '\'' +
                '}';
    }
}
