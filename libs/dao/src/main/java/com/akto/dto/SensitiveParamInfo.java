package com.akto.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SensitiveParamInfo {
    private String url;
    private String method;
    private int responseCode;
    private boolean isHeader;
    private String param;
    private int apiCollectionId;
    public static final String SENSITIVE = "sensitive";
    private boolean sensitive;
    public static final String SAMPLE_DATA_SAVED = "sampleDataSaved";
    private boolean sampleDataSaved;
    List<Integer> collectionIds;

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
        this.sampleDataSaved = false;
        this.collectionIds = Arrays.asList(apiCollectionId);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensitiveParamInfo that = (SensitiveParamInfo) o;
        return responseCode == that.responseCode && isHeader == that.isHeader && apiCollectionId == that.apiCollectionId && url.equals(that.url) && method.equals(that.method) && param.equals(that.param);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, responseCode, isHeader, param, apiCollectionId);
    }

    public boolean isSampleDataSaved() {
        return sampleDataSaved;
    }

    public void setSampleDataSaved(boolean sampleDataSaved) {
        this.sampleDataSaved = sampleDataSaved;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }

}
