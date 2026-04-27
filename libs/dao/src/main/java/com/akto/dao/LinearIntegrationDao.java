package com.akto.dao;

import com.akto.dto.linear_integration.LinearIntegration;

public class LinearIntegrationDao extends AccountsContextDao<LinearIntegration> {
    public static final LinearIntegrationDao instance = new LinearIntegrationDao();

    @Override
    public String getCollName() {
        return "linear_integration";
    }

    @Override
    public Class<LinearIntegration> getClassT() {
        return LinearIntegration.class;
    }
}
