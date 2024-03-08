package com.akto.dto;

import java.util.Map;

public class PercentageMatchRequest {

    private OriginalHttpResponse response;
    private Map<String, Boolean> excludedKeys;

    public PercentageMatchRequest(OriginalHttpResponse response, Map<String, Boolean> excludedKeys) {
        this.response = response;
        this.excludedKeys = excludedKeys;
    }

    public PercentageMatchRequest() { }

    public OriginalHttpResponse getResponse() {
        return response;
    }

    public void setResponse(OriginalHttpResponse response) {
        this.response = response;
    }

    public Map<String, Boolean> getExcludedKeys() {
        return excludedKeys;
    }

    public void setExcludedKeys(Map<String, Boolean> excludedKeys) {
        this.excludedKeys = excludedKeys;
    }
    
}
