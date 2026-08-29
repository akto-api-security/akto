package com.akto.detection;

/**
 * One locally-detected value that is eligible for refinement by an external classifier.
 *
 * Mirrors the "detection" shape used by detection-corrector integrations: an index the caller uses
 * to match the answer back up, the JSON path the value was found at, the value itself, and the data
 * type local detection settled on.
 */
public class DetectionCandidate {

    private int idx;
    private String jsonPath;
    private String value;
    private String type;

    public DetectionCandidate(int idx, String jsonPath, String value, String type) {
        this.idx = idx;
        this.jsonPath = jsonPath;
        this.value = value;
        this.type = type;
    }

    public int getIdx() {
        return idx;
    }


    public String getJsonPath() {
        return jsonPath;
    }


    public String getValue() {
        return value;
    }


    public String getType() {
        return type;
    }


    @Override
    public String toString() {
        return "{ idx='" + idx + "', jsonPath='" + jsonPath + "', type='" + type + "' }";
    }
}
