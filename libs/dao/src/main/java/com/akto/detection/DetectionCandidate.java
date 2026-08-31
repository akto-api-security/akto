package com.akto.detection;

/**
 * One locally-detected value that is eligible for refinement by an external classifier.
 *
 * Mirrors the "detection" shape used by detection-corrector integrations: an index the caller uses
 * to match the answer back up, the JSON path the value was found at, the value itself, and the data
 * type local detection settled on. Also carries param context (url, method, param, apiCollectionId)
 * for param-level caching and Kafka publishing.
 */
public class DetectionCandidate {

    private int idx;
    private String jsonPath;
    private String value;
    private String type;
    private int apiCollectionId;
    private String url;
    private String method;
    private String param;

    public DetectionCandidate() {}

    public DetectionCandidate(int idx, String jsonPath, String value, String type) {
        this.idx = idx;
        this.jsonPath = jsonPath;
        this.value = value;
        this.type = type;
    }

    public int getIdx() { return idx; }
    public String getJsonPath() { return jsonPath; }
    public String getValue() { return value; }
    public String getType() { return type; }

    public int getApiCollectionId() { return apiCollectionId; }
    public void setApiCollectionId(int apiCollectionId) { this.apiCollectionId = apiCollectionId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }

    @Override
    public String toString() {
        return "{ idx='" + idx + "', jsonPath='" + jsonPath + "', type='" + type + "' }";
    }
}
