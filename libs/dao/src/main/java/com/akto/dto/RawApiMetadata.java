package com.akto.dto;

public class RawApiMetadata {
    private String countryCode;

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
}