package com.akto.dao;

import com.akto.dto.CustomAuthType;

public class CustomAuthTypeDao extends AccountsContextDao<CustomAuthType>{

    public static final CustomAuthTypeDao instance = new CustomAuthTypeDao();

    @Override
    public String getCollName() {
        return "custom_auth_type";
    }

    @Override
    public Class<CustomAuthType> getClassT() {
        return CustomAuthType.class;
    }
}