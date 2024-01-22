package com.akto.dto.dependency_flow;

import java.util.*;

public class ReplaceDetail {
    int apiCollectionId;
    public static final String _API_COLLECTION_ID = "apiCollectionId";
    String url;
    public static final String _URL = "url";
    String method;
    public static final String _METHOD = "method";
    List<KVPair> kvPairs;
    public static final String _KV_PAIRS = "kvPairs";

    public ReplaceDetail() {
    }

    public ReplaceDetail(int apiCollectionId, String url, String method, List<KVPair> kvPairs) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.kvPairs = kvPairs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplaceDetail that = (ReplaceDetail) o;
        return apiCollectionId == that.apiCollectionId && url.equals(that.url) && method.equals(that.method);
    }

    public void addIfNotExist(List<KVPair> kvPairs) {
        for (KVPair kvPair: kvPairs) addIfNotExist(kvPair);
    }

    public void addIfNotExist(KVPair kvPair) {
        if (this.kvPairs == null) this.kvPairs = new ArrayList<>();
        boolean found = false;
        for (KVPair thisKVPair: this.kvPairs) {
            if (thisKVPair.equals(kvPair)) {
                found = true;
                break;
            }
        }

        if (!found) this.kvPairs.add(kvPair);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionId, url, method);
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

    public List<KVPair> getKvPairs() {
        return kvPairs;
    }

    public void setKvPairs(List<KVPair> kvPairs) {
        this.kvPairs = kvPairs;
    }
}

