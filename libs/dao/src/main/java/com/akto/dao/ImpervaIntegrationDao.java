package com.akto.dao;

import com.akto.dto.imperva_integration.ImpervaIntegration;

public class ImpervaIntegrationDao extends AccountsContextDao<ImpervaIntegration> {

    public static final ImpervaIntegrationDao instance = new ImpervaIntegrationDao();

    private ImpervaIntegrationDao() {}

    @Override
    public String getCollName() {
        return "imperva_integration";
    }

    @Override
    public Class<ImpervaIntegration> getClassT() {
        return ImpervaIntegration.class;
    }
}
