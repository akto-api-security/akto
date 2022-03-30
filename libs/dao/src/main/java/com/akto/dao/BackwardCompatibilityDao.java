package com.akto.dao;

import com.akto.dto.BackwardCompatibility;

public class BackwardCompatibilityDao extends AccountsContextDao<BackwardCompatibility>{

    public static final BackwardCompatibilityDao instance = new BackwardCompatibilityDao();
    @Override
    public String getCollName() {
        return "backward_compatibility";
    }

    @Override
    public Class<BackwardCompatibility> getClassT() {
        return BackwardCompatibility.class;
    }
}
