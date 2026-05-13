package com.akto.dao;

import com.akto.dto.new_relic_integration.NewRelicIntegration;

public class NewRelicIntegrationDao extends AccountsContextDao<NewRelicIntegration> {

    public static final NewRelicIntegrationDao instance = new NewRelicIntegrationDao();

    private NewRelicIntegrationDao() {}

    @Override
    public String getCollName() {
        return "new_relic_integration";
    }

    @Override
    public Class<NewRelicIntegration> getClassT() {
        return NewRelicIntegration.class;
    }
}