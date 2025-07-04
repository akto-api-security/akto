package com.akto.action;


import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.List;

import org.bson.conversions.Bson;

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

    private String url ;
    private String method;
    private ApiInfo apiInfo;

    public String fetchApiInfo(){
        ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId), Projections.include(ApiCollection._TYPE));
        Bson filter = ApiInfoDao.getFilter(url, method, apiCollectionId);
        if(collection.getType() != null && collection.getType().equals(ApiCollection.Type.API_GROUP)) {
            filter = Filters.and(
                Filters.in(ApiInfo.COLLECTION_IDS, apiCollectionId),
                Filters.eq(ApiInfo.ID_URL, url),
                Filters.eq(ApiInfo.ID_METHOD, method)   
            );
        }
       
        this.apiInfo = ApiInfoDao.instance.findOne(filter);
        return SUCCESS.toUpperCase();
    }

    public List<ApiInfo> getApiInfoList() {
        return apiInfoList;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public ApiInfo getApiInfo() {
        return apiInfo;
    }

}
