package com.akto.dao;

import com.akto.dto.McpReconRequest;

/**
 * DAO for MCP Recon Requests
 * Handles database operations for MCP reconnaissance scan requests
 */
public class McpReconRequestDao extends AccountsContextDao<McpReconRequest> {

    public static final McpReconRequestDao instance = new McpReconRequestDao();

    private McpReconRequestDao() {}

    @Override
    public String getCollName() {
        return "mcp_recon_requests";
    }

    @Override
    public Class<McpReconRequest> getClassT() {
        return McpReconRequest.class;
    }


    public void createIndicesIfAbsent() {

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] {  Fields.STATUS, Fields.ACCOUNT_ID }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.ACCOUNT_ID, Fields.STATUS, Fields.CREATED_AT }, false);
    }

    /**
     * Constants for common field names
     */
    public static class Fields {
        public static final String ID = "_id";
        public static final String ACCOUNT_ID = "accountId";
        public static final String IP_RANGE = "ipRange";
        public static final String STATUS = "status";
        public static final String STARTED_AT = "startedAt";
        public static final String FINISHED_AT = "finishedAt";
        public static final String CREATED_AT = "createdAt";
        public static final String SERVERS_FOUND = "serversFound";

    }
}