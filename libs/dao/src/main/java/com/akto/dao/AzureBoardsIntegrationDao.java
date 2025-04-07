package com.akto.dao;

import com.akto.dto.azure_boards_integration.AzureBoardsIntegration;

public class AzureBoardsIntegrationDao extends AccountsContextDao<AzureBoardsIntegration> {

    public static final AzureBoardsIntegrationDao instance = new AzureBoardsIntegrationDao();


    @Override
    public String getCollName() {
        return "azure_boards_integration";
    }

    @Override
    public Class<AzureBoardsIntegration> getClassT() {
        return AzureBoardsIntegration.class;
    }
}
