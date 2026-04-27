package com.akto.dao;

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
}
