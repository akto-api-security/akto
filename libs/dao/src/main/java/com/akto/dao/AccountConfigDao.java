package com.akto.dao;

import com.akto.dto.AccountConfig;
import com.mongodb.client.model.Filters;
import java.util.List;

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

    public List<AccountConfig> findByOrgId(String orgId) {
        return findAll(Filters.eq(AccountConfig.ORG_ID, orgId));
    }

    public AccountConfig findByAwsAccountId(String awsAccountId) {
        return findOne(Filters.eq(AccountConfig.AWS_ACCOUNT_ID, awsAccountId));
    }
}
