package com.akto.dao;

import com.akto.dto.McpRegistryConfig;
import com.mongodb.client.model.CreateCollectionOptions;

public class McpRegistryConfigDao extends AccountsContextDao<McpRegistryConfig> {

    public static final McpRegistryConfigDao instance = new McpRegistryConfigDao();

    private McpRegistryConfigDao() {}

    @Override
    public String getCollName() {
        return "mcp_registry_config";
    }

    @Override
    public Class<McpRegistryConfig> getClassT() {
        return McpRegistryConfig.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpRegistryConfig.REGISTRY_TYPE}, false);
    }
}
