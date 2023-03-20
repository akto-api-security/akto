package com.akto.dto.testing;

import java.util.List;

public class EndpointDataResponse {
    
    private String url;
    private String method;
    private int apiCollectionId;
    private int discoveredTs;
    private int lastSeenTs;
    private String accessType;
    private List<String> authTypes;
    private List<String> sensitiveParams;

    public EndpointDataResponse() {}
    
    public EndpointDataResponse(String url, String method, int apiCollectionId, int discoveredTs, int lastSeenTs, String accessType, List<String> authTypes, List<String> sensitiveParams) {
        this.url = url;
        this.method = method;
        this.apiCollectionId = apiCollectionId;
        this.discoveredTs = discoveredTs;
        this.lastSeenTs = lastSeenTs;
        this.accessType = accessType;
        this.authTypes = authTypes;
        this.sensitiveParams = sensitiveParams;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getApiCollectionID() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }


    public int getDiscoveredTs() {
        return this.discoveredTs;
    }

    public void setDiscoveredTs(int discoveredTs) {
        this.discoveredTs = discoveredTs;
    }

    public int getLastSeenTs() {
        return lastSeenTs;
    }

    public void setLastSeenTs(int lastSeenTs) {
        this.lastSeenTs = lastSeenTs;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public List<String> getAuthTypes() {
        return authTypes;
    }

    public void setAuthTypes(List<String> authTypes) {
        this.authTypes = authTypes;
    }

    public List<String> getSensitiveParams() {
        return sensitiveParams;
    }

    public void setSensitiveParams(List<String> sensitiveParams) {
        this.sensitiveParams = sensitiveParams;
    }

}
