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
        Bson filter = ApiInfoDao.getFilter(url, method, apiCollectionId);
        this.apiInfo = ApiInfoDao.instance.findOne(filter);
        if(this.apiInfo == null){
            // case of slash missing in first character of url
            // search for url having no leading slash
            if (url != null && url.startsWith("/")) {
                String urlWithoutLeadingSlash = url.substring(1);
                filter = ApiInfoDao.getFilter(urlWithoutLeadingSlash, method, apiCollectionId);
                this.apiInfo = ApiInfoDao.instance.findOne(filter);   
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
