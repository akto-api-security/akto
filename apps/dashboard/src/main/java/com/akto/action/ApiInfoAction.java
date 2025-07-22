package com.akto.action;


import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import com.akto.dto.type.URLMethods.Method;

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

    private String type;
    private int lowerLimitValue;
    private int higherLimitValue;
    private String fieldName;
    private List<ApiInfo> apiInfos;

    public String fetchApiInfosWithCustomFilter() {
        switch (type) {
            case "RISK_SCORE":
                handleRiskScoreFilter();
                break;
            case "SENSITIVE":
                handleSensitiveFilter();
                break;
            case "AUTH_TYPES":
                handleAuthTypesFilter();
                break;
            case "THIRD_PARTY":
                handleThirdPartyFilter();
                break;
            default:
                addActionError("Invalid filter type: " + type);
                return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    

    private void handleRiskScoreFilter() {
        Bson filter = Filters.gt(fieldName, lowerLimitValue);
        apiInfos = ApiInfoDao.instance.findAll(filter);
    }

    private void handleSensitiveFilter() {
        Bson filter = SingleTypeInfoDao.instance.filterForSensitiveParamsExcludingUserMarkedSensitive(
            (apiCollectionId != 0) ? apiCollectionId : null, null, null, null
        );
        List<SingleTypeInfo> sensitiveSTIs = SingleTypeInfoDao.instance.findAll(filter);
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<ApiInfo> result = new ArrayList<>();
        for (SingleTypeInfo sti : sensitiveSTIs) {
            int collectionId = sti.getApiCollectionId();
            String url = sti.getUrl();
            String method = sti.getMethod();
            String key = collectionId + "|" + url + "|" + method;
            if (seen.contains(key)) continue;
            seen.add(key);
            ApiInfo apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(url, method, collectionId));
            if (apiInfo != null) {
                result.add(apiInfo);
            } else {
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(collectionId, url, Method.fromString(method));
                ApiInfo minimalApiInfo = new ApiInfo();
                minimalApiInfo.setId(apiInfoKey);
                result.add(minimalApiInfo);
            }
        }
        apiInfos = result;
    }

    private void handleAuthTypesFilter() {
        List<ApiInfo> allApis = ApiInfoDao.instance.findAll(new BsonDocument());
        List<ApiInfo> unauthenticatedApis = new ArrayList<>();
        
        for (ApiInfo apiInfo : allApis) {
            apiInfo.calculateActualAuth();
            List<ApiInfo.AuthType> actualAuthTypes = apiInfo.getActualAuthType();
            if (actualAuthTypes != null && !actualAuthTypes.isEmpty() && actualAuthTypes.get(0) == ApiInfo.AuthType.UNAUTHENTICATED) {
                unauthenticatedApis.add(apiInfo);
            }
        }
        apiInfos = unauthenticatedApis;
    }

    private void handleThirdPartyFilter() {
        int sevenDaysAgo = (int) (System.currentTimeMillis() / 1000) - 604800; // 7 days in seconds
        Bson filter = Filters.and(
            Filters.gte(fieldName, sevenDaysAgo),
            Filters.in("apiAccessTypes", "THIRD_PARTY")
        );
        apiInfos = ApiInfoDao.instance.findAll(filter);
    }

    public List<ApiInfo> getApiInfos() {
        return apiInfos;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setLowerLimitValue(int lowerLimitValue) {
        this.lowerLimitValue = lowerLimitValue;
    }

    public void setHigherLimitValue(int higherLimitValue) {
        this.higherLimitValue = higherLimitValue;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
}


