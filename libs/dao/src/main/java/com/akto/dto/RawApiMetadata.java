package com.akto.dto;

public class RawApiMetadata {
    private String countryCode;
    private int apiCollectionId;

    public RawApiMetadata() {
    }

    public RawApiMetadata(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public RawApiMetadata(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}