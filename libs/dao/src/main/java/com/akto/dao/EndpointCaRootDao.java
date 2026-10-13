package com.akto.dao;

import com.akto.dto.EndpointCaRoot;
import com.mongodb.client.model.CreateCollectionOptions;

public class EndpointCaRootDao extends AccountsContextDao<EndpointCaRoot> {

    public static final EndpointCaRootDao instance = new EndpointCaRootDao();

    private EndpointCaRootDao() {}

    @Override
    public String getCollName() {
        return "endpoint_ca_root";
    }

    @Override
    public Class<EndpointCaRoot> getClassT() {
        return EndpointCaRoot.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
    }
}
