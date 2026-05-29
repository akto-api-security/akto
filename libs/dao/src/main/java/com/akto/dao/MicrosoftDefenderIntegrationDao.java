package com.akto.dao;

import com.akto.dto.microsoft_defender_integration.MicrosoftDefenderIntegration;

public class MicrosoftDefenderIntegrationDao extends AccountsContextDao<MicrosoftDefenderIntegration> {

    public static final MicrosoftDefenderIntegrationDao instance = new MicrosoftDefenderIntegrationDao();

    @Override
    public String getCollName() {
        return "microsoft_defender_integration";
    }

    @Override
    public Class<MicrosoftDefenderIntegration> getClassT() {
        return MicrosoftDefenderIntegration.class;
    }
}
