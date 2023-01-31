package com.akto.dao;

import com.akto.dto.APIConfig;

public class APIConfigsDao extends CommonContextDao<APIConfig>{
    public static final APIConfigsDao instance = new APIConfigsDao();

    @Override
    public String getCollName() {
        return "api_configs";
    }

    @Override
    public Class<APIConfig> getClassT() {
        return APIConfig.class;
    }
}
