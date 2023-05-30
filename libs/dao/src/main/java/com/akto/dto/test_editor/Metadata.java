package com.akto.dto.test_editor;

public class Metadata {

    private String minAktoVersion;

    private String minOnpremVersion;

    private Boolean isActive;

    public Metadata(String minAktoVersion, String minOnpremVersion, Boolean isActive) {
        this.minAktoVersion = minAktoVersion;
        this.minOnpremVersion = minOnpremVersion;
        this.isActive = isActive;
    }

    public Metadata() {
    }

    public String getMinAktoVersion() {
        return minAktoVersion;
    }

    public void setMinAktoVersion(String minAktoVersion) {
        this.minAktoVersion = minAktoVersion;
    }

    public String getMinOnpremVersion() {
        return minOnpremVersion;
    }

    public void setMinOnpremVersion(String minOnpremVersion) {
        this.minOnpremVersion = minOnpremVersion;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
