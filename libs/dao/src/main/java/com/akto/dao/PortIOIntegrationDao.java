package com.akto.dao;

import com.akto.dto.port_io_integration.PortIOIntegration;

public class PortIOIntegrationDao extends AccountsContextDao<PortIOIntegration> {

    public static final PortIOIntegrationDao instance = new PortIOIntegrationDao();

    private PortIOIntegrationDao() {}

    @Override
    public String getCollName() {
        return "port_io_integration";
    }

    @Override
    public Class<PortIOIntegration> getClassT() {
        return PortIOIntegration.class;
    }
}
