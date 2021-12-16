package com.akto.dao;


import com.akto.dto.RBAC;

public class RBACDao extends CommonContextDao<RBAC> {
    public static final RBACDao instance = new RBACDao();

    @Override
    public String getCollName() {
        return "rbac";
    }

    @Override
    public Class<RBAC> getClassT() {
        return RBAC.class;
    }
}
