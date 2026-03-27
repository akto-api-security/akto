package com.akto.dao;

import com.akto.dto.AccountConfig;
import com.mongodb.client.model.Filters;

public class AccountConfigDao extends CommonContextDao<AccountConfig> {

    public static final AccountConfigDao instance = new AccountConfigDao();

    @Override
    public String getCollName() {
        return "account_config";
    }

    @Override
    public Class<AccountConfig> getClassT() {
        return AccountConfig.class;
    }

    public AccountConfig findByOrgId(String orgId) {
        return findOne(Filters.eq(AccountConfig.ID, orgId));
    }
}
