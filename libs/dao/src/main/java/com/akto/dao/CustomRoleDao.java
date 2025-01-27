package com.akto.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.Role;
import com.mongodb.client.model.Filters;

public class CustomRoleDao extends AccountsContextDao<Role> {

    public static final CustomRoleDao instance = new CustomRoleDao();

    private static final Logger logger = LoggerFactory.getLogger(CustomRoleDao.class);

    public Role findRoleByName(String roleName) {
        return instance.findOne(Filters.eq(Role._NAME, roleName));
    }

    @Override
    public String getCollName() {
        return "custom_roles";
    }

    @Override
    public Class<Role> getClassT() {
        return Role.class;
    }

}
