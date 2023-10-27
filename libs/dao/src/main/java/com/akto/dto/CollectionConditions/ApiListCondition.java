package com.akto.dto.CollectionConditions;

import java.util.Set;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class ApiListCondition extends CollectionCondition{

    Set<ApiInfoKey> apiList;

    public Set<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(Set<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    public ApiListCondition() {
        super(Type.API_LIST);
    }

    public ApiListCondition(Set<ApiInfoKey> apiList) {
        super(Type.API_LIST);
        this.apiList = apiList;
    }

    @Override
    public Set<ApiInfoKey> returnApis() {
        return apiList;
    }
    
}
