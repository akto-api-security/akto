package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

    @Override
    public String getCollName() {
        return "api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public static Bson getFilter(ApiInfo.ApiInfoKey apiInfoKey) {
        return getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), apiInfoKey.getApiCollectionId());
    }

    public static Bson getFilter(String url, String method, int apiCollectionId) {
        return Filters.and(
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method),
                Filters.eq("_id.apiCollectionId", apiCollectionId)
        );
    }

}
