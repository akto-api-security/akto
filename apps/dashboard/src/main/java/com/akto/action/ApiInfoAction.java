package com.akto.action;


import com.akto.action.observe.InventoryAction;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApiInfoAction extends UserAction {
    @Override
    public String execute() {
        return SUCCESS;
    }

    private List<ApiInfo> apiInfoList;
    private int apiCollectionId;
    public String fetchApiInfoList() {
        apiInfoList= ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", apiCollectionId));
        for (ApiInfo apiInfo: apiInfoList) {
            apiInfo.calculateActualAuth();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchApiInfoListForRecentEndpoints() {
        apiInfoList = new ArrayList<>();
        InventoryAction inventoryAction = new InventoryAction();
        List<SingleTypeInfo> list = inventoryAction.fetchRecentParams(InventoryAction.deltaPeriodValue);
        Set<ApiInfoKey> apiInfoKeys = new HashSet<ApiInfoKey>();
        for (SingleTypeInfo singleTypeInfo: list) {
            apiInfoKeys.add(new ApiInfoKey(singleTypeInfo.getApiCollectionId(),singleTypeInfo.getUrl(), Method.valueOf(singleTypeInfo.getMethod())));
        }

        List<ApiInfo> fromDb = ApiInfoDao.instance.findAll(new BasicDBObject());
        for (ApiInfo a: fromDb) {
            if (apiInfoKeys.contains(a.getId())) {
                a.calculateActualAuth();
                apiInfoList.add(a);
            }
        }


        return SUCCESS.toUpperCase();
    }

    public List<ApiInfo> getApiInfoList() {
        return apiInfoList;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
