package com.akto.dto.traffic;

import com.akto.dto.type.URLMethods.Method;

public class Key {
    int apiCollectionId;
    public String url;
    public Method method;
    public int responseCode;
    int bucketStartEpoch;
    int bucketEndEpoch;

    public Key() {}

    public Key(int apiCollectionId, String url, Method method, int responseCode, int bucketStartEpoch, int bucketEndEpoch) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.responseCode = responseCode;
        this.bucketStartEpoch = bucketStartEpoch;
        this.bucketEndEpoch = bucketEndEpoch;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Method getMethod() {
        return this.method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public int getBucketStartEpoch() {
        return this.bucketStartEpoch;
    }

    public void setBucketStartEpoch(int bucketStartEpoch) {
        this.bucketStartEpoch = bucketStartEpoch;
    }

    public int getBucketEndEpoch() {
        return this.bucketEndEpoch;
    }

    public void setBucketEndEpoch(int bucketEndEpoch) {
        this.bucketEndEpoch = bucketEndEpoch;
    }

    @Override
    public String toString() {
        return apiCollectionId + " " + url + " " + method + " " + responseCode;
    }

}

