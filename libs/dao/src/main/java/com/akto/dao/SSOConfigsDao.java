package com.akto.dao;

import com.akto.dto.Config;

public class SSOConfigsDao extends CommonContextDao<Config> {

    public static final SSOConfigsDao instance = new SSOConfigsDao();

    @Override
    public String getCollName() {
        return "sso_configs";
    }

    @Override
    public Class<Config> getClassT() {
        return Config.class;
    }
}
