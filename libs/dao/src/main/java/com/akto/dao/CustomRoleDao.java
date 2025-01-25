package com.akto.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.Role;

public class CustomRoleDao extends AccountsContextDao<Role> {

    public static final CustomRoleDao instance = new CustomRoleDao();

    private static final Logger logger = LoggerFactory.getLogger(CustomRoleDao.class);

    @Override
    public String getCollName() {
        return "custom_roles";
    }

    @Override
    public Class<Role> getClassT() {
        return Role.class;
    }

}
