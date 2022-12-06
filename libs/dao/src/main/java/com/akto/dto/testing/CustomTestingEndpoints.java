package com.akto.dto.testing;

import com.akto.dto.ApiInfo;

import java.util.List;

public class CustomTestingEndpoints extends TestingEndpoints {

    private List<ApiInfo.ApiInfoKey> apisList;

    public CustomTestingEndpoints() {
        super(Type.CUSTOM);
    }

    public CustomTestingEndpoints(List<ApiInfo.ApiInfoKey> apisList) {
        super(Type.CUSTOM);
        this.apisList = apisList;
    }
    public List<ApiInfo.ApiInfoKey> getApisList() {
        return apisList;
    }

    public void setApisList(List<ApiInfo.ApiInfoKey> apisList) {
        this.apisList = apisList;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        return this.getApisList();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return false;
    }
}
