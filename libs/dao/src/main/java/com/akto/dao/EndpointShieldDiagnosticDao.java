package com.akto.dao;

import com.akto.dto.EndpointShieldDiagnostic;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class EndpointShieldDiagnosticDao extends AccountsContextDao<EndpointShieldDiagnostic> {

    public static final String COLLECTION_NAME = "endpoint_shield_diagnostics";
    public static final EndpointShieldDiagnosticDao instance = new EndpointShieldDiagnosticDao();

    private EndpointShieldDiagnosticDao() {}

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointShieldDiagnostic> getClassT() {
        return EndpointShieldDiagnostic.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        // per-device history, newest first (list + retention trim)
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.compoundIndex(
                        Indexes.ascending(EndpointShieldDiagnostic.DEVICE_ID),
                        Indexes.descending(EndpointShieldDiagnostic.CREATED_AT)),
                new IndexOptions().name("deviceId_createdAt"));
    }
}
