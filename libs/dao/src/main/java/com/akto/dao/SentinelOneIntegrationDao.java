package com.akto.dao;

import com.akto.dto.sentinelone_integration.SentinelOneIntegration;

public class SentinelOneIntegrationDao extends AccountsContextDao<SentinelOneIntegration> {

    public static final SentinelOneIntegrationDao instance = new SentinelOneIntegrationDao();

    @Override
    public String getCollName() {
        return "sentinelone_integration";
    }

    @Override
    public Class<SentinelOneIntegration> getClassT() {
        return SentinelOneIntegration.class;
    }
}
