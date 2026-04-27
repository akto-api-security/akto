package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.McpAllowlist;

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

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpAllowlist.REGISTRY_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpAllowlist.CREATED_AT}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{McpAllowlist.MANUALLY_ADDED}, false);
    }
}
