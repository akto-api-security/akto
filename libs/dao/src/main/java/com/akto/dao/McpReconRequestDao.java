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

    /**
     * Create indexes for the collection
     * Indexes on: ip_range, status, account_id, and compound indexes
     */
    public void createIndicesIfAbsent() {
        // Single field indexes
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.IP_RANGE }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.STATUS }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.ACCOUNT_ID }, false);

        // Compound indexes for common queries
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.ACCOUNT_ID, Fields.STATUS }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.ACCOUNT_ID, Fields.IP_RANGE }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.STATUS, Fields.STARTED_AT }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { Fields.ACCOUNT_ID, Fields.STATUS, Fields.CREATED_AT }, false);
    }

    /**
     * Constants for common field names
     */
    public static class Fields {
        public static final String ID = "_id";
        public static final String ACCOUNT_ID = "account_id";
        public static final String IP_RANGE = "ip_range";
        public static final String STATUS = "status";
        public static final String STARTED_AT = "started_at";
        public static final String FINISHED_AT = "finished_at";
        public static final String CREATED_AT = "created_at";
        public static final String TIMEOUT = "timeout";

    }
}