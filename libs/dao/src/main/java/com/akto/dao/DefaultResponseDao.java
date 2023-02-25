package com.akto.dao;

import com.akto.dto.DefaultResponse;

public class DefaultResponseDao extends AccountsContextDao<DefaultResponse> {
    public static DefaultResponseDao instance = new DefaultResponseDao();
    @Override
    public String getCollName() {
        return "default_response";
    }

    @Override
    public Class<DefaultResponse> getClassT() {
        return DefaultResponse.class;
    }
}