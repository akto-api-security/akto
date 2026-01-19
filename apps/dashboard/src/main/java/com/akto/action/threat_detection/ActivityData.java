package com.akto.action.threat_detection;

import lombok.Getter;
import lombok.Setter;

public class ActivityData {
    
    private String url;
    private String severity;
    private String subCategory;
    private long detectedAt;
    private String method;
    private String host;

    @Getter
    @Setter
    private String metadata;

    public ActivityData(String url, String severity, String subCategory, long detectedAt, String method, String host, String metadata) {
        this.url = url;
        this.severity = severity;
        this.subCategory = subCategory;
        this.detectedAt = detectedAt;
        this.method = method;
        this.host = host;
        this.metadata = metadata;
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getSeverity() {
        return severity;
    }
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    public String getSubCategory() {
        return subCategory;
    }
    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }
    public long getDetectedAt() {
        return detectedAt;
    }
    public void setDetectedAt(long detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
