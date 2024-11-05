package com.akto.malicious_request;

import com.akto.dto.HttpResponseParams;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MaliciousRequest {
    @JsonProperty("ac")
    private final String actor;

    @JsonProperty("rp")
    private final HttpResponseParams responseParam;

    // We can more fields here later. Something like type, reason, etc.

    public MaliciousRequest(String actor, HttpResponseParams responseParam) {
        this.actor = actor;
        this.responseParam = responseParam;
    }

    public String getActor() {
        return actor;
    }

    public HttpResponseParams getResponseParam() {
        return responseParam;
    }

}
