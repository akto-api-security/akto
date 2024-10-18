package com.akto.dao;

import com.akto.dto.sso.SAMLConfig;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class SSOConfigsDao extends CommonContextDao<SAMLConfig> {

    public static final SSOConfigsDao instance = new SSOConfigsDao();
    public int getSSOConfigId(String userEmail){
        if (userEmail.trim().isEmpty()) {
            return -1;
        }
        String[] companyKeyArr = userEmail.split("@");
        if(companyKeyArr == null || companyKeyArr.length < 2){
            return -1;
        }

        String domain = companyKeyArr[1];
        SAMLConfig config = SSOConfigsDao.instance.findOne(
            Filters.eq(SAMLConfig.ORGANIZATION_DOMAIN, domain), Projections.include(Constants.ID)
        );
        if(config == null){
            return -1;
        }

        return Integer.parseInt(config.getId());
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
