package com.akto.dao;

import com.akto.dto.ApiDependenciesFromSwagger;

public class ApiDependenciesFromSwaggerDao extends AccountsContextDao<ApiDependenciesFromSwagger> {
    public static final ApiDependenciesFromSwaggerDao instance = new ApiDependenciesFromSwaggerDao();

    private ApiDependenciesFromSwaggerDao() {}

    @Override
    public String getCollName() {
        return "api_dependencies_from_discovery";
    }

    @Override
    public Class<ApiDependenciesFromSwagger> getClassT() {
        return ApiDependenciesFromSwagger.class;
    }
}
