package com.akto.dto.CollectionConditions;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    public List<ApiInfoKey> returnApis() {
        return new ArrayList<>(apiList);
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return apiList.contains(key);
    }

    public static void updateApiListCondition(ApiListCondition apiListCondition, List<ApiInfoKey> list, boolean isAddOperation) {
        Set<ApiInfoKey> tmp = new HashSet<>(apiListCondition.returnApis());

        if (isAddOperation) {
            tmp.addAll(list);
        } else {
            tmp.removeAll(list);
        }

        apiListCondition.setApiList(tmp);
    }

}
