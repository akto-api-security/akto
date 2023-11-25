package com.akto.dto.CollectionConditions;

import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dto.ApiCollectionUsers.CollectionType;
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
        super(Type.API_LIST, Operator.OR);
    }

    public ApiListCondition(Operator operator) {
        super(Type.API_LIST, operator);
    }

    public ApiListCondition(Set<ApiInfoKey> apiList, Operator operator) {
        super(Type.API_LIST, operator);
        this.apiList = apiList;
    }

    @Override
    public Set<ApiInfoKey> returnApis() {
        return apiList;
    }

    @Override
    public Map<CollectionType, Bson> returnFiltersMap(){
        return CollectionCondition.createFiltersMapWithApiList(returnApis());
    }

}
