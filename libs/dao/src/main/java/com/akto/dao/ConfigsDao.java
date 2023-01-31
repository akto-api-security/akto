package com.akto.dao;

import com.akto.dto.Config;

public class ConfigsDao extends CommonContextDao<Config> {

    public static final ConfigsDao instance = new ConfigsDao();

    @Override
    public String getCollName() {
        return "configs";
    }

    @Override
    public Class<Config> getClassT() {
        return Config.class;
    }
}
