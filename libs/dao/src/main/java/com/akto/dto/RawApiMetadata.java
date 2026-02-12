package com.akto.dto;

public class RawApiMetadata {
    private String countryCode;
    private String destCountryCode;

    public RawApiMetadata() {
    }

    public RawApiMetadata(String countryCode) {
        this.countryCode = countryCode;
    }

    public RawApiMetadata(String countryCode, String destCountryCode) {
        this.countryCode = countryCode;
        this.destCountryCode = destCountryCode;
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getDestCountryCode() {
        return this.destCountryCode;
    }

    public void setDestCountryCode(String destCountryCode) {
        this.destCountryCode = destCountryCode;
    }
}