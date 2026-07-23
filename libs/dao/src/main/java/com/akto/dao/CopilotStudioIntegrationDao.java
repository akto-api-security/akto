package com.akto.dao;

import com.akto.dto.CopilotStudioIntegration;
import com.mongodb.client.model.CreateCollectionOptions;

public class CopilotStudioIntegrationDao extends AccountsContextDao<CopilotStudioIntegration> {

    public static final CopilotStudioIntegrationDao instance = new CopilotStudioIntegrationDao();

    private CopilotStudioIntegrationDao() {}

    @Override
    public String getCollName() {
        return "copilot_studio_integration";
    }

    @Override
    public Class<CopilotStudioIntegration> getClassT() {
        return CopilotStudioIntegration.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
    }
}
