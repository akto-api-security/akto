package com.akto.dto;

import java.util.Objects;

public class SensitiveParamInfo {
    private String url;
    private String method;
    private int responseCode;
    private boolean isHeader;
    private String param;
    private boolean sensitive;

    public SensitiveParamInfo(String url, String method, int responseCode, boolean isHeader, String param, boolean sensitive) {
        this.url = url;
        this.method = method;
        this.responseCode = responseCode;
        this.isHeader = isHeader;
        this.param = param;
        this.sensitive = sensitive;
    }

    public SensitiveParamInfo() {

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

    public boolean getIsHeader() {
        return isHeader;
    }

    public void setIsHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }
}
