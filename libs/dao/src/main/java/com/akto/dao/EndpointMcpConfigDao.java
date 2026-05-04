package com.akto.dao;

import com.akto.dto.EndpointMcpConfig;
import com.mongodb.client.model.CreateCollectionOptions;

public class EndpointMcpConfigDao extends AccountsContextDao<EndpointMcpConfig> {
    public static final String COLLECTION_NAME = "endpoint_mcp_config";
    public static final EndpointMcpConfigDao instance = new EndpointMcpConfigDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<EndpointMcpConfig> getClassT() {
        return EndpointMcpConfig.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{EndpointMcpConfig.COLLECTION_NAME_FIELD}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{EndpointMcpConfig.UPDATED_DATE_FIELD}, false);
    }
}
