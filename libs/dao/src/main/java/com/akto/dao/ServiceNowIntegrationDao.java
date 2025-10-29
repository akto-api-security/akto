package com.akto.dao;

import com.akto.dto.servicenow_integration.ServiceNowIntegration;

public class ServiceNowIntegrationDao extends AccountsContextDao<ServiceNowIntegration> {

    public static final ServiceNowIntegrationDao instance = new ServiceNowIntegrationDao();

    @Override
    public String getCollName() {
        return "servicenow_integration";
    }

    @Override
    public Class<ServiceNowIntegration> getClassT() {
        return ServiceNowIntegration.class;
    }
}
