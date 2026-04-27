package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.McpRegistryConfig;

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
        boolean exists = false;
        for (String col : clients[0].getDatabase(Context.accountId.get() + "").listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            clients[0].getDatabase(Context.accountId.get() + "").createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpRegistryConfig.HASH}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpRegistryConfig.REGISTRY_TYPE}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpRegistryConfig.CREATED_AT}, false);
    }
}
