package com.akto.dto;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class IgnoredEndpoint {
    
    private ApiInfoKey apiInfoKey;

    public IgnoredEndpoint() {
    }

    public IgnoredEndpoint(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

}
