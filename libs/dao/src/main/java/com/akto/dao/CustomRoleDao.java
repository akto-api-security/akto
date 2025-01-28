package com.akto.dao;

import com.akto.dto.CustomRole;
import com.mongodb.client.model.Filters;

public class CustomRoleDao extends AccountsContextDao<CustomRole> {

    public static final CustomRoleDao instance = new CustomRoleDao();

    public CustomRole findRoleByName(String roleName) {
        return instance.findOne(Filters.eq(CustomRole._NAME, roleName));
    }

    @Override
    public String getCollName() {
        return "custom_roles";
    }

    @Override
    public Class<CustomRole> getClassT() {
        return CustomRole.class;
    }

}
