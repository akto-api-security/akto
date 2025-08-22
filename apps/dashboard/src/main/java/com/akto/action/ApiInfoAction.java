package com.akto.action;
import com.akto.DaoInit;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.util.Constants;
import com.google.protobuf.Api;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
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
    @Setter
    private boolean showApiInfo;

    @Getter
    private int unauthenticatedApis;
    @Getter
    private List<ApiInfo> unauthenticatedApiList;

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
    public String fetchAllUnauthenticatedApis(){
        Bson filter = Document.parse(
            "{ \"allAuthTypesFound\": { \"$elemMatch\": { \"$elemMatch\": { \"$eq\": \"UNAUTHENTICATED\" } } } }"
        );
        if(!showApiInfo){
            this.unauthenticatedApis = (int) ApiInfoDao.instance.count(filter);
        }else{
            int count = 0;
            int skip = 0;
            int limit = 1000;
            Bson sort = Sorts.ascending(Constants.ID);
            while(true){
                List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(filter, skip, limit, sort);
                if(apiInfos.isEmpty()) {
                    break;
                }
                for(ApiInfo apiInfo: apiInfos) {
                    apiInfo.calculateActualAuth();
                }
                if(unauthenticatedApiList == null) {
                    unauthenticatedApiList = apiInfos;
                } else {
                    unauthenticatedApiList.addAll(apiInfos);
                }
                count += apiInfos.size();
                skip += limit;
            }
            this.unauthenticatedApis = count;
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


