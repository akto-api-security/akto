package com.akto.action;

import java.util.List;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.type.SingleTypeInfo;
import com.opensymphony.xwork2.Action;

public class APICatalogAction {

    List<SingleTypeInfo> apiCatalogData;

    public String getAPICatalog() {

        try {
            apiCatalogData = SingleTypeInfoDao.instance.fetchAll();
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
    } 

    public List<SingleTypeInfo> getApiCatalogData() {
        return this.apiCatalogData;
    }

    public void setApiCatalogData(List<SingleTypeInfo> apiCatalogData) {
        this.apiCatalogData = apiCatalogData;
    }
}
