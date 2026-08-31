package com.akto.detection;

/**
 * A verdict from the async detection-corrector service: the refined data type for a param.
 * Keyed by (collectionId, url, method, param) — all future values on that param use this type.
 */
public class DetectionVerdict {
    private int apiCollectionId;
    private String url;
    private String method;
    private String param;
    private String correctedType;
    private long timestamp;

    public DetectionVerdict() {}

    public DetectionVerdict(int apiCollectionId, String url, String method, String param,
                           String correctedType, long timestamp) {
        this.apiCollectionId = apiCollectionId;
        this.url = url;
        this.method = method;
        this.param = param;
        this.correctedType = correctedType;
        this.timestamp = timestamp;
    }

    public int getApiCollectionId() { return apiCollectionId; }
    public void setApiCollectionId(int apiCollectionId) { this.apiCollectionId = apiCollectionId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }

    public String getCorrectedType() { return correctedType; }
    public void setCorrectedType(String correctedType) { this.correctedType = correctedType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
