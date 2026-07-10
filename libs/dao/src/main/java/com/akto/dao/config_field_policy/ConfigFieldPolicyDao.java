package com.akto.dao.config_field_policy;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.config_field_policy.ConfigFieldPolicy;

public class ConfigFieldPolicyDao extends AccountsContextDao<ConfigFieldPolicy> {

    public static ConfigFieldPolicyDao instance = new ConfigFieldPolicyDao();

    @Override
    public String getCollName() {
        return ConfigFieldPolicy.COLLECTION_NAME;
    }

    @Override
    public Class<ConfigFieldPolicy> getClassT() {
        return ConfigFieldPolicy.class;
    }
}
