package com.akto.dao;

import com.akto.dto.crowdstrike_integration.CrowdStrikeIntegration;

public class CrowdStrikeIntegrationDao extends AccountsContextDao<CrowdStrikeIntegration> {

    public static final CrowdStrikeIntegrationDao instance = new CrowdStrikeIntegrationDao();

    @Override
    public String getCollName() {
        return "crowdstrike_integration";
    }

    @Override
    public Class<CrowdStrikeIntegration> getClassT() {
        return CrowdStrikeIntegration.class;
    }
}
