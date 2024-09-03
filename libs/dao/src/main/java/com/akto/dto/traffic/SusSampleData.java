package com.akto.dto.traffic;

import java.util.List;

import org.bson.types.ObjectId;

import com.akto.dto.type.URLMethods.Method;

public class SusSampleData {

    ObjectId id;
    public final static String SOURCE_IPS = "sourceIPs";
    List<String> sourceIPs;
    public final static String API_COLLECTION_ID = "apiCollectionId";
    int apiCollectionId;
    String url;
    Method method;
    public static final String _SAMPLE = "sample";
    String sample;
    public final static String _DISCOVERED = "discovered";
    int discovered;
    /*
     * we retrospectively match all sus-samples' url
     * with the urls present in the db to match them.
     */
    public final static String MATCHING_URL = "matchingUrl";
    String matchingUrl;

    public SusSampleData() {
    }

    public SusSampleData(List<String> sourceIPs, int apiCollectionId, String url, Method method, String sample,
            int discovered) {
        this.sourceIPs = sourceIPs;
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.sample = sample;
        this.discovered = discovered;
        this.matchingUrl = "";
    }

    public List<String> getSourceIPs() {
        return sourceIPs;
    }

    public void setSourceIPs(List<String> sourceIPs) {
        this.sourceIPs = sourceIPs;
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

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public String getSample() {
        return sample;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }

    public int getDiscovered() {
        return discovered;
    }

    public void setDiscovered(int discovered) {
        this.discovered = discovered;
    }

    public String getMatchingUrl() {
        return matchingUrl;
    }

    public void setMatchingUrl(String matchingUrl) {
        this.matchingUrl = matchingUrl;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
}