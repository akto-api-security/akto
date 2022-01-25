package com.akto.dao;

import com.akto.dto.ApiToken;
import com.mongodb.client.model.Filters;

public class ApiTokensDao extends CommonContextDao<ApiToken>{

    public static final ApiTokensDao instance = new ApiTokensDao();

    @Override
    public String getCollName() {
        return "api_tokens";
    }

    @Override
    public Class<ApiToken> getClassT() {
        return ApiToken.class;
    }

    public ApiToken findByKeyForPath(String key, String path) {
        return instance.findOne(
                Filters.and(
                        Filters.eq(ApiToken.KEY, key),
                        Filters.in(ApiToken.ACCESS_LIST, path)
                )
        );
    }
}
