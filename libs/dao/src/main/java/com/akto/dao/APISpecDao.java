package com.akto.dao;

import com.akto.dto.APISpec;

public class APISpecDao extends AccountsContextDao<APISpec> {

    public static final APISpecDao instance = new APISpecDao();

    private APISpecDao() {}

    @Override
    public String getCollName() {
        return "apispec";
    }

    @Override
    public Class<APISpec> getClassT() {
        return APISpec.class;
    }
    
    public APISpec findById(int id) {
        return this.findOne("apiCollectionId", id);
    }

}
