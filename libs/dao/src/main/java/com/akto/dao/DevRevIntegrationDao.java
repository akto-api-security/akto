package com.akto.dao;

import com.akto.dto.devrev_integration.DevRevIntegration;

public class DevRevIntegrationDao extends AccountsContextDao<DevRevIntegration> {

    public static final DevRevIntegrationDao instance = new DevRevIntegrationDao();

    private DevRevIntegrationDao() {}

    @Override
    public String getCollName() {
        return "devrev_integration";
    }

    @Override
    public Class<DevRevIntegration> getClassT() {
        return DevRevIntegration.class;
    }
}