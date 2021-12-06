package com.akto.dao;

import com.akto.dto.auth.APIAuth;

public class APIAuthDao extends AccountsContextDao<APIAuth> {

    public static final APIAuthDao instance = new APIAuthDao();

    private APIAuthDao() {}

    @Override
    public String getCollName() {
        return "api_auth";
    }

    @Override
    public Class<APIAuth> getClassT() {
        return APIAuth.class;
    }
    
}
