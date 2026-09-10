package com.akto.detection;

/**
 * One locally-detected value handed to the external classifier as an example of what a parameter
 * carries.
 *
 * Mirrors the "detection" shape used by detection-corrector integrations: an index the caller uses
 * to match the answer back up, the JSON path the value was found at, the value itself, and the data
 * type local detection settled on.
 *
 * The index is only meaningful inside a single call. A candidate sitting on the queue has no useful
 * index, because it will be batched with candidates from unrelated parameters; the worker assigns
 * indexes when it builds the request. What survives the wait is the {@link ParamLocation}, which is
 * what a late answer gets filed against.
 */
public class DetectionCandidate {

    private final int idx;
    private final String jsonPath;
    private final String value;
    private final String type;
    private final ParamLocation location;

    /** For queueing, where the index has no meaning yet. */
    public DetectionCandidate(ParamLocation location, String jsonPath, String value, String type) {
        this(0, jsonPath, value, type, location);
    }

    /** For building a classifier request, where the index identifies the answer. */
    public DetectionCandidate(int idx, String jsonPath, String value, String type, ParamLocation location) {
        this.idx = idx;
        this.jsonPath = jsonPath;
        this.value = value;
        this.type = type;
        this.location = location;
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

    public ParamLocation getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "{ idx='" + idx + "', jsonPath='" + jsonPath + "', type='" + type + "' }";
    }
}
