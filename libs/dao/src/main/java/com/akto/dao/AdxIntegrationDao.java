package com.akto.dao;

import com.akto.dto.adx_integration.AdxIntegration;

public class AdxIntegrationDao extends AccountsContextDao<AdxIntegration> {

    public static final AdxIntegrationDao instance = new AdxIntegrationDao();

    @Override
    public String getCollName() {
        return "adx_integration";
    }

    @Override
    public Class<AdxIntegration> getClassT() {
        return AdxIntegration.class;
    }
}

