package com.akto.dto.testing;

import java.util.List;
import java.util.Set;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class SingleTypeInfoView {
    
    private ApiInfoKey id;
    private int discoveredTs;
    private int lastSeenTs;
    private String accessType;
    private List<String> authTypes;
    private List<String> reqSubTypes;
    private List<String> respSubTypes;
    private List<String> combinedData;
    private Set<Integer> logicalGroups;

    public SingleTypeInfoView() {}

    public SingleTypeInfoView(ApiInfoKey apiInfoKey) {
        this.id = apiInfoKey;
    }
    
    public SingleTypeInfoView(ApiInfoKey id, int discoveredTs, int lastSeenTs, String accessType,
            List<String> authTypes, List<String> reqSubTypes, List<String> respSubTypes, List<String> combinedData, 
            Set<Integer> logicalGroups) {
        this.id = id;
        this.discoveredTs = discoveredTs;
        this.lastSeenTs = lastSeenTs;
        this.accessType = accessType;
        this.authTypes = authTypes;
        this.reqSubTypes = reqSubTypes;
        this.respSubTypes = respSubTypes;
        this.combinedData = combinedData;
        this.logicalGroups = logicalGroups;
    }

    public ApiInfoKey getId() {
        return this.id;
    }

    public int getDiscoveredTs() {
        return this.discoveredTs;
    }

    public void setId(ApiInfoKey apiInfoKey) {
        this.id = apiInfoKey;
    }

    public ApiInfoKey getApiInfoKey() {
        return this.id;
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

    public List<String> getAllAuthTypes() {
        return authTypes;
    }

    public void setAllAuthTypes(List<String> authTypes) {
        this.authTypes = authTypes;
    }

    public List<String> getAuthTypes() {
        return authTypes;
    }

    public void setAuthTypes(List<String> authTypes) {
        this.authTypes = authTypes;
    }

    public List<String> getReqSubTypes() {
        return reqSubTypes;
    }

    public void setReqSubTypes(List<String> reqSubTypes) {
        this.reqSubTypes = reqSubTypes;
    }

    public List<String> getRespSubTypes() {
        return respSubTypes;
    }

    public void setRespSubTypes(List<String> respSubTypes) {
        this.respSubTypes = respSubTypes;
    }

    public List<String> getCombinedData() {
        return combinedData;
    }

    public void setCombinedData(List<String> combinedData) {
        this.combinedData = combinedData;
    }

    public Set<Integer> getLogicalGroups() {
        return logicalGroups;
    }

    public void setLogicalGroups(Set<Integer> logicalGroups) {
        this.logicalGroups = logicalGroups;
    }
    
}
