package com.akto.dto;

import java.util.List;

public class SensitiveInfoInApiCollections {
    
    private int apiCollectionId;

    private int sensitiveUrlsInRequest;
    private List<String> sensitiveSubtypesInRequest;
    
    private int sensitiveUrlsInResponse;
    private List<String> sensitiveSubtypesInResponse;

    public SensitiveInfoInApiCollections(int apiCollectionId, int sensitiveUrlsInRequest, List<String> sensitiveSubtypesInRequest, int sensitiveUrlsInResponse, List<String> sensitiveSubtypesInResponse){
        apiCollectionId = this.apiCollectionId;
        sensitiveUrlsInRequest = this.sensitiveUrlsInRequest;
        sensitiveSubtypesInRequest = this.sensitiveSubtypesInRequest ;
        sensitiveUrlsInResponse = this.sensitiveUrlsInResponse;
        sensitiveSubtypesInResponse = this.sensitiveSubtypesInResponse;
    }

    public int getSensitiveUrlsInResponse() {
        return sensitiveUrlsInResponse;
    }
    public void setSensitiveUrlsInResponse(int sensitiveUrlsInResponse) {
        this.sensitiveUrlsInResponse = sensitiveUrlsInResponse;
    }

    public List<String> getSensitiveSubtypesInResponse() {
        return sensitiveSubtypesInResponse;
    }
    public void setSensitiveSubtypesInResponse(List<String> sensitiveSubtypesInResponse) {
        this.sensitiveSubtypesInResponse = sensitiveSubtypesInResponse;
    }
    public int getApiCollectionId() {
        return apiCollectionId;
    }
    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
    public int getSensitiveUrlsInRequest() {
        return sensitiveUrlsInRequest;
    }
    public void setSensitiveUrlsInRequest(int sensitiveUrlsInRequest) {
        this.sensitiveUrlsInRequest = sensitiveUrlsInRequest;
    }
    public List<String> getSensitiveSubtypesInRequest() {
        return sensitiveSubtypesInRequest;
    }
    public void setSensitiveSubtypesInRequest(List<String> sensitiveSubtypesInRequest) {
        this.sensitiveSubtypesInRequest = sensitiveSubtypesInRequest;
    }
    
}
