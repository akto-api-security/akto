package com.akto.dto.type;


import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class ParamTypeInfo {
    public static final String API_COLLECTION_ID = "apiCollectionId";
    public int apiCollectionId;
    public static final String URL = "url";
    public String url;
    public static final String METHOD = "method";
    public String method;
    public static final String RESPONSE_CODE = "responseCode";
    public int responseCode;
    public static final String IS_HEADER = "isHeader";
    public boolean isHeader;
    public static final String IS_URL_PARAM = "isUrlParam";
    public boolean isUrlParam;
    public static final String PARAM = "param";
    public String param;
    public static final String UNIQUE_COUNT = "uniqueCount";
    public int uniqueCount;
    public static final String PUBLIC_COUNT = "publicCount";
    public int publicCount;

    public ParamTypeInfo() { }

    public ParamTypeInfo(int apiCollectionId, String url, String method, int responseCode, boolean isHeader, boolean isUrlParam, String param) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.responseCode = responseCode;
        this.isHeader = isHeader;
        this.isUrlParam = isUrlParam;
        this.param = param;
        this.uniqueCount = 0;
        this.publicCount = 0;
    }

    public void incUniqueCount(int inc) {
        this.uniqueCount += inc;
    }

    public void incPublicCount(int inc) {
        this.publicCount += inc;
    }

    public void reset() {
        this.publicCount = 0;
        this.uniqueCount = 0;
    }

    public String composeKey() {
        return StringUtils.joinWith("@", apiCollectionId, url, method, responseCode, isHeader, isUrlParam, param);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParamTypeInfo that = (ParamTypeInfo) o;
        return apiCollectionId == that.apiCollectionId && responseCode == that.responseCode && isHeader == that.isHeader
                && isUrlParam == that.isUrlParam && url.equals(that.url) && method.equals(that.method) && param.equals(that.param);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionId, url, method, responseCode, isHeader, isUrlParam, param);
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isHeader() {
        return isHeader;
    }

    public void setHeader(boolean header) {
        isHeader = header;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public int getUniqueCount() {
        return uniqueCount;
    }

    public void setUniqueCount(int uniqueCount) {
        this.uniqueCount = uniqueCount;
    }

    public int getPublicCount() {
        return publicCount;
    }

    public void setPublicCount(int publicCount) {
        this.publicCount = publicCount;
    }

    public boolean isUrlParam() {
        return isUrlParam;
    }

    public void setUrlParam(boolean urlParam) {
        isUrlParam = urlParam;
    }
}
