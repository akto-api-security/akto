package com.akto.dto;

public class SensitiveParamInfo {
    private String url;
    private String method;
    private int responseCode;
    private boolean isHeader;
    private String param;
    private int apiCollectionId;
    private boolean sensitive;


    public SensitiveParamInfo() {
    }

    public SensitiveParamInfo(String url, String method, int responseCode, boolean isHeader, String param, int apiCollectionId, boolean sensitive) {
        this.url = url;
        this.method = method;
        this.responseCode = responseCode;
        this.isHeader = isHeader;
        this.param = param;
        this.apiCollectionId = apiCollectionId;
        this.sensitive = sensitive;
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

    public int getResponseCode() {
        return this.responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isIsHeader() {
        return this.isHeader;
    }

    public boolean getIsHeader() {
        return this.isHeader;
    }

    public void setIsHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public String getParam() {
        return this.param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public boolean isSensitive() {
        return this.sensitive;
    }

    public boolean getSensitive() {
        return this.sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    @Override
    public String toString() {
        return "{" +
            " url='" + getUrl() + "'" +
            ", method='" + getMethod() + "'" +
            ", responseCode='" + getResponseCode() + "'" +
            ", isHeader='" + isIsHeader() + "'" +
            ", param='" + getParam() + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            ", sensitive='" + isSensitive() + "'" +
            "}";
    }

}
