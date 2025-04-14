package com.akto.dto.sql;

public class SampleDataAltCopy {
    String id;
    String sample;
    int apiCollectionId;
    String method;
    String url;
    int responseCode;
    int timestamp;
    int accountId;

    public SampleDataAltCopy(String id, String sample, int apiCollectionId, String method, String url, int responseCode,
            int timestamp, int accountId) {
        this.id = id;
        this.sample = sample;
        this.apiCollectionId = apiCollectionId;
        this.method = method;
        this.url = url;
        this.responseCode = responseCode;
        this.timestamp = timestamp;
        this.accountId = accountId;
    }

    public SampleDataAltCopy() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSample() {
        return sample;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }
}
