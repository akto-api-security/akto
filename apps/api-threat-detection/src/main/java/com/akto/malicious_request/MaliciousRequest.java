package com.akto.malicious_request;

import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MaliciousRequest {
    @JsonProperty("ac")
    private final String actor;

    @JsonProperty("rp")
    private final HttpResponseParams responseParam;

    @JsonProperty("da")
    private final long detectedAt;

    // We can more fields here later. Something like type, reason, etc.

    public MaliciousRequest(String actor, HttpResponseParams responseParam) {
        this.actor = actor;
        this.responseParam = responseParam;
        this.detectedAt = System.currentTimeMillis();
    }

    public String getActor() {
        return actor;
    }

    public HttpResponseParams getResponseParam() {
        return responseParam;
    }

    public long getDetectedAt() {
        return detectedAt;
    }
}
