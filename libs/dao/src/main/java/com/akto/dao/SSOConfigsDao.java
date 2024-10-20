package com.akto.dao;

import com.akto.dto.sso.SAMLConfig;
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
        SAMLConfig config = SSOConfigsDao.instance.findOne(
            Filters.eq(SAMLConfig.ORGANIZATION_DOMAIN, domain)
        );
        return config;
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
