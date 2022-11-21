package com.akto.dao;

import com.akto.dto.AktoDataType;

public class AktoDataTypeDao extends AccountsContextDao<AktoDataType> {
    
    public static final AktoDataTypeDao instance = new AktoDataTypeDao();

    @Override
    public String getCollName() {
        return "akto_data_type";
    }

    @Override
    public Class<AktoDataType> getClassT() {
        return AktoDataType.class;
    }
}