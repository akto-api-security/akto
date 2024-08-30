package com.akto.dto.traffic;

import java.util.List;

import com.akto.dto.type.URLMethods.Method;

public class SusSampleData {

    List<String> sourceIPs;
    int apiCollectionId;
    String url;
    Method method;
    String sample;
    int discovered;

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

}
