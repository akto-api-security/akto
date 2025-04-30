package com.akto.dao;

import com.akto.dto.Config.ConfigType;
import com.akto.dto.sso.SAMLConfig;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;

public class SSOConfigsDao extends CommonContextDao<SAMLConfig> {

    public static final SSOConfigsDao instance = new SSOConfigsDao();
    public SAMLConfig getSSOConfig(String userEmail){
        if (userEmail.trim().isEmpty()) {
            return null;
        }
        String[] companyKeyArr = userEmail.split("@");
        if(companyKeyArr == null || companyKeyArr.length < 2){
            return null;
        }

        String domain = companyKeyArr[1];
        return getSSOConfigByDomain(domain);
    }

    public SAMLConfig getSSOConfigByDomain(String domain) {
        SAMLConfig config = SSOConfigsDao.instance.findOne(
                Filters.eq(SAMLConfig.ORGANIZATION_DOMAIN, domain)
        );
        return config;
    }

    public static SAMLConfig getSAMLConfigByAccountId(int accountId) {
        return SSOConfigsDao.instance.findOne(
                Filters.and(
                    Filters.eq(Constants.ID, String.valueOf(accountId))
                )
        );
    }

    public static SAMLConfig getSAMLConfigByAccountId(int accountId, ConfigType configType) {
        return SSOConfigsDao.instance.findOne(
                Filters.and(
                    Filters.eq(Constants.ID, String.valueOf(accountId)),
                    Filters.eq("configType", configType.name())
                )
        );
    }

    @Override
    public String getCollName() {
        return "sso_configs";
    }

    @Override
    public Class<SAMLConfig> getClassT() {
        return SAMLConfig.class;
    }
}
