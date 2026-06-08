package com.akto.dao;

import com.akto.dto.wiz_integration.WizIntegration;

public class WizIntegrationDao extends AccountsContextDao<WizIntegration> {

    public static final WizIntegrationDao instance = new WizIntegrationDao();

    private WizIntegrationDao() {}

    @Override
    public String getCollName() {
        return "wiz_integration";
    }

    @Override
    public Class<WizIntegration> getClassT() {
        return WizIntegration.class;
    }
}
