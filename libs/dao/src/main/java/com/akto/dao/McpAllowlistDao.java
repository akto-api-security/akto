package com.akto.dao;

import com.akto.dto.McpAllowlist;
import com.mongodb.client.model.CreateCollectionOptions;

public class McpAllowlistDao extends AccountsContextDao<McpAllowlist> {

    public static final McpAllowlistDao instance = new McpAllowlistDao();

    private McpAllowlistDao() {}

    @Override
    public String getCollName() {
        return "mcp_allowlist";
    }

    @Override
    public Class<McpAllowlist> getClassT() {
        return McpAllowlist.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpAllowlist.REGISTRY_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpAllowlist.CREATED_AT}, false);
        MCollection.createUniqueIndex(getDBName(), getCollName(), new String[]{McpAllowlist.NAME, McpAllowlist.REGISTRY_ID}, false);
    }
}
