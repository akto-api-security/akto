package com.akto.dto;

import org.apache.commons.lang3.StringUtils;

public class ApiCollectionTestStatus {

    private int apiCollectionId;
    private int lastTestedAt;
    private String state;


    public ApiCollectionTestStatus(int apiCollectionId, int lastTestedAt, String state) {
        this.lastTestedAt = lastTestedAt;
        this.state = state != null ? StringUtils.capitalize(state.toLowerCase()): null;
        this.apiCollectionId = apiCollectionId;
    }

    public int getLastTestedAt() {
        return lastTestedAt;
    }

    public void setLastTestedAt(int lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
