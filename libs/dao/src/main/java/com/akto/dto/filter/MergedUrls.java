package com.akto.dto.filter;

import org.bson.types.ObjectId;

import java.util.Objects;

public class MergedUrls {
    private ObjectId id;

    public static final String URL = "url";
    private String url;

    public static final String METHOD = "method";
    private String method;

    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

    public MergedUrls() {}

    public MergedUrls(String url, String method, int apiCollectionId) {
        this.url = url;
        this.method = method;
        this.apiCollectionId = apiCollectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MergedUrls that = (MergedUrls) o;
        return apiCollectionId == that.apiCollectionId && Objects.equals(url, that.url) && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, apiCollectionId);
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
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

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
