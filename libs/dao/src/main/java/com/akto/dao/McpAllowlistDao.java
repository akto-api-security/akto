package com.akto.dao;

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
}
