package com.akto.dao;

import com.akto.dto.CustomDataType;

public class CustomDataTypeDao extends AccountsContextDao<CustomDataType>{

    public static final CustomDataTypeDao instance = new CustomDataTypeDao();

    @Override
    public String getCollName() {
        return "custom_data_type";
    }

    @Override
    public Class<CustomDataType> getClassT() {
        return CustomDataType.class;
    }
}
