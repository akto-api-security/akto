package com.akto.dto.filter;

import org.bson.types.ObjectId;

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
